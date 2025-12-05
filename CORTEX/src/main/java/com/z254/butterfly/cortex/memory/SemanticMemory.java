package com.z254.butterfly.cortex.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Interface for semantic memory.
 * Stores long-term knowledge using vector embeddings for semantic search.
 */
public interface SemanticMemory {

    /**
     * Store a memory entry with its embedding.
     *
     * @param namespace the namespace (e.g., agent ID, user ID)
     * @param entry the memory entry
     * @return completion signal
     */
    Mono<Void> store(String namespace, MemoryEntry entry);

    /**
     * Store multiple memory entries.
     *
     * @param namespace the namespace
     * @param entries the entries to store
     * @return completion signal
     */
    Mono<Void> storeBatch(String namespace, List<MemoryEntry> entries);

    /**
     * Search for similar memories using semantic similarity.
     *
     * @param namespace the namespace
     * @param query the search query (will be embedded)
     * @param topK number of results to return
     * @return flux of matching entries with similarity scores
     */
    Flux<SearchResult> search(String namespace, String query, int topK);

    /**
     * Search for similar memories using a pre-computed embedding.
     *
     * @param namespace the namespace
     * @param embedding the query embedding
     * @param topK number of results
     * @return flux of matching entries
     */
    Flux<SearchResult> searchByEmbedding(String namespace, List<Float> embedding, int topK);

    /**
     * Search with metadata filtering.
     *
     * @param namespace the namespace
     * @param query the search query
     * @param topK number of results
     * @param metadataFilter filter conditions
     * @return flux of matching entries
     */
    Flux<SearchResult> searchWithFilter(String namespace, String query, int topK, 
                                         Map<String, Object> metadataFilter);

    /**
     * Get a specific memory entry by ID.
     *
     * @param namespace the namespace
     * @param entryId the entry ID
     * @return the entry or empty
     */
    Mono<MemoryEntry> getById(String namespace, String entryId);

    /**
     * Update a memory entry.
     *
     * @param namespace the namespace
     * @param entry the updated entry
     * @return completion signal
     */
    Mono<Void> update(String namespace, MemoryEntry entry);

    /**
     * Delete a memory entry.
     *
     * @param namespace the namespace
     * @param entryId the entry ID
     * @return completion signal
     */
    Mono<Void> delete(String namespace, String entryId);

    /**
     * Compress old memories (summarize and consolidate).
     *
     * @param namespace the namespace
     * @return number of entries compressed
     */
    Mono<Long> compress(String namespace);

    /**
     * Clear all entries in a namespace.
     *
     * @param namespace the namespace
     * @return completion signal
     */
    Mono<Void> clearNamespace(String namespace);

    /**
     * Count entries in a namespace.
     *
     * @param namespace the namespace
     * @return entry count
     */
    Mono<Long> count(String namespace);

    /**
     * A memory entry stored in semantic memory.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class MemoryEntry {
        /**
         * Unique ID for this entry.
         */
        private String id;

        /**
         * Text content of the memory.
         */
        private String content;

        /**
         * Pre-computed embedding (optional, will be computed if not provided).
         */
        private List<Float> embedding;

        /**
         * Type of memory.
         */
        private MemoryType type;

        /**
         * Source of this memory (conversation ID, task ID, etc.).
         */
        private String source;

        /**
         * When this memory was created.
         */
        private Instant createdAt;

        /**
         * When this memory was last accessed.
         */
        private Instant lastAccessedAt;

        /**
         * Access count for this memory.
         */
        private Integer accessCount;

        /**
         * Importance score (0.0 - 1.0).
         */
        private Double importance;

        /**
         * Additional metadata.
         */
        private Map<String, Object> metadata;

        /**
         * Create a fact entry.
         */
        public static MemoryEntry fact(String content, String source) {
            return MemoryEntry.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .content(content)
                    .type(MemoryType.FACT)
                    .source(source)
                    .createdAt(Instant.now())
                    .accessCount(0)
                    .importance(0.5)
                    .build();
        }

        /**
         * Create a summary entry.
         */
        public static MemoryEntry summary(String content, String source) {
            return MemoryEntry.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .content(content)
                    .type(MemoryType.SUMMARY)
                    .source(source)
                    .createdAt(Instant.now())
                    .accessCount(0)
                    .importance(0.7)
                    .build();
        }
    }

    /**
     * Search result with similarity score.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SearchResult {
        private MemoryEntry entry;
        private double similarityScore;
    }

    /**
     * Types of semantic memories.
     */
    enum MemoryType {
        FACT,           // Factual information
        SUMMARY,        // Summarized content
        PREFERENCE,     // User preferences
        INSTRUCTION,    // Learned instructions
        REFLECTION,     // Agent reflections
        CONTEXT         // Contextual information
    }
}
