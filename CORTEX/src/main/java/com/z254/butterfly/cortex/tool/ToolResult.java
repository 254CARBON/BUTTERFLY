package com.z254.butterfly.cortex.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Result of a tool execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * Tool ID that was executed.
     */
    private String toolId;

    /**
     * Whether execution was successful.
     */
    private boolean success;

    /**
     * Result content (for successful executions).
     */
    private Object content;

    /**
     * Result as string (for LLM consumption).
     */
    private String output;

    /**
     * Error code (for failed executions).
     */
    private String errorCode;

    /**
     * Error message (for failed executions).
     */
    private String errorMessage;

    /**
     * Execution duration.
     */
    private Duration duration;

    /**
     * When execution started.
     */
    private Instant startedAt;

    /**
     * When execution completed.
     */
    private Instant completedAt;

    /**
     * SYNAPSE action ID (if executed via SYNAPSE).
     */
    private String synapseActionId;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Create a success result with string output.
     */
    public static ToolResult success(String toolId, String output) {
        return ToolResult.builder()
                .toolId(toolId)
                .success(true)
                .output(output)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Create a success result with object content.
     */
    public static ToolResult success(String toolId, Object content, String output) {
        return ToolResult.builder()
                .toolId(toolId)
                .success(true)
                .content(content)
                .output(output)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Create a failure result.
     */
    public static ToolResult failure(String toolId, String errorCode, String errorMessage) {
        return ToolResult.builder()
                .toolId(toolId)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .output("Error: " + errorMessage)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * Create a timeout result.
     */
    public static ToolResult timeout(String toolId, Duration timeout) {
        return ToolResult.builder()
                .toolId(toolId)
                .success(false)
                .errorCode("TIMEOUT")
                .errorMessage("Tool execution timed out after " + timeout.toSeconds() + " seconds")
                .output("Error: Tool execution timed out")
                .completedAt(Instant.now())
                .duration(timeout)
                .build();
    }

    /**
     * Get the output for LLM consumption.
     */
    public String getOutputForLLM() {
        if (success) {
            return output != null ? output : String.valueOf(content);
        } else {
            return "Error: " + errorMessage;
        }
    }
}
