package com.z254.butterfly.cortex.memory.impl;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.llm.LLMProvider;
import com.z254.butterfly.cortex.llm.LLMProviderRegistry;
import com.z254.butterfly.cortex.memory.SemanticMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vector store-backed semantic memory implementation.
 * Uses embeddings for semantic search. Backed by InMemoryVectorStore for development,
 * can be swapped for Pinecone, Milvus, or pgvector in production.
 */
@Component
@Slf4j
public class VectorStoreSemanticMemory implements SemanticMemory {

    private final InMemoryVectorStore vectorStore;
    private final LLMProviderRegistry llmProviderRegistry;
    private final CortexProperties.MemoryProperties.SemanticProperties config;

    public VectorStoreSemanticMemory(
            InMemoryVectorStore vectorStore,
            LLMProviderRegistry llmProviderRegistry,
            CortexProperties cortexProperties) {
        this.vectorStore = vectorStore;
        this.llmProviderRegistry = llmProviderRegistry;
        this.config = cortexProperties.getMemory().getSemantic();
    }

    @Override
    public Mono<Void> store(String namespace, MemoryEntry entry) {
        // Generate ID if not present
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID().toString());
        }
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(Instant.now());
        }
        
        // If embedding not provided, generate it
        if (entry.getEmbedding() == null || entry.getEmbedding().isEmpty()) {
            return getEmbeddingProvider()
                    .embed(entry.getContent())
                    .flatMap(embedding -> vectorStore.store(namespace, entry, embedding));
        }
        
        return vectorStore.store(namespace, entry, entry.getEmbedding());
    }

    @Override
    public Mono<Void> storeBatch(String namespace, List<MemoryEntry> entries) {
        return Flux.fromIterable(entries)
                .flatMap(entry -> store(namespace, entry))
                .then();
    }

    @Override
    public Flux<SearchResult> search(String namespace, String query, int topK) {
        return getEmbeddingProvider()
                .embed(query)
                .flatMapMany(embedding -> searchByEmbedding(namespace, embedding, topK));
    }

    @Override
    public Flux<SearchResult> searchByEmbedding(String namespace, List<Float> embedding, int topK) {
        return vectorStore.search(namespace, embedding, topK, config.getSimilarityThreshold());
    }

    @Override
    public Flux<SearchResult> searchWithFilter(String namespace, String query, int topK,
                                                Map<String, Object> metadataFilter) {
        return getEmbeddingProvider()
                .embed(query)
                .flatMapMany(embedding -> 
                        vectorStore.searchWithFilter(namespace, embedding, topK, 
                                config.getSimilarityThreshold(), metadataFilter));
    }

    @Override
    public Mono<MemoryEntry> getById(String namespace, String entryId) {
        return vectorStore.getById(namespace, entryId);
    }

    @Override
    public Mono<Void> update(String namespace, MemoryEntry entry) {
        // Re-generate embedding if content changed
        return getEmbeddingProvider()
                .embed(entry.getContent())
                .flatMap(embedding -> vectorStore.update(namespace, entry, embedding));
    }

    @Override
    public Mono<Void> delete(String namespace, String entryId) {
        return vectorStore.delete(namespace, entryId);
    }

    @Override
    public Mono<Long> compress(String namespace) {
        // Compression: summarize old memories and consolidate
        if (!config.isCompressionEnabled()) {
            return Mono.just(0L);
        }
        
        return count(namespace)
                .flatMap(count -> {
                    if (count < config.getCompressionThreshold()) {
                        return Mono.just(0L);
                    }
                    
                    log.info("Compressing semantic memory for namespace {}: {} entries", 
                            namespace, count);
                    
                    // For now, just return 0 - full implementation would:
                    // 1. Identify old/low-importance entries
                    // 2. Group related entries
                    // 3. Summarize using LLM
                    // 4. Store summaries and delete originals
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<Void> clearNamespace(String namespace) {
        return vectorStore.clearNamespace(namespace);
    }

    @Override
    public Mono<Long> count(String namespace) {
        return vectorStore.count(namespace);
    }

    /**
     * Get the LLM provider for embeddings.
     * Prefers OpenAI or Ollama for embeddings since Anthropic doesn't support them.
     */
    private LLMProvider getEmbeddingProvider() {
        // Try OpenAI first (has best embeddings)
        if (llmProviderRegistry.hasProvider("openai")) {
            return llmProviderRegistry.getProvider("openai");
        }
        // Fallback to Ollama
        if (llmProviderRegistry.hasProvider("ollama")) {
            return llmProviderRegistry.getProvider("ollama");
        }
        // Use default
        return llmProviderRegistry.getDefaultProvider();
    }
}
