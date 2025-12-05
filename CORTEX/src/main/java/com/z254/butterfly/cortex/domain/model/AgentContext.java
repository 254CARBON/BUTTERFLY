package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime context for agent task execution.
 * Contains working memory, token budget, governance context, and available tools.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    /**
     * ID of the task being executed.
     */
    private String taskId;

    /**
     * ID of the agent executing.
     */
    private String agentId;

    /**
     * ID of the conversation (if applicable).
     */
    private String conversationId;

    /**
     * Correlation ID for distributed tracing.
     */
    private String correlationId;

    /**
     * User ID who initiated the task.
     */
    private String userId;

    /**
     * Namespace context.
     */
    private String namespace;

    /**
     * Working memory for this task.
     */
    @Builder.Default
    private WorkingMemory workingMemory = new WorkingMemory();

    /**
     * Token budget for this task.
     */
    private TokenBudget tokenBudget;

    /**
     * Governance context.
     */
    private GovernanceContext governance;

    /**
     * Security context.
     */
    private SecurityContext security;

    /**
     * Set of tool IDs available for this task.
     */
    private Set<String> availableTools;

    /**
     * Messages history for conversation context.
     */
    private java.util.List<Message> messages;

    /**
     * When this context was created.
     */
    private Instant createdAt;

    /**
     * Additional context data.
     */
    private Map<String, Object> additionalContext;

    /**
     * Working memory for per-task context storage.
     */
    @Data
    @NoArgsConstructor
    public static class WorkingMemory {
        private final Map<String, Object> store = new ConcurrentHashMap<>();

        public void set(String key, Object value) {
            store.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object value = store.get(key);
            return type.isInstance(value) ? (T) value : null;
        }

        public Object get(String key) {
            return store.get(key);
        }

        public boolean has(String key) {
            return store.containsKey(key);
        }

        public void remove(String key) {
            store.remove(key);
        }

        public void clear() {
            store.clear();
        }

        public Map<String, Object> getAll() {
            return new ConcurrentHashMap<>(store);
        }

        public int size() {
            return store.size();
        }
    }

    /**
     * Token budget for controlling LLM usage.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenBudget {
        private int maxTokens;
        private int usedTokens;
        private int maxInputTokens;
        private int maxOutputTokens;
        private int usedInputTokens;
        private int usedOutputTokens;

        public int getRemainingTokens() {
            return Math.max(0, maxTokens - usedTokens);
        }

        public boolean hasRemaining() {
            return getRemainingTokens() > 0;
        }

        public double getUsagePercentage() {
            return maxTokens > 0 ? (double) usedTokens / maxTokens : 0.0;
        }

        public void addUsage(int input, int output) {
            this.usedInputTokens += input;
            this.usedOutputTokens += output;
            this.usedTokens = this.usedInputTokens + this.usedOutputTokens;
        }

        public boolean wouldExceed(int additionalTokens) {
            return (usedTokens + additionalTokens) > maxTokens;
        }
    }

    /**
     * Governance context for policy enforcement.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GovernanceContext {
        private String planId;
        private String stepId;
        private String policyId;
        private String approvalId;
        private Double riskScore;
        private boolean approved;
        private Set<String> permissions;
        private Map<String, Object> policyParams;
    }

    /**
     * Security context for the task.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityContext {
        private String userId;
        private String serviceId;
        private String namespace;
        private Set<String> roles;
        private Set<String> permissions;
        private String tokenId;
        private Instant tokenExpiry;
    }

    /**
     * Chat message in conversation history.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;  // system, user, assistant, tool
        private String content;
        private String name;  // tool name if role is tool
        private String toolCallId;  // tool call ID if role is tool
        private Map<String, Object> metadata;
        private Instant timestamp;
    }

    /**
     * Add usage to token budget.
     */
    public void addTokenUsage(int input, int output) {
        if (tokenBudget != null) {
            tokenBudget.addUsage(input, output);
        }
    }

    /**
     * Check if token budget allows more tokens.
     */
    public boolean hasTokenBudget(int additionalTokens) {
        return tokenBudget == null || !tokenBudget.wouldExceed(additionalTokens);
    }

    /**
     * Add a message to the conversation history.
     */
    public void addMessage(String role, String content) {
        if (messages == null) {
            messages = new java.util.ArrayList<>();
        }
        messages.add(Message.builder()
                .role(role)
                .content(content)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Create a default context for a task.
     */
    public static AgentContext forTask(AgentTask task, Agent agent, int maxTokens) {
        return AgentContext.builder()
                .taskId(task.getId())
                .agentId(agent.getId())
                .conversationId(task.getConversationId())
                .correlationId(task.getCorrelationId())
                .userId(task.getUserId())
                .namespace(task.getNamespace())
                .workingMemory(new WorkingMemory())
                .tokenBudget(TokenBudget.builder()
                        .maxTokens(maxTokens)
                        .usedTokens(0)
                        .build())
                .availableTools(agent.getToolIds())
                .messages(new java.util.ArrayList<>())
                .createdAt(Instant.now())
                .build();
    }
}
