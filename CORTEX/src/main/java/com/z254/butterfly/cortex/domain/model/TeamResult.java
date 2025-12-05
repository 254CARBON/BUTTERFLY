package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a multi-agent team task execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResult {

    /**
     * ID of the team task.
     */
    private String teamTaskId;

    /**
     * ID of the team that executed the task.
     */
    private String teamId;

    /**
     * Final status of the task.
     */
    private TeamTaskStatus status;

    /**
     * Final output/result of the team execution.
     */
    private String output;

    /**
     * Structured output (if applicable).
     */
    private Map<String, Object> structuredOutput;

    /**
     * Overall confidence in the result (0.0 - 1.0).
     */
    @Builder.Default
    private double confidence = 0.0;

    /**
     * Aggregated confidence scores from different agents.
     */
    @Builder.Default
    private Map<String, Double> agentConfidences = new HashMap<>();

    /**
     * Results from individual subtasks.
     */
    @Builder.Default
    private List<SubTaskResult> subTaskResults = new ArrayList<>();

    /**
     * How the result was determined (aggregation method).
     */
    private String aggregationMethod;

    /**
     * Consensus level (for debate teams, 0.0 - 1.0).
     */
    private Double consensusLevel;

    /**
     * Number of debate rounds (for debate teams).
     */
    private Integer debateRounds;

    /**
     * Total number of agents that contributed.
     */
    private int agentCount;

    /**
     * Total number of subtasks.
     */
    private int subTaskCount;

    /**
     * Number of successful subtasks.
     */
    private int successfulSubTasks;

    /**
     * Number of failed subtasks.
     */
    private int failedSubTasks;

    /**
     * Total token usage across all agents.
     */
    private int totalTokens;

    /**
     * Token usage by agent.
     */
    @Builder.Default
    private Map<String, Integer> tokensByAgent = new HashMap<>();

    /**
     * Total number of inter-agent messages.
     */
    private int totalMessages;

    /**
     * When the task started.
     */
    private Instant startedAt;

    /**
     * When the task completed.
     */
    private Instant completedAt;

    /**
     * Total execution duration.
     */
    private Duration duration;

    /**
     * Error message (if failed).
     */
    private String errorMessage;

    /**
     * Errors from individual agents.
     */
    @Builder.Default
    private List<String> agentErrors = new ArrayList<>();

    /**
     * Warnings generated during execution.
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * PLATO governance decision (if applicable).
     */
    private String governanceDecision;

    /**
     * PLATO plan ID (if governance was involved).
     */
    private String platoPlanId;

    /**
     * CAPSULE trace ID for replay.
     */
    private String capsuleTraceId;

    /**
     * Additional metadata.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Result from an individual subtask.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskResult {
        private String subTaskId;
        private String agentId;
        private String agentName;
        private SubTaskStatus status;
        private String output;
        private Double confidence;
        private int tokenUsage;
        private Duration duration;
        private String errorMessage;
    }

    /**
     * Check if the result was successful.
     */
    public boolean isSuccess() {
        return status == TeamTaskStatus.COMPLETED;
    }

    /**
     * Get the success rate of subtasks.
     */
    public double getSubTaskSuccessRate() {
        if (subTaskCount == 0) {
            return 0.0;
        }
        return (double) successfulSubTasks / subTaskCount;
    }

    /**
     * Calculate average confidence across agents.
     */
    public double calculateAverageConfidence() {
        if (agentConfidences == null || agentConfidences.isEmpty()) {
            return confidence;
        }
        return agentConfidences.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(confidence);
    }

    /**
     * Add a subtask result.
     */
    public void addSubTaskResult(SubTaskResult result) {
        if (subTaskResults == null) {
            subTaskResults = new ArrayList<>();
        }
        subTaskResults.add(result);
        
        // Update counters
        subTaskCount = subTaskResults.size();
        successfulSubTasks = (int) subTaskResults.stream()
                .filter(r -> r.getStatus() == SubTaskStatus.COMPLETED)
                .count();
        failedSubTasks = (int) subTaskResults.stream()
                .filter(r -> r.getStatus() == SubTaskStatus.FAILED)
                .count();
    }

    /**
     * Add token usage for an agent.
     */
    public void addTokenUsage(String agentId, int tokens) {
        if (tokensByAgent == null) {
            tokensByAgent = new HashMap<>();
        }
        tokensByAgent.merge(agentId, tokens, Integer::sum);
        totalTokens = tokensByAgent.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Add an agent error.
     */
    public void addAgentError(String error) {
        if (agentErrors == null) {
            agentErrors = new ArrayList<>();
        }
        agentErrors.add(error);
    }

    /**
     * Add a warning.
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }

    /**
     * Create a successful result.
     */
    public static TeamResult success(String teamTaskId, String teamId, String output, double confidence) {
        return TeamResult.builder()
                .teamTaskId(teamTaskId)
                .teamId(teamId)
                .status(TeamTaskStatus.COMPLETED)
                .output(output)
                .confidence(confidence)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Create a failed result.
     */
    public static TeamResult failure(String teamTaskId, String teamId, String errorMessage) {
        return TeamResult.builder()
                .teamTaskId(teamTaskId)
                .teamId(teamId)
                .status(TeamTaskStatus.FAILED)
                .errorMessage(errorMessage)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Create a timeout result.
     */
    public static TeamResult timeout(String teamTaskId, String teamId) {
        return TeamResult.builder()
                .teamTaskId(teamTaskId)
                .teamId(teamId)
                .status(TeamTaskStatus.TIMEOUT)
                .errorMessage("Task execution timed out")
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Finalize the result with duration calculation.
     */
    public void finalize(Instant startTime) {
        this.completedAt = Instant.now();
        if (startTime != null) {
            this.startedAt = startTime;
            this.duration = Duration.between(startTime, completedAt);
        }
    }
}
