package com.z254.butterfly.cortex.api.dto;

import com.z254.butterfly.cortex.config.CortexProperties.AgentType;
import com.z254.butterfly.cortex.domain.model.Agent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Request DTO for creating/updating an agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    @NotBlank(message = "Agent name is required")
    @Size(max = 255, message = "Agent name must be less than 255 characters")
    private String name;

    @Size(max = 1000, message = "Description must be less than 1000 characters")
    private String description;

    private AgentType type;

    @Size(max = 10000, message = "System prompt must be less than 10000 characters")
    private String systemPrompt;

    private Set<String> toolIds;

    private Agent.MemoryConfig memoryConfig;

    private Agent.LLMConfig llmConfig;

    private Agent.GovernanceConfig governanceConfig;

    private String namespace;

    private Map<String, Object> metadata;

    /**
     * Convert to domain model.
     */
    public Agent toAgent(String owner) {
        return Agent.builder()
                .name(name)
                .description(description)
                .type(type != null ? type : AgentType.REACT)
                .systemPrompt(systemPrompt)
                .toolIds(toolIds)
                .memoryConfig(memoryConfig)
                .llmConfig(llmConfig)
                .governanceConfig(governanceConfig)
                .namespace(namespace)
                .owner(owner)
                .status(Agent.AgentStatus.ACTIVE)
                .version("1.0.0")
                .taskCount(0L)
                .successRate(1.0)
                .metadata(metadata)
                .build();
    }
}
