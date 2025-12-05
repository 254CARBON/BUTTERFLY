package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines an agent's specialization - its domain expertise and capabilities.
 * Used for intelligent task routing in multi-agent orchestration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSpecialization {

    /**
     * Domain areas the agent specializes in.
     * Examples: "finance", "code", "research", "writing", "data-analysis"
     */
    @Builder.Default
    private Set<String> domains = new HashSet<>();

    /**
     * Capabilities the agent possesses.
     * Examples: "analysis", "generation", "review", "summarization", "translation"
     */
    @Builder.Default
    private Set<String> capabilities = new HashSet<>();

    /**
     * Minimum confidence threshold for the agent to accept a task.
     * Tasks below this threshold may be delegated to other agents.
     */
    @Builder.Default
    private double confidenceThreshold = 0.7;

    /**
     * Performance scores by domain (0.0 - 1.0).
     * Updated based on task outcomes to enable adaptive routing.
     */
    @Builder.Default
    private Map<String, Double> domainScores = new HashMap<>();

    /**
     * Performance scores by capability (0.0 - 1.0).
     */
    @Builder.Default
    private Map<String, Double> capabilityScores = new HashMap<>();

    /**
     * Maximum complexity score the agent can handle (0.0 - 1.0).
     * Complex tasks above this threshold require team coordination.
     */
    @Builder.Default
    private double maxComplexity = 0.8;

    /**
     * Preferred task types for this agent.
     */
    @Builder.Default
    private Set<String> preferredTaskTypes = new HashSet<>();

    /**
     * Tags for additional categorization.
     */
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    /**
     * Check if this agent specializes in a given domain.
     */
    public boolean hasDomain(String domain) {
        return domains != null && domains.contains(domain.toLowerCase());
    }

    /**
     * Check if this agent has a specific capability.
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability.toLowerCase());
    }

    /**
     * Get the performance score for a domain.
     */
    public double getDomainScore(String domain) {
        if (domainScores == null) {
            return 0.5; // Default neutral score
        }
        return domainScores.getOrDefault(domain.toLowerCase(), 0.5);
    }

    /**
     * Get the performance score for a capability.
     */
    public double getCapabilityScore(String capability) {
        if (capabilityScores == null) {
            return 0.5;
        }
        return capabilityScores.getOrDefault(capability.toLowerCase(), 0.5);
    }

    /**
     * Update the domain score based on task outcome.
     */
    public void updateDomainScore(String domain, double newScore) {
        if (domainScores == null) {
            domainScores = new HashMap<>();
        }
        // Exponential moving average
        double current = getDomainScore(domain);
        double alpha = 0.2; // Learning rate
        domainScores.put(domain.toLowerCase(), current * (1 - alpha) + newScore * alpha);
    }

    /**
     * Calculate match score for a task with given requirements.
     */
    public double calculateMatchScore(Set<String> requiredDomains, Set<String> requiredCapabilities) {
        if ((requiredDomains == null || requiredDomains.isEmpty()) &&
            (requiredCapabilities == null || requiredCapabilities.isEmpty())) {
            return 0.5; // Neutral match for unspecified requirements
        }

        double domainMatch = 0.0;
        double capabilityMatch = 0.0;
        int domainCount = 0;
        int capabilityCount = 0;

        if (requiredDomains != null && !requiredDomains.isEmpty()) {
            for (String domain : requiredDomains) {
                if (hasDomain(domain)) {
                    domainMatch += getDomainScore(domain);
                    domainCount++;
                }
            }
            domainMatch = domainCount > 0 ? domainMatch / requiredDomains.size() : 0.0;
        }

        if (requiredCapabilities != null && !requiredCapabilities.isEmpty()) {
            for (String capability : requiredCapabilities) {
                if (hasCapability(capability)) {
                    capabilityMatch += getCapabilityScore(capability);
                    capabilityCount++;
                }
            }
            capabilityMatch = capabilityCount > 0 ? capabilityMatch / requiredCapabilities.size() : 0.0;
        }

        // Weighted average (domains slightly more important)
        double domainWeight = 0.6;
        double capabilityWeight = 0.4;
        
        if (requiredDomains == null || requiredDomains.isEmpty()) {
            return capabilityMatch;
        }
        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            return domainMatch;
        }
        
        return domainMatch * domainWeight + capabilityMatch * capabilityWeight;
    }

    /**
     * Create a general-purpose specialization.
     */
    public static AgentSpecialization generalPurpose() {
        return AgentSpecialization.builder()
                .domains(Set.of("general"))
                .capabilities(Set.of("analysis", "generation", "summarization"))
                .confidenceThreshold(0.5)
                .maxComplexity(0.6)
                .build();
    }

    /**
     * Create a code-focused specialization.
     */
    public static AgentSpecialization codeSpecialist() {
        return AgentSpecialization.builder()
                .domains(Set.of("code", "software", "programming"))
                .capabilities(Set.of("generation", "review", "debugging", "refactoring"))
                .confidenceThreshold(0.7)
                .maxComplexity(0.9)
                .tags(Set.of("technical"))
                .build();
    }

    /**
     * Create a research-focused specialization.
     */
    public static AgentSpecialization researchSpecialist() {
        return AgentSpecialization.builder()
                .domains(Set.of("research", "analysis", "data"))
                .capabilities(Set.of("analysis", "summarization", "synthesis", "fact-checking"))
                .confidenceThreshold(0.8)
                .maxComplexity(0.85)
                .tags(Set.of("analytical"))
                .build();
    }
}
