package com.z254.butterfly.cortex.llm;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Interface for LLM provider implementations.
 * Each provider (OpenAI, Anthropic, Ollama) implements this interface.
 */
public interface LLMProvider {

    /**
     * Complete a prompt with the LLM (non-streaming).
     *
     * @param request the completion request
     * @return the completion response
     */
    Mono<LLMResponse> complete(LLMRequest request);

    /**
     * Stream a completion from the LLM.
     *
     * @param request the completion request
     * @return flux of completion chunks
     */
    Flux<LLMChunk> stream(LLMRequest request);

    /**
     * Generate embeddings for text.
     *
     * @param text the text to embed
     * @return list of embedding values
     */
    Mono<List<Float>> embed(String text);

    /**
     * Generate embeddings for multiple texts.
     *
     * @param texts the texts to embed
     * @return list of embedding lists
     */
    Mono<List<List<Float>>> embedBatch(List<String> texts);

    /**
     * Get the provider ID.
     *
     * @return provider ID (e.g., "openai", "anthropic", "ollama")
     */
    String getProviderId();

    /**
     * Check if this provider supports tool/function calling.
     *
     * @return true if tools are supported
     */
    boolean supportsTools();

    /**
     * Check if this provider supports streaming.
     *
     * @return true if streaming is supported
     */
    default boolean supportsStreaming() {
        return true;
    }

    /**
     * Check if this provider is currently available.
     *
     * @return true if the provider is available
     */
    Mono<Boolean> isAvailable();

    /**
     * Get the default model for this provider.
     *
     * @return default model ID
     */
    String getDefaultModel();

    /**
     * Count tokens in a text (approximate).
     *
     * @param text the text to count
     * @return approximate token count
     */
    default int countTokens(String text) {
        // Simple approximation: ~4 characters per token
        return text != null ? text.length() / 4 : 0;
    }

    /**
     * Count tokens in a request (approximate).
     *
     * @param request the request
     * @return approximate token count
     */
    default int countRequestTokens(LLMRequest request) {
        int total = 0;
        if (request.getMessages() != null) {
            for (LLMRequest.Message msg : request.getMessages()) {
                total += countTokens(msg.getContent());
            }
        }
        if (request.getTools() != null) {
            // Tools add overhead
            total += request.getTools().size() * 100;
        }
        return total;
    }
}
