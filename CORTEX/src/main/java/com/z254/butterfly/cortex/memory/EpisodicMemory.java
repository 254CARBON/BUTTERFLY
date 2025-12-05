package com.z254.butterfly.cortex.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Interface for episodic memory.
 * Stores recent interaction history in a sliding window with configurable retention.
 */
public interface EpisodicMemory {

    /**
     * Record an episode (interaction) in memory.
     *
     * @param conversationId the conversation ID
     * @param episode the episode to record
     * @return completion signal
     */
    Mono<Void> record(String conversationId, Episode episode);

    /**
     * Retrieve recent episodes from a conversation.
     *
     * @param conversationId the conversation ID
     * @param limit maximum number of episodes to retrieve
     * @return flux of episodes (most recent last)
     */
    Flux<Episode> retrieve(String conversationId, int limit);

    /**
     * Retrieve episodes within a time range.
     *
     * @param conversationId the conversation ID
     * @param start start time (inclusive)
     * @param end end time (inclusive)
     * @return flux of episodes
     */
    Flux<Episode> retrieveByTimeRange(String conversationId, Instant start, Instant end);

    /**
     * Prune old episodes beyond retention period.
     *
     * @param conversationId the conversation ID
     * @param retention retention duration
     * @return number of episodes pruned
     */
    Mono<Long> prune(String conversationId, Duration retention);

    /**
     * Count episodes in a conversation.
     *
     * @param conversationId the conversation ID
     * @return episode count
     */
    Mono<Long> count(String conversationId);

    /**
     * Delete all episodes for a conversation.
     *
     * @param conversationId the conversation ID
     * @return completion signal
     */
    Mono<Void> clear(String conversationId);

    /**
     * An episode represents a single interaction/event in the conversation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class Episode {
        /**
         * Unique ID for this episode.
         */
        private String id;

        /**
         * Type of episode.
         */
        private EpisodeType type;

        /**
         * Role of the message sender.
         */
        private String role;

        /**
         * Content of the episode.
         */
        private String content;

        /**
         * Associated task ID (if any).
         */
        private String taskId;

        /**
         * Tool call information (if applicable).
         */
        private String toolName;
        private String toolResult;

        /**
         * Token count for this episode.
         */
        private Integer tokenCount;

        /**
         * When this episode occurred.
         */
        private Instant timestamp;

        /**
         * Additional metadata.
         */
        private Map<String, Object> metadata;

        /**
         * Create a user message episode.
         */
        public static Episode userMessage(String content, String taskId) {
            return Episode.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type(EpisodeType.USER_MESSAGE)
                    .role("user")
                    .content(content)
                    .taskId(taskId)
                    .timestamp(Instant.now())
                    .build();
        }

        /**
         * Create an assistant message episode.
         */
        public static Episode assistantMessage(String content, String taskId) {
            return Episode.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type(EpisodeType.ASSISTANT_MESSAGE)
                    .role("assistant")
                    .content(content)
                    .taskId(taskId)
                    .timestamp(Instant.now())
                    .build();
        }

        /**
         * Create a tool call episode.
         */
        public static Episode toolCall(String toolName, String result, String taskId) {
            return Episode.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type(EpisodeType.TOOL_CALL)
                    .role("tool")
                    .toolName(toolName)
                    .toolResult(result)
                    .taskId(taskId)
                    .timestamp(Instant.now())
                    .build();
        }
    }

    /**
     * Types of episodes.
     */
    enum EpisodeType {
        USER_MESSAGE,
        ASSISTANT_MESSAGE,
        SYSTEM_MESSAGE,
        TOOL_CALL,
        THOUGHT,
        ERROR
    }
}
