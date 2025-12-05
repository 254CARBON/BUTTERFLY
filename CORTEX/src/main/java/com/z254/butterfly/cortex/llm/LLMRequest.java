package com.z254.butterfly.cortex.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request object for LLM completions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequest {

    /**
     * Model to use for completion.
     */
    private String model;

    /**
     * Messages for the conversation.
     */
    private List<Message> messages;

    /**
     * Tools/functions available for the model to call.
     */
    private List<Tool> tools;

    /**
     * Tool choice behavior.
     */
    private ToolChoice toolChoice;

    /**
     * Temperature for sampling (0.0 - 2.0).
     */
    @Builder.Default
    private Double temperature = 0.7;

    /**
     * Maximum tokens to generate.
     */
    private Integer maxTokens;

    /**
     * Stop sequences.
     */
    private List<String> stopSequences;

    /**
     * Top-p sampling.
     */
    private Double topP;

    /**
     * Frequency penalty.
     */
    private Double frequencyPenalty;

    /**
     * Presence penalty.
     */
    private Double presencePenalty;

    /**
     * User identifier for tracking.
     */
    private String user;

    /**
     * Whether to stream the response.
     */
    @Builder.Default
    private boolean stream = false;

    /**
     * Response format.
     */
    private ResponseFormat responseFormat;

    /**
     * Additional provider-specific parameters.
     */
    private Map<String, Object> additionalParams;

    /**
     * A message in the conversation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;  // system, user, assistant, tool
        private String content;
        private String name;  // for tool messages
        private String toolCallId;  // for tool results
        private List<ToolCall> toolCalls;  // for assistant messages with tool calls
    }

    /**
     * A tool that the model can call.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        private String type;  // "function"
        private Function function;
    }

    /**
     * Function definition for tools.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;  // JSON Schema
    }

    /**
     * Tool call made by the model.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;
    }

    /**
     * Function call details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments;  // JSON string
    }

    /**
     * Tool choice behavior.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolChoice {
        private String type;  // "none", "auto", "required", or specific tool
        private Tool tool;    // if type is specific tool
    }

    /**
     * Response format specification.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseFormat {
        private String type;  // "text", "json_object"
    }

    // Factory methods for common message types

    public static Message systemMessage(String content) {
        return Message.builder()
                .role("system")
                .content(content)
                .build();
    }

    public static Message userMessage(String content) {
        return Message.builder()
                .role("user")
                .content(content)
                .build();
    }

    public static Message assistantMessage(String content) {
        return Message.builder()
                .role("assistant")
                .content(content)
                .build();
    }

    public static Message assistantMessage(List<ToolCall> toolCalls) {
        return Message.builder()
                .role("assistant")
                .toolCalls(toolCalls)
                .build();
    }

    public static Message toolMessage(String toolCallId, String name, String content) {
        return Message.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .name(name)
                .content(content)
                .build();
    }
}
