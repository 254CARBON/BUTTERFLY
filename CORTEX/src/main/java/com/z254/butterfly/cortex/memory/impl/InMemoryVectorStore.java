package com.z254.butterfly.cortex.memory.impl;

import com.z254.butterfly.cortex.memory.SemanticMemory.MemoryEntry;
import com.z254.butterfly.cortex.memory.SemanticMemory.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store implementation.
 * Useful for development and testing. For production, use Pinecone, Milvus, or pgvector.
 */
@Component
@Slf4j
public class InMemoryVectorStore {

    // namespace -> entryId -> entry
    private final Map<String, Map<String, StoredEntry>> store = new ConcurrentHashMap<>();

    /**
     * Store an entry with its embedding.
     */
    public Mono<Void> store(String namespace, MemoryEntry entry, List<Float> embedding) {
        return Mono.fromRunnable(() -> {
            store.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>())
                    .put(entry.getId(), new StoredEntry(entry, embedding));
            log.debug("Stored entry {} in namespace {}", entry.getId(), namespace);
        });
    }

    /**
     * Search for similar entries using cosine similarity.
     */
    public Flux<SearchResult> search(String namespace, List<Float> queryEmbedding, int topK, 
                                      double minSimilarity) {
        return Mono.fromCallable(() -> {
            Map<String, StoredEntry> namespaceStore = store.get(namespace);
            if (namespaceStore == null || namespaceStore.isEmpty()) {
                return List.<SearchResult>of();
            }

            // Calculate similarity scores
            List<SearchResult> results = namespaceStore.values().stream()
                    .map(stored -> {
                        double similarity = cosineSimilarity(queryEmbedding, stored.embedding);
                        return SearchResult.builder()
                                .entry(stored.entry)
                                .similarityScore(similarity)
                                .build();
                    })
                    .filter(r -> r.getSimilarityScore() >= minSimilarity)
                    .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                    .limit(topK)
                    .collect(Collectors.toList());

            // Update access counts
            results.forEach(r -> {
                StoredEntry stored = namespaceStore.get(r.getEntry().getId());
                if (stored != null) {
                    stored.entry.setLastAccessedAt(Instant.now());
                    stored.entry.setAccessCount(
                            (stored.entry.getAccessCount() != null ? stored.entry.getAccessCount() : 0) + 1);
                }
            });

            return results;
        }).flatMapMany(Flux::fromIterable);
    }

    /**
     * Search with metadata filtering.
     */
    public Flux<SearchResult> searchWithFilter(String namespace, List<Float> queryEmbedding, 
                                                int topK, double minSimilarity,
                                                Map<String, Object> metadataFilter) {
        return Mono.fromCallable(() -> {
            Map<String, StoredEntry> namespaceStore = store.get(namespace);
            if (namespaceStore == null || namespaceStore.isEmpty()) {
                return List.<SearchResult>of();
            }

            return namespaceStore.values().stream()
                    .filter(stored -> matchesFilter(stored.entry, metadataFilter))
                    .map(stored -> {
                        double similarity = cosineSimilarity(queryEmbedding, stored.embedding);
                        return SearchResult.builder()
                                .entry(stored.entry)
                                .similarityScore(similarity)
                                .build();
                    })
                    .filter(r -> r.getSimilarityScore() >= minSimilarity)
                    .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                    .limit(topK)
                    .collect(Collectors.toList());
        }).flatMapMany(Flux::fromIterable);
    }

    /**
     * Get entry by ID.
     */
    public Mono<MemoryEntry> getById(String namespace, String entryId) {
        return Mono.fromCallable(() -> {
            Map<String, StoredEntry> namespaceStore = store.get(namespace);
            if (namespaceStore == null) {
                return null;
            }
            StoredEntry stored = namespaceStore.get(entryId);
            return stored != null ? stored.entry : null;
        });
    }

    /**
     * Update an entry.
     */
    public Mono<Void> update(String namespace, MemoryEntry entry, List<Float> embedding) {
        return Mono.fromRunnable(() -> {
            Map<String, StoredEntry> namespaceStore = store.get(namespace);
            if (namespaceStore != null) {
                namespaceStore.put(entry.getId(), new StoredEntry(entry, embedding));
            }
        });
    }

    /**
     * Delete an entry.
     */
    public Mono<Void> delete(String namespace, String entryId) {
        return Mono.fromRunnable(() -> {
            Map<String, StoredEntry> namespaceStore = store.get(namespace);
            if (namespaceStore != null) {
                namespaceStore.remove(entryId);
            }
        });
    }

    /**
     * Clear a namespace.
     */
    public Mono<Void> clearNamespace(String namespace) {
        return Mono.fromRunnable(() -> store.remove(namespace));
    }

    /**
     * Count entries in a namespace.
     */
    public Mono<Long> count(String namespace) {
        return Mono.fromCallable(() -> {
            Map<String, StoredEntry> namespaceStore = store.get(namespace);
            return namespaceStore != null ? (long) namespaceStore.size() : 0L;
        });
    }

    /**
     * Calculate cosine similarity between two vectors.
     */
    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            float ai = a.get(i);
            float bi = b.get(i);
            dotProduct += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Check if entry matches metadata filter.
     */
    private boolean matchesFilter(MemoryEntry entry, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        Map<String, Object> metadata = entry.getMetadata();
        if (metadata == null) {
            return false;
        }

        for (Map.Entry<String, Object> f : filter.entrySet()) {
            Object value = metadata.get(f.getKey());
            if (!Objects.equals(value, f.getValue())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Internal storage record.
     */
    private static class StoredEntry {
        final MemoryEntry entry;
        final List<Float> embedding;

        StoredEntry(MemoryEntry entry, List<Float> embedding) {
            this.entry = entry;
            this.embedding = embedding;
        }
    }
}
