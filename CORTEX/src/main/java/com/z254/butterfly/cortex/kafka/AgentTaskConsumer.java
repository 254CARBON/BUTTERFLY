package com.z254.butterfly.cortex.kafka;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentTask;
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
 * Kafka consumer for agent task requests.
 * Consumes tasks from cortex.agent.tasks topic and processes them.
 */
@Component
@Slf4j
public class AgentTaskConsumer {

    private final AgentService agentService;
    private final AgentResultProducer resultProducer;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CortexProperties.KafkaProperties.TopicProperties topics;
    private final Counter taskConsumedCounter;
    private final Counter taskSuccessCounter;
    private final Counter taskFailureCounter;

    private static final String IDEMPOTENCY_PREFIX = "cortex:idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public AgentTaskConsumer(
            AgentService agentService,
            AgentResultProducer resultProducer,
            ReactiveRedisTemplate<String, String> redisTemplate,
            CortexProperties cortexProperties,
            MeterRegistry meterRegistry) {
        this.agentService = agentService;
        this.resultProducer = resultProducer;
        this.redisTemplate = redisTemplate;
        this.topics = cortexProperties.getKafka().getTopics();
        
        this.taskConsumedCounter = Counter.builder("cortex.kafka.tasks.consumed").register(meterRegistry);
        this.taskSuccessCounter = Counter.builder("cortex.kafka.tasks.success").register(meterRegistry);
        this.taskFailureCounter = Counter.builder("cortex.kafka.tasks.failure").register(meterRegistry);
    }

    @KafkaListener(
            topics = "${cortex.kafka.topics.agent-tasks}",
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
        
        log.info("Received task: {} from partition {} offset {}", 
                taskId, record.partition(), record.offset());

        // Check idempotency
        checkIdempotency(idempotencyKey)
                .flatMap(isDuplicate -> {
                    if (isDuplicate) {
                        log.info("Duplicate task detected (idempotency): {}", taskId);
                        return Mono.empty();
                    }
                    
                    // Convert to domain model
                    AgentTask task = mapToTask(data);
                    
                    // Process task
                    return agentService.executeTask(task)
                            .doOnSuccess(result -> {
                                taskSuccessCounter.increment();
                                log.info("Task completed: {} - {}", taskId, result.getStatus());
                                
                                // Publish result
                                resultProducer.publishResult(result);
                                
                                // Mark idempotency
                                markProcessed(idempotencyKey).subscribe();
                            })
                            .doOnError(e -> {
                                taskFailureCounter.increment();
                                log.error("Task failed: {} - {}", taskId, e.getMessage());
                            });
                })
                .doFinally(signal -> {
                    ack.acknowledge();
                    MDC.clear();
                })
                .subscribe();
    }

    private AgentTask mapToTask(Map<String, Object> data) {
        return AgentTask.builder()
                .id((String) data.get("taskId"))
                .agentId((String) data.get("agentId"))
                .conversationId((String) data.get("conversationId"))
                .input((String) data.get("input"))
                .correlationId((String) data.get("correlationId"))
                .idempotencyKey((String) data.get("idempotencyKey"))
                .userId((String) data.get("userId"))
                .namespace((String) data.get("namespace"))
                .priority(data.containsKey("priority") ? ((Number) data.get("priority")).intValue() : 0)
                .timeout(Duration.ofSeconds(data.containsKey("timeoutSeconds") 
                        ? ((Number) data.get("timeoutSeconds")).longValue() : 300))
                .platoPlanId((String) data.get("platoPlanId"))
                .rimNodeId((String) data.get("rimNodeId"))
                .createdAt(data.containsKey("createdAt") 
                        ? Instant.ofEpochMilli(((Number) data.get("createdAt")).longValue())
                        : Instant.now())
                .status(AgentTask.TaskStatus.PENDING)
                .build();
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
