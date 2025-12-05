package com.z254.butterfly.aurora.resilience;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Fallback chain management for degraded operations.
 * <p>
 * Handles fallbacks when:
 * <ul>
 *     <li>PLATO is unavailable - Use cached policies</li>
 *     <li>SYNAPSE is unavailable - Queue actions for retry</li>
 *     <li>CAPSULE is unavailable - Buffer evidence locally</li>
 * </ul>
 */
@Slf4j
@Component
public class FallbackChains {

    private final AuroraProperties auroraProperties;
    private final LocalRemediationCache localCache;
    private final AuroraStructuredLogger logger;

    // Track fallback activations
    private final Map<String, FallbackStatus> fallbackStatuses = new HashMap<>();

    public FallbackChains(AuroraProperties auroraProperties,
                          LocalRemediationCache localCache,
                          AuroraStructuredLogger logger) {
        this.auroraProperties = auroraProperties;
        this.localCache = localCache;
        this.logger = logger;
        
        initializeFallbackStatuses();
    }

    /**
     * Execute with PLATO fallback chain.
     */
    public <T> Mono<T> withPlatoFallback(Mono<T> primary, 
                                          Supplier<Mono<T>> fallback,
                                          String operationName) {
        return primary
                .doOnError(error -> recordFallbackActivation("PLATO", operationName, error))
                .onErrorResume(error -> {
                    if (shouldUseFallback("PLATO")) {
                        log.warn("PLATO fallback activated for {}: {}", 
                                operationName, error.getMessage());
                        return fallback.get();
                    }
                    return Mono.error(error);
                });
    }

    /**
     * Execute with SYNAPSE fallback chain.
     */
    public <T> Mono<T> withSynapseFallback(Mono<T> primary,
                                            Supplier<Mono<T>> fallback,
                                            String operationName) {
        return primary
                .doOnError(error -> recordFallbackActivation("SYNAPSE", operationName, error))
                .onErrorResume(error -> {
                    if (shouldUseFallback("SYNAPSE")) {
                        log.warn("SYNAPSE fallback activated for {}: {}",
                                operationName, error.getMessage());
                        return fallback.get();
                    }
                    return Mono.error(error);
                });
    }

    /**
     * Execute with CAPSULE fallback chain.
     */
    public <T> Mono<T> withCapsuleFallback(Mono<T> primary,
                                            Supplier<Mono<T>> fallback,
                                            String operationName) {
        return primary
                .doOnError(error -> recordFallbackActivation("CAPSULE", operationName, error))
                .onErrorResume(error -> {
                    if (shouldUseFallback("CAPSULE")) {
                        log.warn("CAPSULE fallback activated for {}: {}",
                                operationName, error.getMessage());
                        return fallback.get();
                    }
                    return Mono.error(error);
                });
    }

    /**
     * Get cached approval decision when PLATO is unavailable.
     */
    public Optional<CachedApproval> getCachedApproval(String component, 
                                                       String actionType, 
                                                       double riskScore) {
        // Auto-approve low risk if configured
        if (auroraProperties.getPlato().getFallback().isAutoApproveLowRisk() &&
                riskScore <= auroraProperties.getSafety().getAutoApproveRiskThreshold()) {
            return Optional.of(CachedApproval.builder()
                    .approved(true)
                    .source("AUTO_APPROVE_LOW_RISK")
                    .timestamp(Instant.now())
                    .build());
        }

        // Check local cache for previous approvals
        return localCache.getCachedApproval(component, actionType);
    }

    /**
     * Queue action for retry when SYNAPSE is unavailable.
     */
    public void queueForRetry(QueuedAction action) {
        localCache.queueAction(action);
        log.info("Queued action for retry: remediationId={}", action.getRemediationId());
    }

    /**
     * Get fallback status for a service.
     */
    public FallbackStatus getFallbackStatus(String service) {
        return fallbackStatuses.get(service);
    }

    /**
     * Get all fallback statuses.
     */
    public Map<String, FallbackStatus> getAllFallbackStatuses() {
        return new HashMap<>(fallbackStatuses);
    }

    /**
     * Reset fallback status (when service recovers).
     */
    public void resetFallbackStatus(String service) {
        FallbackStatus status = fallbackStatuses.get(service);
        if (status != null) {
            status.setActive(false);
            status.setActivationCount(0);
            status.setLastDeactivatedAt(Instant.now());
            log.info("Fallback reset for service: {}", service);
        }
    }

    // ========== Private Methods ==========

    private void initializeFallbackStatuses() {
        fallbackStatuses.put("PLATO", FallbackStatus.inactive("PLATO"));
        fallbackStatuses.put("SYNAPSE", FallbackStatus.inactive("SYNAPSE"));
        fallbackStatuses.put("CAPSULE", FallbackStatus.inactive("CAPSULE"));
    }

    private boolean shouldUseFallback(String service) {
        switch (service) {
            case "PLATO":
                return auroraProperties.getPlato().getFallback().isAllowOnUnavailable();
            case "SYNAPSE":
                return true; // Always allow queuing
            case "CAPSULE":
                return true; // Always allow local buffering
            default:
                return false;
        }
    }

    private void recordFallbackActivation(String service, String operation, Throwable error) {
        FallbackStatus status = fallbackStatuses.get(service);
        if (status != null) {
            status.setActive(true);
            status.setActivationCount(status.getActivationCount() + 1);
            status.setLastActivatedAt(Instant.now());
            status.setLastError(error.getMessage());
        }

        if (auroraProperties.getPlato().getFallback().isLogFallbacks()) {
            log.warn("Fallback activated: service={}, operation={}, error={}",
                    service, operation, error.getMessage());
        }
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class FallbackStatus {
        private String service;
        private boolean active;
        private int activationCount;
        private Instant lastActivatedAt;
        private Instant lastDeactivatedAt;
        private String lastError;

        public static FallbackStatus inactive(String service) {
            return FallbackStatus.builder()
                    .service(service)
                    .active(false)
                    .activationCount(0)
                    .build();
        }
    }

    @Data
    @Builder
    public static class CachedApproval {
        private boolean approved;
        private String source;
        private String platoPlanId;
        private Instant timestamp;
        private Instant expiresAt;
    }

    @Data
    @Builder
    public static class QueuedAction {
        private String remediationId;
        private String incidentId;
        private String actionType;
        private String targetComponent;
        private Map<String, String> parameters;
        private Instant queuedAt;
        private int retryCount;
    }
}
