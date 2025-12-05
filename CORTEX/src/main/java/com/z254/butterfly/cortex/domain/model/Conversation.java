package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a conversation with an agent.
 * Tracks conversation history, metadata, and associated tasks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    /**
     * Unique identifier for this conversation.
     */
    private String id;

    /**
     * ID of the agent for this conversation.
     */
    private String agentId;

    /**
     * User ID who owns this conversation.
     */
    private String userId;

    /**
     * Title or summary of the conversation.
     */
    private String title;

    /**
     * Namespace context.
     */
    private String namespace;

    /**
     * Messages in the conversation.
     */
    private List<ConversationMessage> messages;

    /**
     * List of task IDs associated with this conversation.
     */
    private List<String> taskIds;

    /**
     * Current status of the conversation.
     */
    private ConversationStatus status;

    /**
     * Total token count for the conversation.
     */
    private TokenSummary tokenSummary;

    /**
     * When the conversation was created.
     */
    private Instant createdAt;

    /**
     * When the conversation was last updated.
     */
    private Instant updatedAt;

    /**
     * When the conversation was last active.
     */
    private Instant lastActiveAt;

    /**
     * CAPSULE ID for persistent storage.
     */
    private String capsuleId;

    /**
     * RIM node ID this conversation relates to.
     */
    private String rimNodeId;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Conversation status.
     */
    public enum ConversationStatus {
        ACTIVE,     // Conversation is active
        PAUSED,     // Conversation is paused
        COMPLETED,  // Conversation is completed
        ARCHIVED    // Conversation is archived
    }

    /**
     * A single message in the conversation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        private String id;
        private String role;      // system, user, assistant, tool
        private String content;
        private String taskId;    // Associated task ID (if agent response)
        private String toolName;  // Tool name (if role is tool)
        private String toolCallId;
        private Integer tokenCount;
        private Instant timestamp;
        private Map<String, Object> metadata;
    }

    /**
     * Token usage summary for the conversation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenSummary {
        private int totalInputTokens;
        private int totalOutputTokens;
        private int totalTokens;
        private int messageCount;
        private Instant lastUpdated;
    }

    /**
     * Add a message to the conversation.
     */
    public void addMessage(String role, String content, Integer tokenCount) {
        if (messages == null) {
            messages = new java.util.ArrayList<>();
        }
        messages.add(ConversationMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .role(role)
                .content(content)
                .tokenCount(tokenCount)
                .timestamp(Instant.now())
                .build());
        this.lastActiveAt = Instant.now();
        this.updatedAt = Instant.now();
        
        // Update token summary
        if (tokenSummary == null) {
            tokenSummary = TokenSummary.builder().build();
        }
        if (tokenCount != null) {
            if ("user".equals(role)) {
                tokenSummary.totalInputTokens += tokenCount;
            } else if ("assistant".equals(role)) {
                tokenSummary.totalOutputTokens += tokenCount;
            }
            tokenSummary.totalTokens = tokenSummary.totalInputTokens + tokenSummary.totalOutputTokens;
            tokenSummary.messageCount++;
            tokenSummary.lastUpdated = Instant.now();
        }
    }

    /**
     * Add a task ID to the conversation.
     */
    public void addTaskId(String taskId) {
        if (taskIds == null) {
            taskIds = new java.util.ArrayList<>();
        }
        taskIds.add(taskId);
    }

    /**
     * Get the message count.
     */
    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }

    /**
     * Check if conversation is active.
     */
    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    /**
     * Get recent messages for context window.
     */
    public List<ConversationMessage> getRecentMessages(int limit) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, messages.size() - limit);
        return messages.subList(start, messages.size());
    }
}
