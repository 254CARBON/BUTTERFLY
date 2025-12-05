package com.z254.butterfly.cortex.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client interface for PLATO governance service.
 * Provides policy evaluation, approval workflows, and governance artifacts.
 */
public interface PlatoGovernanceClient {

    /**
     * Evaluate a policy against context.
     *
     * @param request the policy evaluation request
     * @return evaluation result
     */
    Mono<PolicyEvaluation> evaluatePolicy(PolicyEvaluationRequest request);

    /**
     * Submit an agent plan for approval.
     *
     * @param request the plan submission request
     * @return submitted plan with approval ID
     */
    Mono<PlanSubmission> submitPlan(PlanSubmissionRequest request);

    /**
     * Get approval status.
     *
     * @param approvalId the approval ID
     * @return current approval status
     */
    Mono<ApprovalStatus> getApprovalStatus(String approvalId);

    /**
     * Wait for approval decision.
     *
     * @param approvalId the approval ID
     * @return approval decision when available
     */
    Mono<ApprovalDecision> waitForApproval(String approvalId);

    /**
     * Submit evidence for a plan.
     *
     * @param planId the plan ID
     * @param evidence the evidence to submit
     * @return completion signal
     */
    Mono<Void> submitEvidence(String planId, Evidence evidence);

    /**
     * Get governance spec.
     *
     * @param specId the specification ID
     * @return the specification
     */
    Mono<GovernanceSpec> getSpec(String specId);

    /**
     * Get governance artifact.
     *
     * @param artifactId the artifact ID
     * @return the artifact
     */
    Mono<GovernanceArtifact> getArtifact(String artifactId);

    /**
     * Check service availability.
     *
     * @return true if available
     */
    Mono<Boolean> isAvailable();

    // Request/Response DTOs

    record PolicyEvaluationRequest(
            String policyId,
            String agentId,
            String taskId,
            String action,
            Map<String, Object> context,
            Map<String, Object> metadata
    ) {}

    record PolicyEvaluation(
            String policyId,
            boolean allowed,
            String reason,
            List<String> violations,
            Map<String, Object> recommendations,
            Instant evaluatedAt
    ) {}

    record PlanSubmissionRequest(
            String agentId,
            String taskId,
            String planType,
            String description,
            List<String> steps,
            List<String> toolsRequired,
            String riskLevel,
            Map<String, Object> context
    ) {}

    record PlanSubmission(
            String planId,
            String approvalId,
            String status,
            String approvalUrl,
            Instant submittedAt
    ) {}

    record ApprovalStatus(
            String approvalId,
            String planId,
            Status status,
            String assignedTo,
            Instant createdAt,
            Instant updatedAt
    ) {
        public enum Status {
            PENDING, IN_REVIEW, APPROVED, REJECTED, EXPIRED, CANCELLED
        }
    }

    record ApprovalDecision(
            String approvalId,
            String planId,
            boolean approved,
            String decidedBy,
            String reason,
            List<String> conditions,
            Instant decidedAt
    ) {}

    record Evidence(
            String type,
            String description,
            Map<String, Object> data,
            List<String> attachments
    ) {}

    record GovernanceSpec(
            String specId,
            String name,
            String version,
            String description,
            Map<String, Object> schema,
            List<String> policies,
            Instant createdAt
    ) {}

    record GovernanceArtifact(
            String artifactId,
            String specId,
            String type,
            String name,
            Map<String, Object> content,
            String status,
            Instant createdAt
    ) {}
}
