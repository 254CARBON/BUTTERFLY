package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of an agent task execution.
 * Contains the output, thoughts, tool calls, and provenance information.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {

    /**
     * ID of the task this result is for.
     */
    private String taskId;

    /**
     * ID of the agent that executed the task.
     */
    private String agentId;

    /**
     * ID of the conversation (if applicable).
     */
    private String conversationId;

    /**
     * Correlation ID for tracing.
     */
    private String correlationId;

    /**
     * Final output/response from the agent.
     */
    private String output;

    /**
     * List of thoughts generated during execution.
     */
    private List<AgentThought> thoughts;

    /**
     * List of tool calls made during execution.
     */
    private List<ToolInvocation> toolCalls;

    /**
     * Token usage statistics.
     */
    private TokenUsage tokenUsage;

    /**
     * Provenance information linking to other BUTTERFLY services.
     */
    private ProvenanceInfo provenance;

    /**
     * When execution started.
     */
    private Instant startedAt;

    /**
     * When execution completed.
     */
    private Instant completedAt;

    /**
     * Total execution duration.
     */
    private Duration duration;

    /**
     * Result status.
     */
    private ResultStatus status;

    /**
     * Error code if failed.
     */
    private String errorCode;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * Number of iterations (for ReAct agent).
     */
    private Integer iterations;

    /**
     * Risk score assessed for this result (0.0 - 1.0).
     */
    private Double riskScore;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Result status enumeration.
     */
    public enum ResultStatus {
        SUCCESS,          // Task completed successfully
        PARTIAL_SUCCESS,  // Task partially completed
        FAILURE,          // Task failed
        TIMEOUT,          // Task timed out
        CANCELLED,        // Task was cancelled
        REJECTED,         // Task was rejected by governance
        MAX_ITERATIONS    // Hit maximum iterations limit
    }

    /**
     * Token usage statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
        private String model;
        private String providerId;
        private Map<String, Integer> perCallBreakdown;
    }

    /**
     * Provenance information linking to BUTTERFLY ecosystem.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProvenanceInfo {
        private String platoPlanId;
        private String platoStepId;
        private String platoProofId;
        private String platoApprovalId;
        private String rimNodeId;
        private String capsuleId;
        private String nexusLearningSignalId;
        private List<String> synapseActionIds;
    }

    /**
     * Tool invocation record.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolInvocation {
        private String id;
        private String toolId;
        private String toolName;
        private Map<String, Object> parameters;
        private Object result;
        private boolean success;
        private String errorCode;
        private String errorMessage;
        private Duration duration;
        private Instant timestamp;
        private String synapseActionId;
    }

    /**
     * Check if the result indicates success.
     */
    public boolean isSuccess() {
        return status == ResultStatus.SUCCESS;
    }

    /**
     * Check if the result indicates failure.
     */
    public boolean isFailure() {
        return status == ResultStatus.FAILURE ||
               status == ResultStatus.TIMEOUT ||
               status == ResultStatus.REJECTED;
    }

    /**
     * Get the number of tool calls made.
     */
    public int getToolCallCount() {
        return toolCalls != null ? toolCalls.size() : 0;
    }

    /**
     * Get the number of thoughts generated.
     */
    public int getThoughtCount() {
        return thoughts != null ? thoughts.size() : 0;
    }
}
