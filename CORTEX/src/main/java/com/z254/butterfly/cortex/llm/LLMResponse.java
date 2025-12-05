package com.z254.butterfly.cortex.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response object from LLM completions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponse {

    /**
     * Unique ID for this response.
     */
    private String id;

    /**
     * Model used for generation.
     */
    private String model;

    /**
     * Provider ID.
     */
    private String providerId;

    /**
     * Generated content.
     */
    private String content;

    /**
     * Tool calls requested by the model.
     */
    private List<LLMRequest.ToolCall> toolCalls;

    /**
     * Finish reason.
     */
    private FinishReason finishReason;

    /**
     * Token usage statistics.
     */
    private Usage usage;

    /**
     * Generation latency in milliseconds.
     */
    private Long latencyMs;

    /**
     * Additional response metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Reasons for completion finish.
     */
    public enum FinishReason {
        STOP,           // Natural completion
        LENGTH,         // Hit max tokens
        TOOL_CALLS,     // Model requested tool calls
        CONTENT_FILTER, // Blocked by content filter
        ERROR           // Error occurred
    }

    /**
     * Token usage statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    /**
     * Check if the response has tool calls.
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * Check if the response finished due to stop.
     */
    public boolean isComplete() {
        return finishReason == FinishReason.STOP;
    }

    /**
     * Check if the response was truncated.
     */
    public boolean isTruncated() {
        return finishReason == FinishReason.LENGTH;
    }

    /**
     * Get total tokens used.
     */
    public int getTotalTokens() {
        return usage != null ? usage.getTotalTokens() : 0;
    }

    /**
     * Create a simple text response.
     */
    public static LLMResponse text(String content, String model, String providerId) {
        return LLMResponse.builder()
                .content(content)
                .model(model)
                .providerId(providerId)
                .finishReason(FinishReason.STOP)
                .build();
    }

    /**
     * Create a tool calls response.
     */
    public static LLMResponse toolCalls(List<LLMRequest.ToolCall> calls, String model, String providerId) {
        return LLMResponse.builder()
                .toolCalls(calls)
                .model(model)
                .providerId(providerId)
                .finishReason(FinishReason.TOOL_CALLS)
                .build();
    }
}
