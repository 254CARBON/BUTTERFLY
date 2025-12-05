package com.z254.butterfly.aurora.remediation;

import com.z254.butterfly.aurora.config.AuroraProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client for SYNAPSE healing connectors.
 * <p>
 * Provides an abstraction layer for executing remediation actions:
 * <ul>
 *     <li>HTTP-based execution via SYNAPSE REST API</li>
 *     <li>Kafka-based async execution</li>
 *     <li>Local mock execution for testing</li>
 * </ul>
 */
@Slf4j
@Component
public class HealingConnectorClient {

    private final WebClient webClient;
    private final AuroraProperties auroraProperties;

    public HealingConnectorClient(WebClient.Builder webClientBuilder,
                                   AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
        this.webClient = webClientBuilder
                .baseUrl(auroraProperties.getSynapse().getUrl())
                .build();
    }

    /**
     * Execute a healing action via SYNAPSE.
     */
    @CircuitBreaker(name = "synapse-client", fallbackMethod = "executeFallback")
    @Retry(name = "synapse-client")
    public Mono<HealingResult> execute(HealingRequest request) {
        log.info("Executing healing action via SYNAPSE: remediationId={}, action={}, target={}",
                request.getRemediationId(), request.getActionType(), request.getTargetComponent());

        // In DRY_RUN mode, simulate execution
        if (request.getExecutionMode() == AuroraProperties.ExecutionMode.DRY_RUN) {
            return executeDryRun(request);
        }

        // In SANDBOX mode, use mock
        if (request.getExecutionMode() == AuroraProperties.ExecutionMode.SANDBOX) {
            return executeMock(request);
        }

        // Production execution via SYNAPSE
        return executeViaSynapse(request);
    }

    /**
     * Execute a rollback action.
     */
    public Mono<HealingResult> rollback(String remediationId) {
        log.info("Executing rollback for remediation: {}", remediationId);
        
        return webClient.post()
                .uri("/api/v1/actions/{id}/rollback", remediationId)
                .retrieve()
                .bodyToMono(SynapseResponse.class)
                .map(this::toHealingResult)
                .doOnSuccess(result -> log.info("Rollback result for {}: success={}", 
                        remediationId, result.isSuccess()))
                .doOnError(error -> log.error("Rollback failed for {}: {}", 
                        remediationId, error.getMessage()));
    }

    /**
     * Check status of a healing action.
     */
    public Mono<HealingResult> getStatus(String synapseActionId) {
        return webClient.get()
                .uri("/api/v1/actions/{id}", synapseActionId)
                .retrieve()
                .bodyToMono(SynapseResponse.class)
                .map(this::toHealingResult);
    }

    /**
     * Check SYNAPSE health.
     */
    public Mono<Boolean> isHealthy() {
        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> "UP".equals(response.get("status")))
                .onErrorReturn(false);
    }

    // ========== Private Methods ==========

    private Mono<HealingResult> executeViaSynapse(HealingRequest request) {
        SynapseActionRequest synapseRequest = SynapseActionRequest.builder()
                .toolId(buildToolId(request.getActionType()))
                .parameters(buildSynapseParameters(request))
                .executionMode(request.getExecutionMode().name())
                .platoPlanId(request.getPlatoPlanId())
                .platoApprovalId(request.getPlatoApprovalId())
                .correlationId(request.getCorrelationId())
                .idempotencyKey(request.getRemediationId())
                .timeoutMs(request.getTimeoutMs())
                .build();

        return webClient.post()
                .uri("/api/v1/actions")
                .bodyValue(synapseRequest)
                .retrieve()
                .bodyToMono(SynapseResponse.class)
                .map(this::toHealingResult)
                .doOnSuccess(result -> 
                        log.info("SYNAPSE action result: remediationId={}, synapseActionId={}, success={}",
                                request.getRemediationId(), result.getSynapseActionId(), result.isSuccess()))
                .doOnError(error -> 
                        log.error("SYNAPSE action failed: remediationId={}, error={}",
                                request.getRemediationId(), error.getMessage()));
    }

    private Mono<HealingResult> executeDryRun(HealingRequest request) {
        log.info("DRY_RUN: Would execute {} on {}", 
                request.getActionType(), request.getTargetComponent());
        
        return Mono.just(HealingResult.builder()
                .success(true)
                .synapseActionId("DRY-RUN-" + UUID.randomUUID().toString().substring(0, 8))
                .message("Dry run completed successfully")
                .executedAt(Instant.now())
                .dryRun(true)
                .details(Map.of(
                        "action", request.getActionType(),
                        "target", request.getTargetComponent(),
                        "mode", "DRY_RUN"
                ))
                .build());
    }

    private Mono<HealingResult> executeMock(HealingRequest request) {
        log.info("SANDBOX: Mock execution of {} on {}", 
                request.getActionType(), request.getTargetComponent());
        
        // Simulate some execution time
        return Mono.delay(java.time.Duration.ofMillis(500))
                .map(v -> HealingResult.builder()
                        .success(true)
                        .synapseActionId("MOCK-" + UUID.randomUUID().toString().substring(0, 8))
                        .message("Mock execution completed")
                        .executedAt(Instant.now())
                        .dryRun(false)
                        .details(Map.of(
                                "action", request.getActionType(),
                                "target", request.getTargetComponent(),
                                "mode", "SANDBOX"
                        ))
                        .build());
    }

    /**
     * Fallback when SYNAPSE is unavailable.
     */
    public Mono<HealingResult> executeFallback(HealingRequest request, Throwable throwable) {
        log.warn("SYNAPSE unavailable, using fallback for remediation: {}, error: {}",
                request.getRemediationId(), throwable.getMessage());

        // Queue for later execution
        // In production, this would push to a retry queue
        
        return Mono.just(HealingResult.builder()
                .success(false)
                .errorMessage("SYNAPSE unavailable: " + throwable.getMessage())
                .queued(true)
                .executedAt(Instant.now())
                .build());
    }

    private String buildToolId(String actionType) {
        return auroraProperties.getRemediation().getHealingToolsPrefix() + 
                actionType.toLowerCase().replace("_", "-");
    }

    private Map<String, Object> buildSynapseParameters(HealingRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("targetComponent", request.getTargetComponent());
        params.put("incidentId", request.getIncidentId());
        params.put("hypothesisId", request.getHypothesisId());
        params.put("remediationId", request.getRemediationId());
        
        if (request.getParameters() != null) {
            params.putAll(request.getParameters());
        }
        
        return params;
    }

    private HealingResult toHealingResult(SynapseResponse response) {
        return HealingResult.builder()
                .success("COMPLETED".equals(response.getStatus()) || 
                         "SUCCESS".equals(response.getStatus()))
                .synapseActionId(response.getActionId())
                .message(response.getMessage())
                .errorMessage(response.getError())
                .executedAt(Instant.now())
                .originalState(response.getOriginalState())
                .details(response.getDetails())
                .build();
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class HealingRequest {
        private String remediationId;
        private String incidentId;
        private String hypothesisId;
        private String actionType;
        private String targetComponent;
        @Builder.Default
        private Map<String, String> parameters = new HashMap<>();
        private AuroraProperties.ExecutionMode executionMode;
        private String platoPlanId;
        private String platoApprovalId;
        private long timeoutMs;
        private String correlationId;
    }

    @Data
    @Builder
    public static class HealingResult {
        private boolean success;
        private String synapseActionId;
        private String message;
        private String errorMessage;
        private Instant executedAt;
        private boolean dryRun;
        private boolean queued;
        private Map<String, Object> originalState;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    private static class SynapseActionRequest {
        private String toolId;
        private Map<String, Object> parameters;
        private String executionMode;
        private String platoPlanId;
        private String platoApprovalId;
        private String correlationId;
        private String idempotencyKey;
        private long timeoutMs;
    }

    @Data
    private static class SynapseResponse {
        private String actionId;
        private String status;
        private String message;
        private String error;
        private Map<String, Object> originalState;
        private Map<String, Object> details;
    }
}
