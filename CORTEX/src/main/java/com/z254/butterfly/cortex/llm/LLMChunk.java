package com.z254.butterfly.cortex.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A streaming chunk from LLM completions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMChunk {

    /**
     * Chunk ID (same for all chunks in a response).
     */
    private String id;

    /**
     * Model used.
     */
    private String model;

    /**
     * Content delta for this chunk.
     */
    private String contentDelta;

    /**
     * Tool call deltas for this chunk.
     */
    private List<ToolCallDelta> toolCallDeltas;

    /**
     * Whether this is the final chunk.
     */
    private boolean finished;

    /**
     * Finish reason (only set if finished).
     */
    private LLMResponse.FinishReason finishReason;

    /**
     * Usage statistics (only set if finished).
     */
    private LLMResponse.Usage usage;

    /**
     * Tool call delta in streaming.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallDelta {
        private int index;
        private String id;
        private String type;
        private String functionName;
        private String argumentsDelta;
    }

    /**
     * Check if this chunk has content.
     */
    public boolean hasContent() {
        return contentDelta != null && !contentDelta.isEmpty();
    }

    /**
     * Check if this chunk has tool calls.
     */
    public boolean hasToolCalls() {
        return toolCallDeltas != null && !toolCallDeltas.isEmpty();
    }

    /**
     * Create a content chunk.
     */
    public static LLMChunk content(String id, String model, String content) {
        return LLMChunk.builder()
                .id(id)
                .model(model)
                .contentDelta(content)
                .finished(false)
                .build();
    }

    /**
     * Create a finished chunk.
     */
    public static LLMChunk finished(String id, String model, LLMResponse.FinishReason reason, 
                                     LLMResponse.Usage usage) {
        return LLMChunk.builder()
                .id(id)
                .model(model)
                .finished(true)
                .finishReason(reason)
                .usage(usage)
                .build();
    }
}
