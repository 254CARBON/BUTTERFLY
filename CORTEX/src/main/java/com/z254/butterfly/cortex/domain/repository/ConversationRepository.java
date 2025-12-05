package com.z254.butterfly.cortex.domain.repository;

import com.z254.butterfly.cortex.domain.model.Conversation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Repository interface for Conversation persistence.
 */
public interface ConversationRepository {

    /**
     * Save a conversation.
     *
     * @param conversation the conversation to save
     * @return the saved conversation
     */
    Mono<Conversation> save(Conversation conversation);

    /**
     * Find a conversation by ID.
     *
     * @param id the conversation ID
     * @return the conversation or empty
     */
    Mono<Conversation> findById(String id);

    /**
     * Find conversations by user ID.
     *
     * @param userId the user ID
     * @return conversations for the user
     */
    Flux<Conversation> findByUserId(String userId);

    /**
     * Find conversations by agent ID.
     *
     * @param agentId the agent ID
     * @return conversations for the agent
     */
    Flux<Conversation> findByAgentId(String agentId);

    /**
     * Find conversations by user and agent.
     *
     * @param userId the user ID
     * @param agentId the agent ID
     * @return matching conversations
     */
    Flux<Conversation> findByUserIdAndAgentId(String userId, String agentId);

    /**
     * Find recent conversations.
     *
     * @param userId the user ID
     * @param limit maximum number of results
     * @return recent conversations
     */
    Flux<Conversation> findRecentByUserId(String userId, int limit);

    /**
     * Find conversations by status.
     *
     * @param status the status
     * @return conversations with the status
     */
    Flux<Conversation> findByStatus(Conversation.ConversationStatus status);

    /**
     * Find conversations last active after a time.
     *
     * @param after the time threshold
     * @return active conversations
     */
    Flux<Conversation> findByLastActiveAtAfter(Instant after);

    /**
     * Delete a conversation.
     *
     * @param id the conversation ID
     * @return completion signal
     */
    Mono<Void> deleteById(String id);

    /**
     * Check if a conversation exists.
     *
     * @param id the conversation ID
     * @return true if exists
     */
    Mono<Boolean> existsById(String id);

    /**
     * Archive old conversations.
     *
     * @param before archive conversations last active before this time
     * @return number of conversations archived
     */
    Mono<Long> archiveOlderThan(Instant before);
}
