package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single reasoning step/thought from an agent.
 * Used for streaming real-time agent progress and debugging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentThought {

    /**
     * Unique identifier for this thought.
     */
    private String id;

    /**
     * ID of the task this thought belongs to.
     */
    private String taskId;

    /**
     * ID of the agent that generated this thought.
     */
    private String agentId;

    /**
     * ID of the conversation (if applicable).
     */
    private String conversationId;

    /**
     * Type of thought.
     */
    private ThoughtType type;

    /**
     * Content of the thought.
     */
    private String content;

    /**
     * Iteration number (for ReAct agents).
     */
    private Integer iteration;

    /**
     * Step number within the iteration.
     */
    private Integer step;

    /**
     * Tool being called (if type is TOOL_CALL).
     */
    private String toolId;

    /**
     * Tool call parameters (if type is TOOL_CALL).
     */
    private Map<String, Object> toolParameters;

    /**
     * Tool call result (if type is OBSERVATION).
     */
    private Object toolResult;

    /**
     * Confidence score for this thought (0.0 - 1.0).
     */
    private Double confidence;

    /**
     * Token count for this thought.
     */
    private Integer tokenCount;

    /**
     * Timestamp when this thought was generated.
     */
    private Instant timestamp;

    /**
     * Duration to generate this thought.
     */
    private Long durationMs;

    /**
     * Parent thought ID (for branching thoughts).
     */
    private String parentThoughtId;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Types of agent thoughts.
     */
    public enum ThoughtType {
        /**
         * Initial analysis and understanding of the task.
         */
        ANALYSIS,

        /**
         * Reasoning about what to do next.
         */
        REASONING,

        /**
         * Decision to call a tool.
         */
        TOOL_CALL,

        /**
         * Observation/result from a tool call.
         */
        OBSERVATION,

        /**
         * Reflection on results or errors.
         */
        REFLECTION,

        /**
         * Planning step (for PlanAndExecute agent).
         */
        PLANNING,

        /**
         * Intermediate conclusion.
         */
        INTERMEDIATE,

        /**
         * Final answer/response.
         */
        FINAL_ANSWER,

        /**
         * Error encountered during execution.
         */
        ERROR,

        /**
         * Decision to stop/give up.
         */
        STOP
    }

    /**
     * Check if this thought represents a final answer.
     */
    public boolean isFinalAnswer() {
        return type == ThoughtType.FINAL_ANSWER;
    }

    /**
     * Check if this thought represents a tool call.
     */
    public boolean isToolCall() {
        return type == ThoughtType.TOOL_CALL;
    }

    /**
     * Check if this thought represents an error.
     */
    public boolean isError() {
        return type == ThoughtType.ERROR;
    }

    /**
     * Create a reasoning thought.
     */
    public static AgentThought reasoning(String taskId, String agentId, String content, int iteration) {
        return AgentThought.builder()
                .id(java.util.UUID.randomUUID().toString())
                .taskId(taskId)
                .agentId(agentId)
                .type(ThoughtType.REASONING)
                .content(content)
                .iteration(iteration)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a tool call thought.
     */
    public static AgentThought toolCall(String taskId, String agentId, String toolId, 
                                         Map<String, Object> params, int iteration) {
        return AgentThought.builder()
                .id(java.util.UUID.randomUUID().toString())
                .taskId(taskId)
                .agentId(agentId)
                .type(ThoughtType.TOOL_CALL)
                .toolId(toolId)
                .toolParameters(params)
                .iteration(iteration)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an observation thought.
     */
    public static AgentThought observation(String taskId, String agentId, Object result, int iteration) {
        return AgentThought.builder()
                .id(java.util.UUID.randomUUID().toString())
                .taskId(taskId)
                .agentId(agentId)
                .type(ThoughtType.OBSERVATION)
                .toolResult(result)
                .iteration(iteration)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a final answer thought.
     */
    public static AgentThought finalAnswer(String taskId, String agentId, String answer, int iteration) {
        return AgentThought.builder()
                .id(java.util.UUID.randomUUID().toString())
                .taskId(taskId)
                .agentId(agentId)
                .type(ThoughtType.FINAL_ANSWER)
                .content(answer)
                .iteration(iteration)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error thought.
     */
    public static AgentThought error(String taskId, String agentId, String errorMessage, int iteration) {
        return AgentThought.builder()
                .id(java.util.UUID.randomUUID().toString())
                .taskId(taskId)
                .agentId(agentId)
                .type(ThoughtType.ERROR)
                .content(errorMessage)
                .iteration(iteration)
                .timestamp(Instant.now())
                .build();
    }
}
