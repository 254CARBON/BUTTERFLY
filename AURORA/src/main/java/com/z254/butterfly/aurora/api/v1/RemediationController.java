package com.z254.butterfly.aurora.api.v1;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.domain.service.IncidentService;
import com.z254.butterfly.aurora.remediation.AutoRemediator;
import com.z254.butterfly.aurora.remediation.RemediationPlaybook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API controller for remediation operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/remediation")
@Tag(name = "Remediation", description = "Remediation execution and management")
public class RemediationController {

    private final AutoRemediator autoRemediator;
    private final RemediationPlaybook playbookService;
    private final AuroraProperties auroraProperties;
    private final IncidentService incidentService;

    // In-memory storage for remediation results
    private final Map<String, AutoRemediator.RemediationResult> remediations = 
            new ConcurrentHashMap<>();

    public RemediationController(AutoRemediator autoRemediator,
                                  RemediationPlaybook playbookService,
                                  AuroraProperties auroraProperties,
                                  IncidentService incidentService) {
        this.autoRemediator = autoRemediator;
        this.playbookService = playbookService;
        this.auroraProperties = auroraProperties;
        this.incidentService = incidentService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute remediation", description = "Execute a remediation manually")
    public Mono<ResponseEntity<AutoRemediator.RemediationResult>> executeRemediation(
            @RequestBody ExecuteRequest request) {

        log.info("Manual remediation requested: incidentId={}, actionType={}",
                request.getIncidentId(), request.getActionType());

        if (incidentService.getIncident(request.getIncidentId()).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        // Build hypothesis from request
        RcaHypothesis hypothesis = RcaHypothesis.builder()
                .hypothesisId(request.getHypothesisId())
                .incidentId(request.getIncidentId())
                .rootCauseComponent(request.getTargetComponent())
                .rootCauseType(request.getRootCauseType())
                .confidence(request.getConfidence() != null ? request.getConfidence() : 0.8)
                .suggestedRemediations(List.of(
                        RcaHypothesis.SuggestedRemediation.builder()
                                .actionType(request.getActionType())
                                .targetComponent(request.getTargetComponent())
                                .parameters(request.getParameters())
                                .priority(1)
                                .build()
                ))
                .correlationId(request.getCorrelationId())
                .build();

        AuroraProperties.ExecutionMode mode = request.getExecutionMode() != null ?
                AuroraProperties.ExecutionMode.valueOf(request.getExecutionMode()) :
                (auroraProperties.getSafety().isDryRunDefault() ?
                        AuroraProperties.ExecutionMode.DRY_RUN :
                        AuroraProperties.ExecutionMode.PRODUCTION);

        return autoRemediator.remediate(hypothesis, mode)
                .map(result -> {
                    remediations.put(result.getRemediationId(), result);
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(error -> {
                    log.error("Remediation failed: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(AutoRemediator.RemediationResult.builder()
                                    .incidentId(request.getIncidentId())
                                    .status(AutoRemediator.RemediationStatus.FAILED)
                                    .reason(error.getMessage())
                                    .timestamp(Instant.now())
                                    .build()));
                });
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get remediation status", description = "Get status of a remediation")
    public Mono<ResponseEntity<RemediationStatus>> getRemediationStatus(
            @Parameter(description = "Remediation ID") @PathVariable String id) {

        // Check active remediations first
        Optional<AutoRemediator.ActiveRemediation> active = 
                autoRemediator.getRemediationStatus(id);

        if (active.isPresent()) {
            return Mono.just(ResponseEntity.ok(RemediationStatus.builder()
                    .remediationId(id)
                    .status(active.get().getStatus().name())
                    .incidentId(active.get().getIncidentId())
                    .playbook(active.get().getPlaybook().getName())
                    .startedAt(active.get().getStartedAt())
                    .active(true)
                    .build()));
        }

        // Check completed remediations
        AutoRemediator.RemediationResult result = remediations.get(id);
        if (result != null) {
            return Mono.just(ResponseEntity.ok(RemediationStatus.builder()
                    .remediationId(id)
                    .status(result.getStatus().name())
                    .incidentId(result.getIncidentId())
                    .reason(result.getReason())
                    .synapseActionId(result.getSynapseActionId())
                    .completedAt(result.getTimestamp())
                    .active(false)
                    .build()));
        }

        return Mono.just(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "Trigger rollback", description = "Trigger rollback for a remediation")
    public Mono<ResponseEntity<AutoRemediator.RollbackResult>> triggerRollback(
            @Parameter(description = "Remediation ID") @PathVariable String id) {

        log.info("Rollback requested for remediation: {}", id);

        return autoRemediator.rollback(id)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Rollback failed: {}", error.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(AutoRemediator.RollbackResult.builder()
                                    .remediationId(id)
                                    .success(false)
                                    .message(error.getMessage())
                                    .timestamp(Instant.now())
                                    .build()));
                });
    }

    @GetMapping("/playbooks")
    @Operation(summary = "List playbooks", description = "List available remediation playbooks")
    public Mono<ResponseEntity<List<PlaybookSummary>>> listPlaybooks() {
        List<PlaybookSummary> summaries = playbookService.listPlaybooks().stream()
                .map(p -> PlaybookSummary.builder()
                        .name(p.getName())
                        .primaryAction(p.getPrimaryAction())
                        .description(p.getDescription())
                        .riskLevel(p.getRiskLevel())
                        .timeout(p.getTimeout().toString())
                        .rollbackSupported(p.isRollbackSupported())
                        .applicableRootCauses(p.getApplicableRootCauses())
                        .build())
                .toList();

        return Mono.just(ResponseEntity.ok(summaries));
    }

    @GetMapping("/playbooks/{actionType}")
    @Operation(summary = "Get playbook", description = "Get playbook details by action type")
    public Mono<ResponseEntity<RemediationPlaybook.Playbook>> getPlaybook(
            @Parameter(description = "Action type") @PathVariable String actionType) {

        return Mono.justOrEmpty(playbookService.getPlaybook(actionType))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/config")
    @Operation(summary = "Get remediation config", 
               description = "Get current remediation configuration")
    public Mono<ResponseEntity<RemediationConfig>> getConfig() {
        AuroraProperties.Safety safety = auroraProperties.getSafety();
        AuroraProperties.Remediation remediation = auroraProperties.getRemediation();

        return Mono.just(ResponseEntity.ok(RemediationConfig.builder()
                .maxConcurrentRemediations(safety.getMaxConcurrentRemediations())
                .defaultBlastRadiusLimit(safety.getDefaultBlastRadiusLimit())
                .dryRunDefault(safety.isDryRunDefault())
                .autoApproveRiskThreshold(safety.getAutoApproveRiskThreshold())
                .requireApprovalAbove(safety.getRequireApprovalAbove())
                .rollbackHealthWindow(safety.getRollbackHealthWindow().toString())
                .defaultTimeout(remediation.getDefaultTimeout().toString())
                .maxRetryAttempts(remediation.getMaxRetryAttempts())
                .build()));
    }

    // ========== Request/Response DTOs ==========

    @lombok.Data
    public static class ExecuteRequest {
        private String incidentId;
        private String hypothesisId;
        private String targetComponent;
        private String rootCauseType;
        private String actionType;
        private Double confidence;
        private String executionMode;
        private Map<String, String> parameters;
        private String correlationId;
    }

    @lombok.Data
    @lombok.Builder
    public static class RemediationStatus {
        private String remediationId;
        private String status;
        private String incidentId;
        private String playbook;
        private String reason;
        private String synapseActionId;
        private Instant startedAt;
        private Instant completedAt;
        private boolean active;
    }

    @lombok.Data
    @lombok.Builder
    public static class PlaybookSummary {
        private String name;
        private String primaryAction;
        private String description;
        private double riskLevel;
        private String timeout;
        private boolean rollbackSupported;
        private Set<String> applicableRootCauses;
    }

    @lombok.Data
    @lombok.Builder
    public static class RemediationConfig {
        private int maxConcurrentRemediations;
        private int defaultBlastRadiusLimit;
        private boolean dryRunDefault;
        private double autoApproveRiskThreshold;
        private double requireApprovalAbove;
        private String rollbackHealthWindow;
        private String defaultTimeout;
        private int maxRetryAttempts;
    }
}
