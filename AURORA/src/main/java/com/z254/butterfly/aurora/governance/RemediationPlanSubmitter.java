package com.z254.butterfly.aurora.governance;

import com.z254.butterfly.aurora.client.PlatoClient;
import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.remediation.AutoRemediator;
import com.z254.butterfly.aurora.remediation.RemediationPlaybook;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

/**
 * Submits remediation plans to PLATO for governance approval.
 * <p>
 * Handles:
 * <ul>
 *     <li>Plan conversion to PLATO format</li>
 *     <li>Approval workflow (wait/poll/callback)</li>
 *     <li>Fallback handling when PLATO is unavailable</li>
 * </ul>
 */
@Slf4j
@Component
public class RemediationPlanSubmitter {

    private final PlatoClient platoClient;
    private final BlastRadiusEvaluator blastRadiusEvaluator;
    private final TimeWindowEnforcer timeWindowEnforcer;
    private final AuroraProperties auroraProperties;

    public RemediationPlanSubmitter(PlatoClient platoClient,
                                     BlastRadiusEvaluator blastRadiusEvaluator,
                                     TimeWindowEnforcer timeWindowEnforcer,
                                     AuroraProperties auroraProperties) {
        this.platoClient = platoClient;
        this.blastRadiusEvaluator = blastRadiusEvaluator;
        this.timeWindowEnforcer = timeWindowEnforcer;
        this.auroraProperties = auroraProperties;
    }

    /**
     * Submit a remediation plan for PLATO approval.
     */
    public Mono<AutoRemediator.ApprovalResult> submitForApproval(RcaHypothesis hypothesis,
                                                                   RemediationPlaybook.Playbook playbook,
                                                                   String remediationId) {
        log.info("Submitting remediation plan for approval: remediationId={}, playbook={}",
                remediationId, playbook.getName());

        // Build PLATO plan request
        PlatoPlanRequest planRequest = buildPlanRequest(hypothesis, playbook, remediationId);

        return platoClient.submitPlan(planRequest)
                .flatMap(planResponse -> {
                    if (planResponse.isAutoApproved()) {
                        return Mono.just(AutoRemediator.ApprovalResult.builder()
                                .remediationId(remediationId)
                                .approved(true)
                                .platoPlanId(planResponse.getPlanId())
                                .approvalId(planResponse.getApprovalId())
                                .riskScore(planResponse.getRiskScore())
                                .build());
                    }

                    // Wait for manual approval
                    return waitForApproval(planResponse.getPlanId(), remediationId);
                })
                .onErrorResume(error -> handleApprovalError(remediationId, hypothesis, error));
    }

    /**
     * Request immediate approval (for emergency remediations).
     */
    public Mono<AutoRemediator.ApprovalResult> requestEmergencyApproval(String remediationId,
                                                                          RcaHypothesis hypothesis,
                                                                          String justification) {
        log.warn("Requesting emergency approval for remediation: {}", remediationId);

        return platoClient.requestEmergencyApproval(
                        remediationId,
                        hypothesis.getRootCauseComponent(),
                        justification)
                .map(response -> AutoRemediator.ApprovalResult.builder()
                        .remediationId(remediationId)
                        .approved(response.isApproved())
                        .platoPlanId(response.getPlanId())
                        .approvalId(response.getApprovalId())
                        .riskScore(0.0)
                        .build());
    }

    // ========== Private Methods ==========

    private PlatoPlanRequest buildPlanRequest(RcaHypothesis hypothesis,
                                               RemediationPlaybook.Playbook playbook,
                                               String remediationId) {
        // Evaluate blast radius
        BlastRadiusEvaluator.BlastRadiusAssessment blastAssessment =
                blastRadiusEvaluator.evaluate(hypothesis, playbook);

        // Check time window
        TimeWindowEnforcer.TimeWindowResult timeWindowResult =
                timeWindowEnforcer.checkTimeWindow();

        // Build plan steps
        List<PlatoPlanStep> planSteps = playbook.getSteps().stream()
                .map(step -> PlatoPlanStep.builder()
                        .order(step.getOrder())
                        .action(step.getAction())
                        .description(step.getDescription())
                        .timeout(step.getTimeout().toString())
                        .parameters(step.getParameters())
                        .build())
                .toList();

        return PlatoPlanRequest.builder()
                .planId(remediationId)
                .planType("AURORA_REMEDIATION")
                .title("Auto-remediation: " + playbook.getName())
                .description(buildPlanDescription(hypothesis, playbook))
                .targetComponent(hypothesis.getRootCauseComponent())
                .incidentId(hypothesis.getIncidentId())
                .hypothesisId(hypothesis.getHypothesisId())
                .steps(planSteps)
                .riskScore(playbook.getRiskLevel())
                .blastRadius(blastAssessment.getEstimatedAffectedUsers())
                .impactAssessment(blastAssessment.getImpactSummary())
                .requestedBy("AURORA")
                .requiredApprovers(determineRequiredApprovers(playbook.getRiskLevel()))
                .priority(determinePlanPriority(hypothesis))
                .correlationId(hypothesis.getCorrelationId())
                .tenantId(hypothesis.getTenantId())
                .metadata(Map.of(
                        "playbook", playbook.getName(),
                        "confidence", String.valueOf(hypothesis.getConfidence()),
                        "rootCauseType", hypothesis.getRootCauseType(),
                        "inMaintenanceWindow", String.valueOf(timeWindowResult.isInMaintenanceWindow())
                ))
                .build();
    }

    private String buildPlanDescription(RcaHypothesis hypothesis,
                                         RemediationPlaybook.Playbook playbook) {
        return String.format("""
                Automated remediation plan generated by AURORA.
                
                Root Cause Analysis:
                - Component: %s
                - Type: %s
                - Confidence: %.1f%%
                
                Remediation:
                - Playbook: %s
                - Description: %s
                - Risk Level: %.1f%%
                
                Supporting Evidence:
                %s
                """,
                hypothesis.getRootCauseComponent(),
                hypothesis.getRootCauseType(),
                hypothesis.getConfidence() * 100,
                playbook.getName(),
                playbook.getDescription(),
                playbook.getRiskLevel() * 100,
                String.join("\n- ", hypothesis.getSupportingEvidence()));
    }

    private List<String> determineRequiredApprovers(double riskLevel) {
        if (riskLevel >= 0.7) {
            return List.of("platform-team", "sre-lead");
        } else if (riskLevel >= 0.4) {
            return List.of("platform-team");
        }
        return Collections.emptyList(); // Auto-approve
    }

    private String determinePlanPriority(RcaHypothesis hypothesis) {
        if (hypothesis.getConfidence() > 0.8) {
            return "HIGH";
        } else if (hypothesis.getConfidence() > 0.6) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Mono<AutoRemediator.ApprovalResult> waitForApproval(String planId, 
                                                                  String remediationId) {
        // Poll for approval status (simplified)
        return platoClient.getApprovalStatus(planId)
                .repeatWhenEmpty(10, flux -> flux.delayElements(
                        java.time.Duration.ofSeconds(5)))
                .map(status -> AutoRemediator.ApprovalResult.builder()
                        .remediationId(remediationId)
                        .approved("APPROVED".equals(status.getStatus()))
                        .platoPlanId(planId)
                        .approvalId(status.getApprovalId())
                        .rejectionReason(status.getRejectionReason())
                        .riskScore(status.getRiskScore())
                        .build())
                .timeout(java.time.Duration.ofMinutes(5))
                .onErrorResume(error -> Mono.just(AutoRemediator.ApprovalResult.builder()
                        .remediationId(remediationId)
                        .approved(false)
                        .rejectionReason("Approval timeout: " + error.getMessage())
                        .build()));
    }

    private Mono<AutoRemediator.ApprovalResult> handleApprovalError(String remediationId,
                                                                      RcaHypothesis hypothesis,
                                                                      Throwable error) {
        log.warn("PLATO approval error for {}: {}", remediationId, error.getMessage());

        AuroraProperties.Plato.Fallback fallback = auroraProperties.getPlato().getFallback();

        // Check if we should auto-approve on PLATO unavailability
        if (fallback.isAllowOnUnavailable() && fallback.isAutoApproveLowRisk()) {
            double riskScore = hypothesis.getConfidence() < 0.5 ? 0.7 : 0.3;
            
            if (riskScore <= auroraProperties.getSafety().getAutoApproveRiskThreshold()) {
                log.warn("PLATO unavailable, auto-approving low-risk remediation: {}", 
                        remediationId);
                return Mono.just(AutoRemediator.ApprovalResult.builder()
                        .remediationId(remediationId)
                        .approved(true)
                        .riskScore(riskScore)
                        .build());
            }
        }

        return Mono.just(AutoRemediator.ApprovalResult.builder()
                .remediationId(remediationId)
                .approved(false)
                .rejectionReason("PLATO unavailable: " + error.getMessage())
                .build());
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class PlatoPlanRequest {
        private String planId;
        private String planType;
        private String title;
        private String description;
        private String targetComponent;
        private String incidentId;
        private String hypothesisId;
        private List<PlatoPlanStep> steps;
        private double riskScore;
        private double blastRadius;
        private String impactAssessment;
        private String requestedBy;
        private List<String> requiredApprovers;
        private String priority;
        private String correlationId;
        private String tenantId;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    public static class PlatoPlanStep {
        private int order;
        private String action;
        private String description;
        private String timeout;
        private Map<String, String> parameters;
    }
}
