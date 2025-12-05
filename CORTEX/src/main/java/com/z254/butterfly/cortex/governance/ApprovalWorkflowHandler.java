package com.z254.butterfly.cortex.governance;

import com.z254.butterfly.cortex.client.PlatoGovernanceClient;
import com.z254.butterfly.cortex.client.PlatoGovernanceClient.ApprovalDecision;
import com.z254.butterfly.cortex.client.PlatoGovernanceClient.ApprovalStatus;
import com.z254.butterfly.cortex.config.CortexProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles approval workflows for agent plans.
 * Supports blocking and non-blocking approval waiting patterns.
 */
@Component
@Slf4j
public class ApprovalWorkflowHandler {

    private final PlatoGovernanceClient platoClient;
    private final CortexProperties cortexProperties;
    
    // Track pending approvals
    private final Map<String, ApprovalWaiter> pendingApprovals = new ConcurrentHashMap<>();

    public ApprovalWorkflowHandler(
            PlatoGovernanceClient platoClient,
            CortexProperties cortexProperties) {
        this.platoClient = platoClient;
        this.cortexProperties = cortexProperties;
    }

    /**
     * Wait for approval (blocking pattern).
     * Returns when approval is granted, denied, or times out.
     */
    public Mono<ApprovalResult> waitForApproval(String approvalId, Duration timeout) {
        log.info("Waiting for approval: {} (timeout: {})", approvalId, timeout);

        return platoClient.waitForApproval(approvalId)
                .timeout(timeout)
                .map(decision -> ApprovalResult.builder()
                        .approvalId(decision.approvalId())
                        .planId(decision.planId())
                        .approved(decision.approved())
                        .decidedBy(decision.decidedBy())
                        .reason(decision.reason())
                        .conditions(decision.conditions())
                        .decidedAt(decision.decidedAt())
                        .build())
                .doOnSuccess(result -> log.info("Approval {} decided: {}", 
                        approvalId, result.isApproved()))
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        return Mono.just(ApprovalResult.builder()
                                .approvalId(approvalId)
                                .approved(false)
                                .reason("Approval timeout")
                                .timedOut(true)
                                .build());
                    }
                    return Mono.just(ApprovalResult.builder()
                            .approvalId(approvalId)
                            .approved(false)
                            .reason("Approval check failed: " + e.getMessage())
                            .build());
                });
    }

    /**
     * Register for approval notification (non-blocking pattern).
     * Executes callback when approval is decided.
     */
    public void registerForApproval(
            String approvalId, 
            Consumer<ApprovalResult> onDecision,
            Duration timeout) {
        
        log.info("Registering for approval notification: {}", approvalId);

        ApprovalWaiter waiter = new ApprovalWaiter(approvalId, onDecision, timeout);
        pendingApprovals.put(approvalId, waiter);

        // Start polling in background
        waitForApproval(approvalId, timeout)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            pendingApprovals.remove(approvalId);
                            onDecision.accept(result);
                        },
                        error -> {
                            pendingApprovals.remove(approvalId);
                            onDecision.accept(ApprovalResult.builder()
                                    .approvalId(approvalId)
                                    .approved(false)
                                    .reason("Error: " + error.getMessage())
                                    .build());
                        }
                );
    }

    /**
     * Check approval status without waiting.
     */
    public Mono<ApprovalStatusResult> checkStatus(String approvalId) {
        return platoClient.getApprovalStatus(approvalId)
                .map(status -> ApprovalStatusResult.builder()
                        .approvalId(status.approvalId())
                        .planId(status.planId())
                        .status(status.status().name())
                        .assignedTo(status.assignedTo())
                        .isPending(status.status() == ApprovalStatus.Status.PENDING ||
                                   status.status() == ApprovalStatus.Status.IN_REVIEW)
                        .isDecided(status.status() == ApprovalStatus.Status.APPROVED ||
                                   status.status() == ApprovalStatus.Status.REJECTED)
                        .updatedAt(status.updatedAt())
                        .build());
    }

    /**
     * Cancel waiting for an approval.
     */
    public void cancelWait(String approvalId) {
        ApprovalWaiter waiter = pendingApprovals.remove(approvalId);
        if (waiter != null) {
            log.info("Cancelled approval wait: {}", approvalId);
        }
    }

    /**
     * Get all pending approvals.
     */
    public List<String> getPendingApprovals() {
        return List.copyOf(pendingApprovals.keySet());
    }

    /**
     * Approval result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalResult {
        private String approvalId;
        private String planId;
        private boolean approved;
        private String decidedBy;
        private String reason;
        private List<String> conditions;
        private Instant decidedAt;
        private boolean timedOut;
    }

    /**
     * Approval status result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalStatusResult {
        private String approvalId;
        private String planId;
        private String status;
        private String assignedTo;
        private boolean isPending;
        private boolean isDecided;
        private Instant updatedAt;
    }

    /**
     * Internal waiter tracking.
     */
    private static class ApprovalWaiter {
        final String approvalId;
        final Consumer<ApprovalResult> callback;
        final Duration timeout;
        final Instant createdAt;

        ApprovalWaiter(String approvalId, Consumer<ApprovalResult> callback, Duration timeout) {
            this.approvalId = approvalId;
            this.callback = callback;
            this.timeout = timeout;
            this.createdAt = Instant.now();
        }
    }
}
