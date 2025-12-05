package com.z254.butterfly.aurora.api.v1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chaos test endpoints for E2E testing.
 * <p>
 * These endpoints allow injection of failures and degradation
 * for testing AURORA's resilience capabilities.
 */
@Slf4j
@RestController
@RequestMapping("/chaos")
public class ChaosTestController {

    // Chaos state flags
    private final AtomicBoolean auroraDegraded = new AtomicBoolean(false);
    private final AtomicBoolean rcaTimeout = new AtomicBoolean(false);
    private final AtomicBoolean remediationFailure = new AtomicBoolean(false);
    private final AtomicLong degradationDelayMs = new AtomicLong(0);
    private final AtomicLong rcaTimeoutMs = new AtomicLong(30000);

    /**
     * Simulate AURORA service degradation (slowdown).
     */
    @PostMapping("/aurora-degraded")
    public Mono<ResponseEntity<ChaosResponse>> enableDegradation(
            @RequestBody(required = false) DegradationRequest request) {

        long delayMs = request != null && request.getDelayMs() != null ? 
                request.getDelayMs() : 5000;
        
        auroraDegraded.set(true);
        degradationDelayMs.set(delayMs);
        
        log.warn("CHAOS: AURORA degradation enabled with {}ms delay", delayMs);
        
        return Mono.just(ResponseEntity.ok(ChaosResponse.builder()
                .status("ENABLED")
                .chaosType("AURORA_DEGRADED")
                .config(Map.of("delayMs", String.valueOf(delayMs)))
                .enabledAt(Instant.now())
                .build()));
    }

    @DeleteMapping("/aurora-degraded")
    public Mono<ResponseEntity<ChaosResponse>> disableDegradation() {
        auroraDegraded.set(false);
        degradationDelayMs.set(0);
        
        log.info("CHAOS: AURORA degradation disabled");
        
        return Mono.just(ResponseEntity.ok(ChaosResponse.builder()
                .status("DISABLED")
                .chaosType("AURORA_DEGRADED")
                .disabledAt(Instant.now())
                .build()));
    }

    /**
     * Simulate RCA timeouts.
     */
    @PostMapping("/rca-timeout")
    public Mono<ResponseEntity<ChaosResponse>> enableRcaTimeout(
            @RequestBody(required = false) TimeoutRequest request) {

        long timeoutMs = request != null && request.getTimeoutMs() != null ?
                request.getTimeoutMs() : 30000;
        
        rcaTimeout.set(true);
        rcaTimeoutMs.set(timeoutMs);
        
        log.warn("CHAOS: RCA timeout enabled with {}ms", timeoutMs);
        
        return Mono.just(ResponseEntity.ok(ChaosResponse.builder()
                .status("ENABLED")
                .chaosType("RCA_TIMEOUT")
                .config(Map.of("timeoutMs", String.valueOf(timeoutMs)))
                .enabledAt(Instant.now())
                .build()));
    }

    @DeleteMapping("/rca-timeout")
    public Mono<ResponseEntity<ChaosResponse>> disableRcaTimeout() {
        rcaTimeout.set(false);
        
        log.info("CHAOS: RCA timeout disabled");
        
        return Mono.just(ResponseEntity.ok(ChaosResponse.builder()
                .status("DISABLED")
                .chaosType("RCA_TIMEOUT")
                .disabledAt(Instant.now())
                .build()));
    }

    /**
     * Simulate remediation failures.
     */
    @PostMapping("/remediation-failure")
    public Mono<ResponseEntity<ChaosResponse>> enableRemediationFailure(
            @RequestBody(required = false) FailureRequest request) {

        remediationFailure.set(true);
        
        log.warn("CHAOS: Remediation failure enabled");
        
        return Mono.just(ResponseEntity.ok(ChaosResponse.builder()
                .status("ENABLED")
                .chaosType("REMEDIATION_FAILURE")
                .enabledAt(Instant.now())
                .build()));
    }

    @DeleteMapping("/remediation-failure")
    public Mono<ResponseEntity<ChaosResponse>> disableRemediationFailure() {
        remediationFailure.set(false);
        
        log.info("CHAOS: Remediation failure disabled");
        
        return Mono.just(ResponseEntity.ok(ChaosResponse.builder()
                .status("DISABLED")
                .chaosType("REMEDIATION_FAILURE")
                .disabledAt(Instant.now())
                .build()));
    }

    /**
     * Get current chaos status.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<ChaosStatus>> getChaosStatus() {
        return Mono.just(ResponseEntity.ok(ChaosStatus.builder()
                .auroraDegraded(auroraDegraded.get())
                .degradationDelayMs(degradationDelayMs.get())
                .rcaTimeout(rcaTimeout.get())
                .rcaTimeoutMs(rcaTimeoutMs.get())
                .remediationFailure(remediationFailure.get())
                .build()));
    }

    /**
     * Reset all chaos scenarios.
     */
    @PostMapping("/reset")
    public Mono<ResponseEntity<ChaosResponse>> resetAll() {
        auroraDegraded.set(false);
        degradationDelayMs.set(0);
        rcaTimeout.set(false);
        remediationFailure.set(false);
        
        log.info("CHAOS: All chaos scenarios reset");
        
        return Mono.just(ResponseEntity.ok(ChaosResponse.builder()
                .status("RESET")
                .chaosType("ALL")
                .disabledAt(Instant.now())
                .build()));
    }

    // ========== Chaos Check Methods (called by other components) ==========

    public boolean isAuroraDegraded() {
        return auroraDegraded.get();
    }

    public Mono<Void> applyDegradation() {
        if (auroraDegraded.get() && degradationDelayMs.get() > 0) {
            return Mono.delay(Duration.ofMillis(degradationDelayMs.get())).then();
        }
        return Mono.empty();
    }

    public boolean isRcaTimeoutEnabled() {
        return rcaTimeout.get();
    }

    public long getRcaTimeoutMs() {
        return rcaTimeoutMs.get();
    }

    public boolean isRemediationFailureEnabled() {
        return remediationFailure.get();
    }

    // ========== Request/Response DTOs ==========

    @lombok.Data
    public static class DegradationRequest {
        private Long delayMs;
    }

    @lombok.Data
    public static class TimeoutRequest {
        private Long timeoutMs;
    }

    @lombok.Data
    public static class FailureRequest {
        private Double failureRate;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChaosResponse {
        private String status;
        private String chaosType;
        private Map<String, String> config;
        private Instant enabledAt;
        private Instant disabledAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChaosStatus {
        private boolean auroraDegraded;
        private long degradationDelayMs;
        private boolean rcaTimeout;
        private long rcaTimeoutMs;
        private boolean remediationFailure;
    }
}
