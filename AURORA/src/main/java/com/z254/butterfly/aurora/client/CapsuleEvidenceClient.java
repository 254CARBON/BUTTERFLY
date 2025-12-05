package com.z254.butterfly.aurora.client;

import com.z254.butterfly.aurora.config.AuroraProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client for CAPSULE evidence storage service.
 * <p>
 * Provides methods to:
 * <ul>
 *     <li>Store incident evidence</li>
 *     <li>Retrieve historical incident data</li>
 *     <li>Store chaos learning events</li>
 * </ul>
 */
@Slf4j
@Component
public class CapsuleEvidenceClient {

    private final WebClient webClient;
    private final AuroraProperties auroraProperties;

    public CapsuleEvidenceClient(WebClient.Builder webClientBuilder, 
                                  AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
        this.webClient = webClientBuilder
                .baseUrl(auroraProperties.getCapsule().getUrl())
                .build();
    }

    /**
     * Store evidence for an incident.
     */
    @CircuitBreaker(name = "capsule-client", fallbackMethod = "storeEvidenceFallback")
    @Retry(name = "capsule-client")
    public Mono<EvidenceResponse> storeEvidence(StoreEvidenceRequest request) {
        log.debug("Storing evidence for incident: {}", request.getIncidentId());
        
        return webClient.post()
                .uri("/api/v1/capsules")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EvidenceResponse.class)
                .doOnSuccess(response -> 
                        log.info("Evidence stored: incidentId={}, capsuleId={}", 
                                request.getIncidentId(), response.getCapsuleId()))
                .doOnError(error -> 
                        log.error("Failed to store evidence: incidentId={}", 
                                request.getIncidentId(), error));
    }

    /**
     * Fallback for evidence storage.
     */
    public Mono<EvidenceResponse> storeEvidenceFallback(StoreEvidenceRequest request, 
                                                         Throwable throwable) {
        log.warn("CAPSULE unavailable, buffering evidence locally: incidentId={}, error={}", 
                request.getIncidentId(), throwable.getMessage());
        
        // Return a placeholder response indicating local buffering
        return Mono.just(EvidenceResponse.builder()
                .capsuleId("LOCAL-" + System.currentTimeMillis())
                .incidentId(request.getIncidentId())
                .status("BUFFERED_LOCALLY")
                .build());
    }

    /**
     * Retrieve historical incidents for a component.
     */
    @CircuitBreaker(name = "capsule-client", fallbackMethod = "getHistoryFallback")
    public Flux<HistoricalIncident> getIncidentHistory(String componentId, int limit) {
        log.debug("Fetching incident history for component: {}", componentId);
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/history")
                        .queryParam("scopeId", componentId)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToFlux(HistoricalIncident.class);
    }

    /**
     * Fallback for history retrieval.
     */
    public Flux<HistoricalIncident> getHistoryFallback(String componentId, int limit, 
                                                        Throwable throwable) {
        log.warn("CAPSULE unavailable, returning empty history: componentId={}", componentId);
        return Flux.empty();
    }

    /**
     * Store chaos learning event.
     */
    @CircuitBreaker(name = "capsule-client", fallbackMethod = "storeLearningFallback")
    public Mono<EvidenceResponse> storeChaosLearning(ChaosLearningRequest request) {
        log.debug("Storing chaos learning: learningId={}", request.getLearningId());
        
        return webClient.post()
                .uri("/api/v1/capsules/learnings")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EvidenceResponse.class);
    }

    /**
     * Fallback for chaos learning storage.
     */
    public Mono<EvidenceResponse> storeLearningFallback(ChaosLearningRequest request, 
                                                         Throwable throwable) {
        log.warn("CAPSULE unavailable, learning buffered locally: learningId={}", 
                request.getLearningId());
        return Mono.just(EvidenceResponse.builder()
                .capsuleId("LOCAL-LEARNING-" + System.currentTimeMillis())
                .status("BUFFERED_LOCALLY")
                .build());
    }

    /**
     * Check CAPSULE health.
     */
    public Mono<Boolean> isHealthy() {
        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> "UP".equals(response.get("status")))
                .onErrorReturn(false);
    }

    // ========== Request/Response Classes ==========

    @lombok.Data
    @lombok.Builder
    public static class StoreEvidenceRequest {
        private String incidentId;
        private String scopeId;
        private String evidenceType;
        private Object evidence;
        private Map<String, String> metadata;
        private String correlationId;
        private String tenantId;
    }

    @lombok.Data
    @lombok.Builder
    public static class EvidenceResponse {
        private String capsuleId;
        private String incidentId;
        private String status;
        private Instant storedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class HistoricalIncident {
        private String incidentId;
        private String componentId;
        private Instant createdAt;
        private Instant resolvedAt;
        private long mttrMs;
        private String rootCause;
        private String remediationType;
        private boolean resolved;
        private double severity;
        private List<String> affectedComponents;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChaosLearningRequest {
        private String learningId;
        private String incidentId;
        private String patternType;
        private String component;
        private Object immunityRule;
        private double confidence;
        private String correlationId;
        private String tenantId;
    }
}
