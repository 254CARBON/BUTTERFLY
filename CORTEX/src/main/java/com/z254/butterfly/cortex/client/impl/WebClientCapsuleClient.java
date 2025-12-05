package com.z254.butterfly.cortex.client.impl;

import com.z254.butterfly.cortex.client.CapsuleClient;
import com.z254.butterfly.cortex.config.CortexProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * WebClient-based implementation of CapsuleClient.
 * Uses circuit breaker and retry for resilience.
 */
@Component
@Slf4j
public class WebClientCapsuleClient implements CapsuleClient {

    private final WebClient webClient;
    private final CortexProperties.ClientProperties.CapsuleClientProperties config;
    private final boolean stubMode;

    public WebClientCapsuleClient(CortexProperties cortexProperties) {
        this.config = cortexProperties.getClients().getCapsule();
        
        // Check if CAPSULE is available or use stub mode
        this.stubMode = config.getBaseUrl() == null || config.getBaseUrl().isEmpty();
        
        if (!stubMode) {
            this.webClient = WebClient.builder()
                    .baseUrl(config.getBaseUrl())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.webClient = null;
            log.warn("CAPSULE client running in stub mode - no actual service connection");
        }
    }

    @Override
    @CircuitBreaker(name = "capsule")
    @Retry(name = "llm")
    public Mono<String> storeSnapshot(ConversationSnapshot snapshot) {
        if (stubMode) {
            return Mono.just("stub-capsule-" + System.currentTimeMillis());
        }

        return webClient.post()
                .uri("/api/v1/capsules")
                .bodyValue(snapshot)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(config.getTimeout())
                .map(response -> (String) response.get("id"))
                .doOnSuccess(id -> log.debug("Stored snapshot in CAPSULE: {}", id))
                .doOnError(e -> log.error("Failed to store snapshot in CAPSULE: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "capsule")
    public Mono<ConversationSnapshot> getSnapshot(String capsuleId) {
        if (stubMode) {
            return Mono.empty();
        }

        return webClient.get()
                .uri("/api/v1/capsules/{id}", capsuleId)
                .retrieve()
                .bodyToMono(ConversationSnapshot.class)
                .timeout(config.getTimeout())
                .doOnError(e -> log.error("Failed to get snapshot from CAPSULE: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "capsule")
    public Flux<ConversationSnapshot> getHistory(String agentId, String userId, int limit) {
        if (stubMode) {
            return Flux.empty();
        }

        return webClient.get()
                .uri(builder -> builder
                        .path("/api/v1/capsules/history")
                        .queryParam("agentId", agentId)
                        .queryParam("userId", userId)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToFlux(ConversationSnapshot.class)
                .timeout(config.getTimeout())
                .doOnError(e -> log.error("Failed to get history from CAPSULE: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "capsule")
    public Flux<ConversationSnapshot> queryByRimNode(String rimNodeId, int limit) {
        if (stubMode) {
            return Flux.empty();
        }

        return webClient.get()
                .uri(builder -> builder
                        .path("/api/v1/capsules/by-rim-node")
                        .queryParam("rimNodeId", rimNodeId)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToFlux(ConversationSnapshot.class)
                .timeout(config.getTimeout())
                .doOnError(e -> log.error("Failed to query CAPSULE by RIM node: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "capsule")
    public Mono<ConversationSnapshot> getAtTime(String conversationId, Instant timestamp) {
        if (stubMode) {
            return Mono.empty();
        }

        return webClient.get()
                .uri(builder -> builder
                        .path("/api/v1/capsules/time-travel")
                        .queryParam("conversationId", conversationId)
                        .queryParam("timestamp", timestamp.toString())
                        .build())
                .retrieve()
                .bodyToMono(ConversationSnapshot.class)
                .timeout(config.getTimeout())
                .doOnError(e -> log.error("Failed time-travel query to CAPSULE: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "capsule")
    public Mono<Void> updateSnapshot(String capsuleId, ConversationSnapshot snapshot) {
        if (stubMode) {
            return Mono.empty();
        }

        return webClient.put()
                .uri("/api/v1/capsules/{id}", capsuleId)
                .bodyValue(snapshot)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(config.getTimeout())
                .doOnError(e -> log.error("Failed to update snapshot in CAPSULE: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "capsule")
    public Mono<Void> deleteSnapshot(String capsuleId) {
        if (stubMode) {
            return Mono.empty();
        }

        return webClient.delete()
                .uri("/api/v1/capsules/{id}", capsuleId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(config.getTimeout())
                .doOnError(e -> log.error("Failed to delete snapshot from CAPSULE: {}", e.getMessage()));
    }

    @Override
    public Mono<Boolean> isAvailable() {
        if (stubMode) {
            return Mono.just(false);
        }

        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> "UP".equals(response.get("status")))
                .onErrorReturn(false);
    }
}
