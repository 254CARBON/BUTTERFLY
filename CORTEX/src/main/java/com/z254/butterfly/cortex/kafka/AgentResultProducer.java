package com.z254.butterfly.cortex.kafka;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentResult;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for agent results and thoughts.
 * Publishes to cortex.agent.results and cortex.agent.thoughts topics.
 */
@Component
@Slf4j
public class AgentResultProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CortexProperties.KafkaTopicsProperties topics;
    private final Counter resultProducedCounter;
    private final Counter thoughtProducedCounter;

    public AgentResultProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            CortexProperties cortexProperties,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = cortexProperties.getKafka().getTopics();
        
        this.resultProducedCounter = Counter.builder("cortex.kafka.results.produced").register(meterRegistry);
        this.thoughtProducedCounter = Counter.builder("cortex.kafka.thoughts.produced").register(meterRegistry);
    }

    /**
     * Publish an agent result to the results topic.
     */
    public void publishResult(AgentResult result) {
        Map<String, Object> message = mapResult(result);
        
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(topics.getResults(), result.getTaskId(), message);
        
        future.whenComplete((sendResult, ex) -> {
            if (ex != null) {
                log.error("Failed to publish result for task {}: {}", result.getTaskId(), ex.getMessage());
            } else {
                resultProducedCounter.increment();
                log.debug("Published result for task {} to partition {} offset {}",
                        result.getTaskId(),
                        sendResult.getRecordMetadata().partition(),
                        sendResult.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish an agent thought to the thoughts topic.
     */
    public void publishThought(AgentThought thought) {
        Map<String, Object> message = mapThought(thought);
        
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topics.getThoughts(), thought.getTaskId(), message);
        
        future.whenComplete((sendResult, ex) -> {
            if (ex != null) {
                log.error("Failed to publish thought {} for task {}: {}", 
                        thought.getId(), thought.getTaskId(), ex.getMessage());
            } else {
                thoughtProducedCounter.increment();
                log.trace("Published thought {} for task {}", thought.getId(), thought.getTaskId());
            }
        });
    }

    private Map<String, Object> mapResult(AgentResult result) {
        Map<String, Object> message = new HashMap<>();
        message.put("taskId", result.getTaskId());
        message.put("agentId", result.getAgentId());
        message.put("conversationId", result.getConversationId());
        message.put("correlationId", result.getCorrelationId());
        message.put("output", result.getOutput());
        message.put("status", result.getStatus().name());
        message.put("errorCode", result.getErrorCode());
        message.put("errorMessage", result.getErrorMessage());
        message.put("iterations", result.getIterations());
        
        if (result.getTokenUsage() != null) {
            message.put("inputTokens", result.getTokenUsage().getInputTokens());
            message.put("outputTokens", result.getTokenUsage().getOutputTokens());
        }
        
        message.put("durationMs", result.getDuration() != null ? result.getDuration().toMillis() : 0);
        message.put("startedAt", result.getStartedAt() != null ? result.getStartedAt().toEpochMilli() : 0);
        message.put("completedAt", result.getCompletedAt() != null ? result.getCompletedAt().toEpochMilli() : Instant.now().toEpochMilli());
        message.put("capsuleId", result.getCapsuleId());
        
        return message;
    }

    private Map<String, Object> mapThought(AgentThought thought) {
        Map<String, Object> message = new HashMap<>();
        message.put("id", thought.getId());
        message.put("taskId", thought.getTaskId());
        message.put("agentId", thought.getAgentId());
        message.put("type", thought.getType().name());
        message.put("content", thought.getContent());
        message.put("iteration", thought.getIteration());
        message.put("toolId", thought.getToolId());
        
        if (thought.getToolParameters() != null) {
            message.put("toolParameters", thought.getToolParameters().toString());
        }
        
        message.put("toolResult", thought.getToolResult());
        message.put("tokenCount", thought.getTokenCount());
        message.put("timestamp", thought.getTimestamp() != null ? thought.getTimestamp().toEpochMilli() : Instant.now().toEpochMilli());
        
        return message;
    }
}
