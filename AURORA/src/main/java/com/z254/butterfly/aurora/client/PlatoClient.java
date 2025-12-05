package com.z254.butterfly.aurora.client;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.governance.RemediationPlanSubmitter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Client for PLATO governance service.
 * <p>
 * Provides methods for:
 * <ul>
 *     <li>Plan submission and approval workflow</li>
 *     <li>Policy evaluation</li>
 *     <li>Emergency approval requests</li>
 * </ul>
 */
@Slf4j
@Component
public class PlatoClient {

    private final WebClient webClient;
    private final AuroraProperties auroraProperties;

    public PlatoClient(WebClient.Builder webClientBuilder,
                       AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
        this.webClient = webClientBuilder
                .baseUrl(auroraProperties.getPlato().getUrl())
                .build();
    }

    /**
     * Submit a remediation plan for approval.
     */
    @CircuitBreaker(name = "plato-client", fallbackMethod = "submitPlanFallback")
    @Retry(name = "plato-client")
    public Mono<PlanSubmissionResponse> submitPlan(RemediationPlanSubmitter.PlatoPlanRequest request) {
        log.info("Submitting remediation plan to PLATO: planId={}", request.getPlanId());

        return webClient.post()
                .uri("/api/v1/plans")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PlanSubmissionResponse.class)
                .doOnSuccess(response -> log.info("Plan submitted: planId={}, status={}",
                        request.getPlanId(), response.getStatus()))
                .doOnError(error -> log.error("Failed to submit plan: planId={}, error={}",
                        request.getPlanId(), error.getMessage()));
    }

    /**
     * Fallback when PLATO is unavailable.
     */
    public Mono<PlanSubmissionResponse> submitPlanFallback(
            RemediationPlanSubmitter.PlatoPlanRequest request, Throwable throwable) {
        log.warn("PLATO unavailable, using fallback for plan: {}", request.getPlanId());

        AuroraProperties.Plato.Fallback fallback = auroraProperties.getPlato().getFallback();

        if (fallback.isAllowOnUnavailable() && fallback.isAutoApproveLowRisk()) {
            if (request.getRiskScore() <= auroraProperties.getSafety().getAutoApproveRiskThreshold()) {
                log.warn("Auto-approving low-risk plan due to PLATO unavailability: {}",
                        request.getPlanId());
                return Mono.just(PlanSubmissionResponse.builder()
                        .planId(request.getPlanId())
                        .status("AUTO_APPROVED")
                        .autoApproved(true)
                        .riskScore(request.getRiskScore())
                        .message("Auto-approved due to PLATO unavailability")
                        .build());
            }
        }

        return Mono.error(new PlatoClientException("PLATO unavailable: " + throwable.getMessage()));
    }

    /**
     * Get approval status for a plan.
     */
    @CircuitBreaker(name = "plato-client", fallbackMethod = "getApprovalStatusFallback")
    public Mono<ApprovalStatus> getApprovalStatus(String planId) {
        return webClient.get()
                .uri("/api/v1/plans/{planId}/approval", planId)
                .retrieve()
                .bodyToMono(ApprovalStatus.class);
    }

    /**
     * Fallback for approval status.
     */
    public Mono<ApprovalStatus> getApprovalStatusFallback(String planId, Throwable throwable) {
        log.warn("PLATO unavailable, returning pending status for plan: {}", planId);
        return Mono.just(ApprovalStatus.builder()
                .planId(planId)
                .status("PENDING")
                .build());
    }

    /**
     * Request emergency approval.
     */
    @CircuitBreaker(name = "plato-client", fallbackMethod = "requestEmergencyApprovalFallback")
    public Mono<EmergencyApprovalResponse> requestEmergencyApproval(String remediationId,
                                                                      String component,
                                                                      String justification) {
        log.warn("Requesting emergency approval: remediationId={}", remediationId);

        EmergencyApprovalRequest request = EmergencyApprovalRequest.builder()
                .remediationId(remediationId)
                .component(component)
                .justification(justification)
                .requestedAt(Instant.now())
                .build();

        return webClient.post()
                .uri("/api/v1/approvals/emergency")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmergencyApprovalResponse.class);
    }

    /**
     * Fallback for emergency approval.
     */
    public Mono<EmergencyApprovalResponse> requestEmergencyApprovalFallback(
            String remediationId, String component, String justification, Throwable throwable) {
        log.error("PLATO unavailable for emergency approval: {}", remediationId);
        return Mono.just(EmergencyApprovalResponse.builder()
                .approved(false)
                .reason("PLATO unavailable for emergency approval")
                .build());
    }

    /**
     * Evaluate policy for an action.
     */
    @CircuitBreaker(name = "plato-client", fallbackMethod = "evaluatePolicyFallback")
    public Mono<PolicyEvaluationResponse> evaluatePolicy(String policyId,
                                                          Map<String, Object> context) {
        return webClient.post()
                .uri("/api/v1/policies/{policyId}/evaluate", policyId)
                .bodyValue(context)
                .retrieve()
                .bodyToMono(PolicyEvaluationResponse.class);
    }

    /**
     * Fallback for policy evaluation.
     */
    public Mono<PolicyEvaluationResponse> evaluatePolicyFallback(String policyId,
                                                                   Map<String, Object> context,
                                                                   Throwable throwable) {
        log.warn("PLATO unavailable for policy evaluation, using cached/default policy");
        return Mono.just(PolicyEvaluationResponse.builder()
                .policyId(policyId)
                .allowed(auroraProperties.getPlato().getFallback().isUseCachedPolicies())
                .reason("Fallback: using default policy")
                .build());
    }

    /**
     * Check PLATO health.
     */
    public Mono<Boolean> isHealthy() {
        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> "UP".equals(response.get("status")))
                .onErrorReturn(false);
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class PlanSubmissionResponse {
        private String planId;
        private String status;
        private boolean autoApproved;
        private String approvalId;
        private double riskScore;
        private String message;
    }

    @Data
    @Builder
    public static class ApprovalStatus {
        private String planId;
        private String status;
        private String approvalId;
        private String approvedBy;
        private Instant approvedAt;
        private String rejectionReason;
        private double riskScore;
    }

    @Data
    @Builder
    public static class EmergencyApprovalRequest {
        private String remediationId;
        private String component;
        private String justification;
        private Instant requestedAt;
    }

    @Data
    @Builder
    public static class EmergencyApprovalResponse {
        private boolean approved;
        private String planId;
        private String approvalId;
        private String reason;
    }

    @Data
    @Builder
    public static class PolicyEvaluationResponse {
        private String policyId;
        private boolean allowed;
        private String reason;
        private Map<String, Object> constraints;
    }

    public static class PlatoClientException extends RuntimeException {
        public PlatoClientException(String message) {
            super(message);
        }
    }
}
