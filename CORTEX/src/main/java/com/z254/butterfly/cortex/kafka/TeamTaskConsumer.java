package com.z254.butterfly.cortex.kafka;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.TeamTask;
import com.z254.butterfly.cortex.domain.model.TeamTaskStatus;
import com.z254.butterfly.cortex.orchestration.MultiAgentOrchestrator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Kafka consumer for team task requests.
 * Consumes tasks from cortex.team.tasks topic and processes them through
 * the multi-agent orchestrator.
 */
@Component
@Slf4j
public class TeamTaskConsumer {

    private final MultiAgentOrchestrator orchestrator;
    private final TeamResultProducer resultProducer;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CortexProperties cortexProperties;
    private final Counter taskConsumedCounter;
    private final Counter taskSuccessCounter;
    private final Counter taskFailureCounter;

    private static final String IDEMPOTENCY_PREFIX = "cortex:team:idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public TeamTaskConsumer(
            MultiAgentOrchestrator orchestrator,
            TeamResultProducer resultProducer,
            ReactiveRedisTemplate<String, String> redisTemplate,
            CortexProperties cortexProperties,
            MeterRegistry meterRegistry) {
        this.orchestrator = orchestrator;
        this.resultProducer = resultProducer;
        this.redisTemplate = redisTemplate;
        this.cortexProperties = cortexProperties;
        
        this.taskConsumedCounter = Counter.builder("cortex.kafka.team.tasks.consumed").register(meterRegistry);
        this.taskSuccessCounter = Counter.builder("cortex.kafka.team.tasks.success").register(meterRegistry);
        this.taskFailureCounter = Counter.builder("cortex.kafka.team.tasks.failure").register(meterRegistry);
    }

    @KafkaListener(
            topics = "${cortex.kafka.topics.team-tasks:cortex.team.tasks}",
            groupId = "${spring.kafka.consumer.group-id:cortex-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        taskConsumedCounter.increment();
        
        Map<String, Object> data = record.value();
        String taskId = (String) data.get("taskId");
        String correlationId = (String) data.get("correlationId");
        String idempotencyKey = (String) data.get("idempotencyKey");
        
        // Set MDC for logging
        MDC.put("correlationId", correlationId);
        MDC.put("taskId", taskId);
        
        log.info("Received team task: {} from partition {} offset {}", 
                taskId, record.partition(), record.offset());

        // Check idempotency
        checkIdempotency(idempotencyKey)
                .flatMap(isDuplicate -> {
                    if (isDuplicate) {
                        log.info("Duplicate team task detected (idempotency): {}", taskId);
                        return Mono.empty();
                    }
                    
                    // Convert to domain model
                    TeamTask task = mapToTask(data);
                    
                    // Process task
                    return orchestrator.executeTask(task)
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    taskSuccessCounter.increment();
                                    log.info("Team task completed: {} - {}", taskId, result.getStatus());
                                } else {
                                    taskFailureCounter.increment();
                                    log.warn("Team task failed: {} - {}", taskId, result.getErrorMessage());
                                }
                                
                                // Publish result
                                resultProducer.publishResult(result);
                                
                                // Mark idempotency
                                markProcessed(idempotencyKey).subscribe();
                            })
                            .doOnError(e -> {
                                taskFailureCounter.increment();
                                log.error("Team task failed: {} - {}", taskId, e.getMessage());
                            });
                })
                .doFinally(signal -> {
                    ack.acknowledge();
                    MDC.clear();
                })
                .subscribe();
    }

    @SuppressWarnings("unchecked")
    private TeamTask mapToTask(Map<String, Object> data) {
        TeamTask.TeamTaskBuilder builder = TeamTask.builder()
                .id((String) data.get("taskId"))
                .teamId((String) data.get("teamId"))
                .input((String) data.get("input"))
                .correlationId((String) data.get("correlationId"))
                .userId((String) data.get("userId"))
                .namespace((String) data.getOrDefault("namespace", "default"))
                .status(TeamTaskStatus.PENDING)
                .createdAt(data.containsKey("createdAt") 
                        ? Instant.ofEpochMilli(((Number) data.get("createdAt")).longValue())
                        : Instant.now())
                .updatedAt(Instant.now());
        
        // Optional fields
        if (data.containsKey("conversationId")) {
            builder.conversationId((String) data.get("conversationId"));
        }
        if (data.containsKey("priority")) {
            builder.priority(((Number) data.get("priority")).intValue());
        }
        if (data.containsKey("timeoutSeconds")) {
            builder.timeout(Duration.ofSeconds(((Number) data.get("timeoutSeconds")).longValue()));
        }
        if (data.containsKey("tokenBudget")) {
            builder.tokenBudget(((Number) data.get("tokenBudget")).intValue());
        }
        if (data.containsKey("context")) {
            builder.context((Map<String, Object>) data.get("context"));
        }
        
        return builder.build();
    }

    private Mono<Boolean> checkIdempotency(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Mono.just(false);
        }
        
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        return redisTemplate.hasKey(key);
    }

    private Mono<Boolean> markProcessed(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Mono.just(false);
        }
        
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        return redisTemplate.opsForValue()
                .set(key, "processed", IDEMPOTENCY_TTL);
    }
}
