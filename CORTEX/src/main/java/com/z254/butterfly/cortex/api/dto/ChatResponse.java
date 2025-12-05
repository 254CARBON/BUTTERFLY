package com.z254.butterfly.cortex.api.dto;

import com.z254.butterfly.cortex.domain.model.AgentResult;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for chat endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String conversationId;
    private String taskId;
    private String message;
    private List<ThoughtSummary> thoughts;
    private TokenUsageSummary tokenUsage;
    private String status;
    private Long durationMs;
    private Instant timestamp;
    private Map<String, Object> metadata;

    /**
     * Summary of a thought for the response.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThoughtSummary {
        private String type;
        private String content;
        private String toolName;
        private Instant timestamp;
    }

    /**
     * Token usage summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsageSummary {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
    }

    /**
     * Create from AgentResult.
     */
    public static ChatResponse fromResult(AgentResult result, String conversationId) {
        List<ThoughtSummary> thoughtSummaries = null;
        if (result.getThoughts() != null) {
            thoughtSummaries = result.getThoughts().stream()
                    .map(t -> ThoughtSummary.builder()
                            .type(t.getType().name())
                            .content(t.getContent())
                            .toolName(t.getToolId())
                            .timestamp(t.getTimestamp())
                            .build())
                    .toList();
        }

        TokenUsageSummary tokenUsage = null;
        if (result.getTokenUsage() != null) {
            tokenUsage = TokenUsageSummary.builder()
                    .inputTokens(result.getTokenUsage().getInputTokens())
                    .outputTokens(result.getTokenUsage().getOutputTokens())
                    .totalTokens(result.getTokenUsage().getTotalTokens())
                    .build();
        }

        return ChatResponse.builder()
                .conversationId(conversationId)
                .taskId(result.getTaskId())
                .message(result.getOutput())
                .thoughts(thoughtSummaries)
                .tokenUsage(tokenUsage)
                .status(result.getStatus().name())
                .durationMs(result.getDuration() != null ? result.getDuration().toMillis() : null)
                .timestamp(Instant.now())
                .build();
    }
}
