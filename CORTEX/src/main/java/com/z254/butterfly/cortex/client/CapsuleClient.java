package com.z254.butterfly.cortex.client;

import com.z254.butterfly.cortex.domain.model.Conversation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Client interface for CAPSULE service.
 * CAPSULE provides historical state storage and time-travel queries.
 */
public interface CapsuleClient {

    /**
     * Store a conversation snapshot in CAPSULE.
     *
     * @param snapshot the snapshot to store
     * @return the CAPSULE ID
     */
    Mono<String> storeSnapshot(ConversationSnapshot snapshot);

    /**
     * Retrieve a conversation snapshot by ID.
     *
     * @param capsuleId the CAPSULE ID
     * @return the snapshot or empty
     */
    Mono<ConversationSnapshot> getSnapshot(String capsuleId);

    /**
     * Retrieve conversation history for an agent/user.
     *
     * @param agentId the agent ID
     * @param userId the user ID
     * @param limit maximum results
     * @return flux of snapshots
     */
    Flux<ConversationSnapshot> getHistory(String agentId, String userId, int limit);

    /**
     * Query snapshots by RIM node.
     *
     * @param rimNodeId the RIM node ID
     * @param limit maximum results
     * @return flux of snapshots
     */
    Flux<ConversationSnapshot> queryByRimNode(String rimNodeId, int limit);

    /**
     * Time-travel query - get state at a specific point in time.
     *
     * @param conversationId the conversation ID
     * @param timestamp the point in time
     * @return the snapshot closest to that time
     */
    Mono<ConversationSnapshot> getAtTime(String conversationId, Instant timestamp);

    /**
     * Update an existing snapshot.
     *
     * @param capsuleId the CAPSULE ID
     * @param snapshot the updated snapshot
     * @return completion signal
     */
    Mono<Void> updateSnapshot(String capsuleId, ConversationSnapshot snapshot);

    /**
     * Delete a snapshot.
     *
     * @param capsuleId the CAPSULE ID
     * @return completion signal
     */
    Mono<Void> deleteSnapshot(String capsuleId);

    /**
     * Check if CAPSULE service is available.
     *
     * @return true if available
     */
    Mono<Boolean> isAvailable();

    /**
     * Conversation snapshot for CAPSULE storage.
     */
    record ConversationSnapshot(
            String capsuleId,
            String conversationId,
            String agentId,
            String userId,
            String rimNodeId,
            Conversation.ConversationStatus status,
            int messageCount,
            int totalTokens,
            String summary,
            Instant createdAt,
            Instant snapshotAt,
            Map<String, Object> metadata
    ) {
        public static ConversationSnapshot fromConversation(Conversation conversation) {
            return new ConversationSnapshot(
                    conversation.getCapsuleId(),
                    conversation.getId(),
                    conversation.getAgentId(),
                    conversation.getUserId(),
                    conversation.getRimNodeId(),
                    conversation.getStatus(),
                    conversation.getMessageCount(),
                    conversation.getTokenSummary() != null ? conversation.getTokenSummary().getTotalTokens() : 0,
                    conversation.getTitle(),
                    conversation.getCreatedAt(),
                    Instant.now(),
                    conversation.getMetadata()
            );
        }
    }
}
