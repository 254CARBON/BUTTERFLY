package com.z254.butterfly.cortex.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentMessage;
import com.z254.butterfly.cortex.domain.model.TeamResult;
import com.z254.butterfly.cortex.domain.model.TeamThought;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer for team task results and events.
 * Publishes results to cortex.team.results, thoughts to cortex.team.thoughts,
 * and inter-agent messages to cortex.agent.messages.
 */
@Component
@Slf4j
public class TeamResultProducer {

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String resultsTopic;
    private final String thoughtsTopic;
    private final String messagesTopic;
    private final Counter resultsPublishedCounter;
    private final Counter thoughtsPublishedCounter;
    private final Counter messagesPublishedCounter;

    public TeamResultProducer(
            KafkaTemplate<String, Map<String, Object>> kafkaTemplate,
            ObjectMapper objectMapper,
            CortexProperties cortexProperties,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        // Get topic names from config with defaults
        CortexProperties.KafkaProperties kafkaConfig = cortexProperties.getKafka();
        if (kafkaConfig != null && kafkaConfig.getTopics() != null) {
            this.resultsTopic = kafkaConfig.getTopics().getTeamResults() != null ?
                    kafkaConfig.getTopics().getTeamResults() : "cortex.team.results";
            this.thoughtsTopic = kafkaConfig.getTopics().getTeamThoughts() != null ?
                    kafkaConfig.getTopics().getTeamThoughts() : "cortex.team.thoughts";
            this.messagesTopic = kafkaConfig.getTopics().getAgentMessages() != null ?
                    kafkaConfig.getTopics().getAgentMessages() : "cortex.agent.messages";
        } else {
            this.resultsTopic = "cortex.team.results";
            this.thoughtsTopic = "cortex.team.thoughts";
            this.messagesTopic = "cortex.agent.messages";
        }
        
        this.resultsPublishedCounter = Counter.builder("cortex.kafka.team.results.published")
                .register(meterRegistry);
        this.thoughtsPublishedCounter = Counter.builder("cortex.kafka.team.thoughts.published")
                .register(meterRegistry);
        this.messagesPublishedCounter = Counter.builder("cortex.kafka.agent.messages.published")
                .register(meterRegistry);
    }

    /**
     * Publish a team task result.
     */
    public void publishResult(TeamResult result) {
        if (result == null) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("teamTaskId", result.getTeamTaskId());
            payload.put("teamId", result.getTeamId());
            payload.put("status", result.getStatus().name());
            payload.put("output", result.getOutput());
            payload.put("confidence", result.getConfidence());
            payload.put("agentCount", result.getAgentCount());
            payload.put("subTaskCount", result.getSubTaskCount());
            payload.put("successfulSubTasks", result.getSuccessfulSubTasks());
            payload.put("failedSubTasks", result.getFailedSubTasks());
            payload.put("totalTokens", result.getTotalTokens());
            payload.put("aggregationMethod", result.getAggregationMethod());
            
            if (result.getConsensusLevel() != null) {
                payload.put("consensusLevel", result.getConsensusLevel());
            }
            if (result.getDebateRounds() != null) {
                payload.put("debateRounds", result.getDebateRounds());
            }
            if (result.getStartedAt() != null) {
                payload.put("startedAt", result.getStartedAt().toEpochMilli());
            }
            if (result.getCompletedAt() != null) {
                payload.put("completedAt", result.getCompletedAt().toEpochMilli());
            }
            if (result.getDuration() != null) {
                payload.put("durationMs", result.getDuration().toMillis());
            }
            if (result.getErrorMessage() != null) {
                payload.put("errorMessage", result.getErrorMessage());
            }
            if (result.getAgentConfidences() != null) {
                payload.put("agentConfidences", result.getAgentConfidences());
            }
            if (result.getTokensByAgent() != null) {
                payload.put("tokensByAgent", result.getTokensByAgent());
            }
            if (result.getGovernanceDecision() != null) {
                payload.put("governanceDecision", result.getGovernanceDecision());
            }
            if (result.getPlatoPlanId() != null) {
                payload.put("platoPlanId", result.getPlatoPlanId());
            }
            if (result.getCapsuleTraceId() != null) {
                payload.put("capsuleTraceId", result.getCapsuleTraceId());
            }

            kafkaTemplate.send(resultsTopic, result.getTeamTaskId(), payload)
                    .whenComplete((sendResult, exception) -> {
                        if (exception != null) {
                            log.error("Failed to publish team result: {} - {}", 
                                    result.getTeamTaskId(), exception.getMessage());
                        } else {
                            resultsPublishedCounter.increment();
                            log.debug("Published team result: {} to partition {} offset {}",
                                    result.getTeamTaskId(),
                                    sendResult.getRecordMetadata().partition(),
                                    sendResult.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize team result: {} - {}", result.getTeamTaskId(), e.getMessage());
        }
    }

    /**
     * Publish a team thought for streaming.
     */
    public void publishThought(TeamThought thought) {
        if (thought == null) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", thought.getId());
            payload.put("teamTaskId", thought.getTeamTaskId());
            payload.put("type", thought.getType().name());
            payload.put("content", thought.getContent());
            payload.put("phase", thought.getPhase());
            payload.put("iteration", thought.getIteration());
            payload.put("round", thought.getRound());
            
            if (thought.getSubTaskId() != null) {
                payload.put("subTaskId", thought.getSubTaskId());
            }
            if (thought.getAgentId() != null) {
                payload.put("agentId", thought.getAgentId());
            }
            if (thought.getAgentName() != null) {
                payload.put("agentName", thought.getAgentName());
            }
            if (thought.getAgentRole() != null) {
                payload.put("agentRole", thought.getAgentRole().name());
            }
            if (thought.getMessageId() != null) {
                payload.put("messageId", thought.getMessageId());
            }
            if (thought.getToolId() != null) {
                payload.put("toolId", thought.getToolId());
            }
            if (thought.getToolParameters() != null) {
                payload.put("toolParameters", thought.getToolParameters());
            }
            if (thought.getToolResult() != null) {
                payload.put("toolResult", thought.getToolResult());
            }
            if (thought.getConfidence() != null) {
                payload.put("confidence", thought.getConfidence());
            }
            if (thought.getTokenCount() != null) {
                payload.put("tokenCount", thought.getTokenCount());
            }
            if (thought.getDurationMs() != null) {
                payload.put("durationMs", thought.getDurationMs());
            }
            if (thought.getTimestamp() != null) {
                payload.put("timestamp", thought.getTimestamp().toEpochMilli());
            }

            kafkaTemplate.send(thoughtsTopic, thought.getTeamTaskId(), payload)
                    .whenComplete((sendResult, exception) -> {
                        if (exception != null) {
                            log.warn("Failed to publish team thought: {}", exception.getMessage());
                        } else {
                            thoughtsPublishedCounter.increment();
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize team thought: {}", e.getMessage());
        }
    }

    /**
     * Publish an inter-agent message.
     */
    public void publishAgentMessage(AgentMessage message) {
        if (message == null) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", message.getId());
            payload.put("teamTaskId", message.getTeamTaskId());
            payload.put("type", message.getType().name());
            payload.put("content", message.getContent());
            payload.put("sequenceNumber", message.getSequenceNumber());
            payload.put("round", message.getRound());
            payload.put("confidence", message.getConfidence());
            payload.put("priority", message.getPriority());
            
            if (message.getSubTaskId() != null) {
                payload.put("subTaskId", message.getSubTaskId());
            }
            if (message.getFromAgentId() != null) {
                payload.put("fromAgentId", message.getFromAgentId());
            }
            if (message.getToAgentId() != null) {
                payload.put("toAgentId", message.getToAgentId());
            }
            if (message.getParentMessageId() != null) {
                payload.put("parentMessageId", message.getParentMessageId());
            }
            if (message.getPayload() != null) {
                payload.put("payload", message.getPayload());
            }
            if (message.getTimestamp() != null) {
                payload.put("timestamp", message.getTimestamp().toEpochMilli());
            }
            if (message.getCorrelationId() != null) {
                payload.put("correlationId", message.getCorrelationId());
            }

            kafkaTemplate.send(messagesTopic, message.getTeamTaskId(), payload)
                    .whenComplete((sendResult, exception) -> {
                        if (exception != null) {
                            log.warn("Failed to publish agent message: {}", exception.getMessage());
                        } else {
                            messagesPublishedCounter.increment();
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize agent message: {}", e.getMessage());
        }
    }
}
