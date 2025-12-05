package com.z254.butterfly.cortex.domain.model;

import com.z254.butterfly.cortex.config.CortexProperties.AgentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Represents an AI agent definition in CORTEX.
 * Agents are configured entities that can execute tasks using LLMs and tools.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {

    /**
     * Unique identifier for this agent.
     */
    private String id;

    /**
     * Human-readable name of the agent.
     */
    private String name;

    /**
     * Description of the agent's purpose and capabilities.
     */
    private String description;

    /**
     * Type of agent implementation.
     */
    private AgentType type;

    /**
     * System prompt that defines the agent's behavior and personality.
     */
    private String systemPrompt;

    /**
     * Set of tool IDs this agent is allowed to use.
     */
    private Set<String> toolIds;

    /**
     * Memory configuration for this agent.
     */
    private MemoryConfig memoryConfig;

    /**
     * LLM configuration for this agent.
     */
    private LLMConfig llmConfig;

    /**
     * Governance configuration for this agent.
     */
    private GovernanceConfig governanceConfig;

    /**
     * Specialization defining the agent's domain expertise and capabilities.
     * Used for intelligent task routing in multi-agent orchestration.
     */
    private AgentSpecialization specialization;

    /**
     * Current status of the agent.
     */
    private AgentStatus status;

    /**
     * Version of this agent configuration.
     */
    private String version;

    /**
     * Namespace this agent belongs to.
     */
    private String namespace;

    /**
     * Owner/creator of this agent.
     */
    private String owner;

    /**
     * When the agent was created.
     */
    private Instant createdAt;

    /**
     * When the agent was last updated.
     */
    private Instant updatedAt;

    /**
     * When the agent was last used.
     */
    private Instant lastUsedAt;

    /**
     * Total number of tasks executed by this agent.
     */
    private Long taskCount;

    /**
     * Success rate of tasks (0.0 - 1.0).
     */
    private Double successRate;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Agent status enumeration.
     */
    public enum AgentStatus {
        ACTIVE,      // Agent is active and can accept tasks
        INACTIVE,    // Agent is temporarily disabled
        SUSPENDED,   // Agent is suspended due to issues
        DEPRECATED,  // Agent is deprecated
        DRAFT        // Agent is in draft state, not yet active
    }

    /**
     * Memory configuration for an agent.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryConfig {
        private boolean episodicEnabled;
        private int maxEpisodes;
        private boolean semanticEnabled;
        private int semanticTopK;
        private boolean capsuleEnabled;
        private boolean workingMemoryEnabled;
    }

    /**
     * LLM configuration for an agent.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LLMConfig {
        private String providerId;
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private String[] stopSequences;
        private boolean streamingEnabled;
        private Map<String, Object> additionalParams;
    }

    /**
     * Governance configuration for an agent.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GovernanceConfig {
        @Builder.Default
        private boolean enabled = true;
        private boolean policyCheckEnabled;
        private Set<String> policyIds;
        private boolean approvalRequired;
        private String approvalLevel;
        private boolean auditEnabled;
        private Double maxRiskScore;
        private String defaultPolicyId;
        @Builder.Default
        private boolean requireApprovalForTools = false;
        private Map<String, String> toolPolicies;
        private Set<String> highRiskTools;
        @Builder.Default
        private boolean outputValidationEnabled = false;
        private String outputPolicyId;
    }

    /**
     * Check if this agent is active and can accept tasks.
     */
    public boolean isActive() {
        return status == AgentStatus.ACTIVE;
    }

    /**
     * Check if this agent requires governance approval.
     */
    public boolean requiresApproval() {
        return governanceConfig != null && governanceConfig.isApprovalRequired();
    }

    /**
     * Check if this agent has specialization defined.
     */
    public boolean hasSpecialization() {
        return specialization != null;
    }

    /**
     * Check if this agent specializes in a given domain.
     */
    public boolean hasDomain(String domain) {
        return specialization != null && specialization.hasDomain(domain);
    }

    /**
     * Check if this agent has a specific capability.
     */
    public boolean hasCapability(String capability) {
        return specialization != null && specialization.hasCapability(capability);
    }

    /**
     * Calculate match score for given requirements.
     * Returns 0.5 (neutral) if no specialization is defined.
     */
    public double calculateMatchScore(java.util.Set<String> requiredDomains, 
                                       java.util.Set<String> requiredCapabilities) {
        if (specialization == null) {
            return 0.5; // Neutral match
        }
        return specialization.calculateMatchScore(requiredDomains, requiredCapabilities);
    }

    /**
     * Update the agent's domain score based on task outcome.
     */
    public void updateDomainScore(String domain, double score) {
        if (specialization != null) {
            specialization.updateDomainScore(domain, score);
        }
    }
}
