package com.z254.butterfly.cortex.api.dto;

import com.z254.butterfly.cortex.domain.model.AgentTask;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for submitting an agent task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskRequest {

    @NotBlank(message = "Agent ID is required")
    private String agentId;

    @NotBlank(message = "Input is required")
    @Size(max = 50000, message = "Input must be less than 50000 characters")
    private String input;

    private String conversationId;

    private String correlationId;

    private String idempotencyKey;

    private int priority;

    private Long timeoutSeconds;

    private String platoPlanId;

    private String rimNodeId;

    private Map<String, Object> context;

    private Map<String, Object> metadata;

    /**
     * Convert to domain model.
     */
    public AgentTask toTask(String userId, String namespace) {
        return AgentTask.builder()
                .id(UUID.randomUUID().toString())
                .agentId(agentId)
                .conversationId(conversationId)
                .input(input)
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString())
                .userId(userId)
                .namespace(namespace)
                .context(context)
                .status(AgentTask.TaskStatus.PENDING)
                .priority(priority)
                .timeout(timeoutSeconds != null ? Duration.ofSeconds(timeoutSeconds) : Duration.ofMinutes(5))
                .platoPlanId(platoPlanId)
                .rimNodeId(rimNodeId)
                .createdAt(Instant.now())
                .metadata(metadata)
                .build();
    }
}
