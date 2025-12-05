package com.z254.butterfly.aurora.remediation;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.governance.RemediationPlanSubmitter;
import com.z254.butterfly.aurora.health.AuroraHealthIndicator;
import com.z254.butterfly.aurora.observability.AuroraMetrics;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Auto-remediation orchestrator.
 * <p>
 * Coordinates the remediation pipeline:
 * <ol>
 *     <li>Safety pre-checks via SafetyGuard</li>
 *     <li>Playbook selection</li>
 *     <li>PLATO governance approval</li>
 *     <li>Execution via SYNAPSE healing connectors</li>
 *     <li>Health monitoring and rollback if needed</li>
 * </ol>
 */
@Slf4j
@Service
public class AutoRemediator {

    private final SafetyGuard safetyGuard;
    private final RemediationPlaybook playbookSelector;
    private final RemediationPlanSubmitter planSubmitter;
    private final HealingConnectorClient healingClient;
    private final RollbackManager rollbackManager;
    private final AuroraProperties auroraProperties;
    private final AuroraMetrics metrics;
    private final AuroraStructuredLogger logger;
    private final AuroraHealthIndicator healthIndicator;

    // Track active remediations
    private final Map<String, ActiveRemediation> activeRemediations = new HashMap<>();

    public AutoRemediator(SafetyGuard safetyGuard,
                          RemediationPlaybook playbookSelector,
                          RemediationPlanSubmitter planSubmitter,
                          HealingConnectorClient healingClient,
                          RollbackManager rollbackManager,
                          AuroraProperties auroraProperties,
                          AuroraMetrics metrics,
                          AuroraStructuredLogger logger,
                          AuroraHealthIndicator healthIndicator) {
        this.safetyGuard = safetyGuard;
        this.playbookSelector = playbookSelector;
        this.planSubmitter = planSubmitter;
        this.healingClient = healingClient;
        this.rollbackManager = rollbackManager;
        this.auroraProperties = auroraProperties;
        this.metrics = metrics;
        this.logger = logger;
        this.healthIndicator = healthIndicator;
    }

    /**
     * Execute remediation for an RCA hypothesis.
     *
     * @param hypothesis the RCA hypothesis
     * @param mode execution mode (PRODUCTION, DRY_RUN, SANDBOX)
     * @return remediation result
     */
    public Mono<RemediationResult> remediate(RcaHypothesis hypothesis, 
                                              AuroraProperties.ExecutionMode mode) {
        String remediationId = generateRemediationId(hypothesis);
        String incidentId = hypothesis.getIncidentId();
        Timer.Sample timerSample = metrics.startRemediationTimer();

        logger.logRemediationEvent(remediationId, incidentId,
                AuroraStructuredLogger.RemediationEventType.STARTED,
                "Starting remediation",
                Map.of(
                        "hypothesisId", hypothesis.getHypothesisId(),
                        "rootCauseComponent", hypothesis.getRootCauseComponent(),
                        "executionMode", mode.name()
                ));

        return performSafetyChecks(hypothesis, remediationId)
                .flatMap(safetyResult -> {
                    if (!safetyResult.isSafe()) {
                        return handleSafetyBlock(remediationId, incidentId, safetyResult, timerSample);
                    }
                    return selectAndExecutePlaybook(hypothesis, mode, remediationId, timerSample);
                })
                .onErrorResume(error -> handleRemediationError(remediationId, incidentId, 
                        error, timerSample));
    }

    /**
     * Execute remediation for an incident.
     */
    public Mono<RemediationResult> remediateIncident(Incident incident, 
                                                      AuroraProperties.ExecutionMode mode) {
        // Get the top hypothesis
        return Mono.justOrEmpty(incident.getHypotheses().stream()
                        .min(Comparator.comparingInt(RcaHypothesis::getRank)))
                .flatMap(hypothesis -> remediate(hypothesis, mode))
                .switchIfEmpty(Mono.error(new RemediationException(
                        "No hypothesis available for incident: " + incident.getId())));
    }

    /**
     * Trigger rollback for a remediation.
     */
    public Mono<RollbackResult> rollback(String remediationId) {
        ActiveRemediation active = activeRemediations.get(remediationId);
        if (active == null) {
            return Mono.error(new RemediationException(
                    "Remediation not found or not active: " + remediationId));
        }

        logger.logRemediationEvent(remediationId, active.getIncidentId(),
                AuroraStructuredLogger.RemediationEventType.ROLLED_BACK,
                "Initiating rollback",
                Map.of("reason", "MANUAL"));

        return rollbackManager.executeRollback(active.getRollbackContext())
                .doOnSuccess(result -> {
                    metrics.recordRemediationRolledBack();
                    activeRemediations.remove(remediationId);
                });
    }

    /**
     * Get status of an active remediation.
     */
    public Optional<ActiveRemediation> getRemediationStatus(String remediationId) {
        return Optional.ofNullable(activeRemediations.get(remediationId));
    }

    // ========== Private Methods ==========

    private Mono<SafetyGuard.SafetyResult> performSafetyChecks(RcaHypothesis hypothesis,
                                                                String remediationId) {
        return safetyGuard.evaluate(hypothesis)
                .doOnNext(result -> log.debug("Safety check result for {}: safe={}, reason={}",
                        remediationId, result.isSafe(), result.getReason()));
    }

    private Mono<RemediationResult> handleSafetyBlock(String remediationId,
                                                       String incidentId,
                                                       SafetyGuard.SafetyResult safetyResult,
                                                       Timer.Sample timerSample) {
        metrics.recordRemediationSafetyBlocked(safetyResult.getReason());
        healthIndicator.decrementActiveRemediations();

        logger.logRemediationEvent(remediationId, incidentId,
                AuroraStructuredLogger.RemediationEventType.SAFETY_BLOCKED,
                "Remediation blocked by safety checks",
                Map.of(
                        "reason", safetyResult.getReason(),
                        "blastRadius", safetyResult.getEstimatedBlastRadius()
                ));

        return Mono.just(RemediationResult.builder()
                .remediationId(remediationId)
                .incidentId(incidentId)
                .status(RemediationStatus.BLOCKED)
                .reason(safetyResult.getReason())
                .safetyResult(safetyResult)
                .timestamp(Instant.now())
                .build());
    }

    private Mono<RemediationResult> selectAndExecutePlaybook(RcaHypothesis hypothesis,
                                                              AuroraProperties.ExecutionMode mode,
                                                              String remediationId,
                                                              Timer.Sample timerSample) {
        return playbookSelector.selectPlaybook(hypothesis)
                .flatMap(playbook -> {
                    log.info("Selected playbook {} for remediation {}", 
                            playbook.getName(), remediationId);
                    
                    return submitForApproval(hypothesis, playbook, remediationId)
                            .flatMap(approval -> {
                                if (!approval.isApproved() && mode == AuroraProperties.ExecutionMode.PRODUCTION) {
                                    return handleApprovalRejection(remediationId, 
                                            hypothesis.getIncidentId(), approval, timerSample);
                                }
                                return executePlaybook(hypothesis, playbook, mode, 
                                        remediationId, approval, timerSample);
                            });
                });
    }

    private Mono<ApprovalResult> submitForApproval(RcaHypothesis hypothesis,
                                                    RemediationPlaybook.Playbook playbook,
                                                    String remediationId) {
        double riskScore = safetyGuard.calculateRiskScore(hypothesis, playbook);
        
        // Auto-approve if below threshold
        if (riskScore < auroraProperties.getSafety().getAutoApproveRiskThreshold()) {
            log.debug("Auto-approving low-risk remediation {}: riskScore={}", 
                    remediationId, riskScore);
            return Mono.just(ApprovalResult.autoApproved(remediationId, riskScore));
        }

        // Check if approval required based on risk
        if (riskScore >= auroraProperties.getSafety().getRequireApprovalAbove()) {
            logger.logRemediationEvent(remediationId, hypothesis.getIncidentId(),
                    AuroraStructuredLogger.RemediationEventType.PENDING_APPROVAL,
                    "Remediation pending PLATO approval",
                    Map.of("riskScore", riskScore));

            return planSubmitter.submitForApproval(hypothesis, playbook, remediationId);
        }

        return Mono.just(ApprovalResult.autoApproved(remediationId, riskScore));
    }

    private Mono<RemediationResult> handleApprovalRejection(String remediationId,
                                                             String incidentId,
                                                             ApprovalResult approval,
                                                             Timer.Sample timerSample) {
        metrics.recordRemediationFailed(timerSample);
        healthIndicator.decrementActiveRemediations();

        logger.logRemediationEvent(remediationId, incidentId,
                AuroraStructuredLogger.RemediationEventType.REJECTED,
                "Remediation rejected by PLATO",
                Map.of("reason", approval.getRejectionReason()));

        return Mono.just(RemediationResult.builder()
                .remediationId(remediationId)
                .incidentId(incidentId)
                .status(RemediationStatus.REJECTED)
                .reason(approval.getRejectionReason())
                .timestamp(Instant.now())
                .build());
    }

    private Mono<RemediationResult> executePlaybook(RcaHypothesis hypothesis,
                                                     RemediationPlaybook.Playbook playbook,
                                                     AuroraProperties.ExecutionMode mode,
                                                     String remediationId,
                                                     ApprovalResult approval,
                                                     Timer.Sample timerSample) {
        String incidentId = hypothesis.getIncidentId();
        
        logger.logRemediationEvent(remediationId, incidentId,
                AuroraStructuredLogger.RemediationEventType.EXECUTING,
                "Executing remediation playbook",
                Map.of(
                        "playbook", playbook.getName(),
                        "executionMode", mode.name(),
                        "platoPlanId", approval.getPlatoPlanId() != null ? 
                                approval.getPlatoPlanId() : "N/A"
                ));

        // Track active remediation
        ActiveRemediation active = ActiveRemediation.builder()
                .remediationId(remediationId)
                .incidentId(incidentId)
                .hypothesisId(hypothesis.getHypothesisId())
                .playbook(playbook)
                .mode(mode)
                .startedAt(Instant.now())
                .status(RemediationStatus.IN_PROGRESS)
                .build();
        activeRemediations.put(remediationId, active);
        healthIndicator.incrementActiveRemediations();

        // Build execution request
        HealingConnectorClient.HealingRequest request = HealingConnectorClient.HealingRequest.builder()
                .remediationId(remediationId)
                .incidentId(incidentId)
                .hypothesisId(hypothesis.getHypothesisId())
                .actionType(playbook.getPrimaryAction())
                .targetComponent(hypothesis.getRootCauseComponent())
                .parameters(playbook.getParameters())
                .executionMode(mode)
                .platoPlanId(approval.getPlatoPlanId())
                .platoApprovalId(approval.getApprovalId())
                .timeoutMs(playbook.getTimeout().toMillis())
                .correlationId(hypothesis.getCorrelationId())
                .build();

        return healingClient.execute(request)
                .flatMap(healingResult -> {
                    if (healingResult.isSuccess()) {
                        return handleSuccessfulRemediation(remediationId, incidentId, 
                                playbook, healingResult, active, timerSample);
                    } else {
                        return handleFailedRemediation(remediationId, incidentId, 
                                healingResult, timerSample);
                    }
                })
                .doFinally(signal -> healthIndicator.decrementActiveRemediations());
    }

    private Mono<RemediationResult> handleSuccessfulRemediation(String remediationId,
                                                                 String incidentId,
                                                                 RemediationPlaybook.Playbook playbook,
                                                                 HealingConnectorClient.HealingResult healingResult,
                                                                 ActiveRemediation active,
                                                                 Timer.Sample timerSample) {
        metrics.recordRemediationCompleted(timerSample, playbook.getPrimaryAction());

        logger.logRemediationEvent(remediationId, incidentId,
                AuroraStructuredLogger.RemediationEventType.COMPLETED,
                "Remediation completed successfully",
                Map.of("synapseActionId", healingResult.getSynapseActionId()));

        // Set up rollback context for monitoring
        active.setStatus(RemediationStatus.MONITORING);
        active.setRollbackContext(RollbackManager.RollbackContext.builder()
                .remediationId(remediationId)
                .playbook(playbook)
                .originalState(healingResult.getOriginalState())
                .healthCheckUrl(playbook.getHealthCheckUrl())
                .build());

        // Start health monitoring for potential rollback
        return rollbackManager.startHealthMonitoring(active.getRollbackContext())
                .map(monitoringResult -> {
                    activeRemediations.remove(remediationId);
                    
                    return RemediationResult.builder()
                            .remediationId(remediationId)
                            .incidentId(incidentId)
                            .status(RemediationStatus.COMPLETED)
                            .synapseActionId(healingResult.getSynapseActionId())
                            .timestamp(Instant.now())
                            .details(healingResult.getDetails())
                            .build();
                });
    }

    private Mono<RemediationResult> handleFailedRemediation(String remediationId,
                                                             String incidentId,
                                                             HealingConnectorClient.HealingResult healingResult,
                                                             Timer.Sample timerSample) {
        metrics.recordRemediationFailed(timerSample);
        activeRemediations.remove(remediationId);

        logger.logRemediationEvent(remediationId, incidentId,
                AuroraStructuredLogger.RemediationEventType.FAILED,
                "Remediation failed",
                Map.of("error", healingResult.getErrorMessage()));

        return Mono.just(RemediationResult.builder()
                .remediationId(remediationId)
                .incidentId(incidentId)
                .status(RemediationStatus.FAILED)
                .reason(healingResult.getErrorMessage())
                .timestamp(Instant.now())
                .build());
    }

    private Mono<RemediationResult> handleRemediationError(String remediationId,
                                                            String incidentId,
                                                            Throwable error,
                                                            Timer.Sample timerSample) {
        metrics.recordRemediationFailed(timerSample);
        activeRemediations.remove(remediationId);

        logger.logRemediationEvent(remediationId, incidentId,
                AuroraStructuredLogger.RemediationEventType.FAILED,
                "Remediation error: " + error.getMessage(),
                Map.of("errorType", error.getClass().getSimpleName()));

        return Mono.just(RemediationResult.builder()
                .remediationId(remediationId)
                .incidentId(incidentId)
                .status(RemediationStatus.FAILED)
                .reason(error.getMessage())
                .timestamp(Instant.now())
                .build());
    }

    private String generateRemediationId(RcaHypothesis hypothesis) {
        return String.format("REM-%s-%d",
                hypothesis.getIncidentId().substring(0, Math.min(8, hypothesis.getIncidentId().length())),
                System.currentTimeMillis() % 100000);
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class RemediationResult {
        private String remediationId;
        private String incidentId;
        private RemediationStatus status;
        private String reason;
        private String synapseActionId;
        private SafetyGuard.SafetyResult safetyResult;
        private Instant timestamp;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    public static class ApprovalResult {
        private String remediationId;
        private boolean approved;
        private String platoPlanId;
        private String approvalId;
        private String rejectionReason;
        private double riskScore;

        public static ApprovalResult autoApproved(String remediationId, double riskScore) {
            return ApprovalResult.builder()
                    .remediationId(remediationId)
                    .approved(true)
                    .riskScore(riskScore)
                    .build();
        }
    }

    @Data
    @Builder
    public static class ActiveRemediation {
        private String remediationId;
        private String incidentId;
        private String hypothesisId;
        private RemediationPlaybook.Playbook playbook;
        private AuroraProperties.ExecutionMode mode;
        private Instant startedAt;
        private RemediationStatus status;
        private RollbackManager.RollbackContext rollbackContext;
    }

    @Data
    @Builder
    public static class RollbackResult {
        private String remediationId;
        private boolean success;
        private String message;
        private Instant timestamp;
    }

    public enum RemediationStatus {
        PENDING,
        IN_PROGRESS,
        MONITORING,
        COMPLETED,
        FAILED,
        BLOCKED,
        REJECTED,
        ROLLED_BACK
    }

    public static class RemediationException extends RuntimeException {
        public RemediationException(String message) {
            super(message);
        }

        public RemediationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
