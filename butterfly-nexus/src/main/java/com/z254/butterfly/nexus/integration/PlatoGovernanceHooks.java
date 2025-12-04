package com.z254.butterfly.nexus.integration;

import com.z254.butterfly.nexus.domain.evolution.LearningSignal;
import com.z254.butterfly.nexus.domain.synthesis.StrategicOption;
import com.z254.butterfly.nexus.evolution.IntegrationHealthService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PLATO Governance Hooks for NEXUS integration.
 * 
 * <p>Implements governance integration:
 * <ul>
 *   <li>Strategic options validated against PLATO Specs</li>
 *   <li>Violations detected and flagged</li>
 *   <li>Validation results feed back to tune PERCEPTION pipelines</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatoGovernanceHooks {

    private final IntegrationHealthService healthService;
    private final MeterRegistry meterRegistry;
    private final WebClient.Builder webClientBuilder;

    @Value("${nexus.clients.plato.url:http://localhost:8085}")
    private String platoUrl;

    /**
     * Listen for PLATO validation events and propagate learning signals.
     */
    @KafkaListener(topics = "plato.events", groupId = "nexus-plato-hooks")
    public void onPlatoEvent(Map<String, Object> event) {
        String eventType = (String) event.get("event_type");
        
        log.debug("Processing PLATO event: {}", eventType);
        meterRegistry.counter("nexus.integration.plato.event", "type", eventType).increment();

        switch (eventType) {
            case "PROOF_VERIFIED" -> handleProofVerified(event);
            case "GOVERNANCE_VIOLATION" -> handleGovernanceViolation(event);
            case "SPEC_STATUS_CHANGED" -> handleSpecStatusChanged(event);
        }
    }

    /**
     * Validate a strategic option against PLATO governance.
     *
     * @param option The strategic option to validate
     * @return The option with updated PLATO contribution
     */
    public Mono<StrategicOption> validateOption(StrategicOption option) {
        log.debug("Validating strategic option {} against PLATO", option.optionId());

        return getWebClient()
            .post()
            .uri("/api/v1/validation/option")
            .bodyValue(Map.of(
                "optionId", option.optionId(),
                "description", option.description(),
                "affectedEntities", option.affectedEntities()
            ))
            .retrieve()
            .bodyToMono(ValidationResponse.class)
            .map(response -> updateOptionWithValidation(option, response))
            .doOnSuccess(updated -> {
                meterRegistry.counter("nexus.integration.plato.validation",
                    "status", updated.platoContribution().status().name()).increment();
            })
            .onErrorResume(e -> {
                log.warn("PLATO validation failed: {}", e.getMessage());
                return Mono.just(option); // Return unchanged on error
            });
    }

    // ============== Pluralism Support (Phase 6) ==============

    /**
     * Validate a strategic option using PLATO's multi-school governance evaluation.
     * Part of Phase 6 Cognitive Pluralism at Ecosystem Scale.
     *
     * <p>This method queries PLATO's pluralism API to get governance evaluations
     * from multiple schools and surfaces any disagreements for synthesis flows.
     *
     * @param option The strategic option to validate
     * @return Multi-school validation result with per-school outcomes and disagreements
     */
    public Mono<MultiSchoolValidationResult> validateOptionWithPluralism(StrategicOption option) {
        log.debug("Validating strategic option {} with pluralism", option.optionId());

        return checkPluralismStatus()
            .flatMap(status -> {
                if (!status.fullyOperational()) {
                    log.warn("Pluralism not operational, falling back to standard validation");
                    return fallbackToStandardValidation(option);
                }

                return getWebClient()
                    .post()
                    .uri("/api/v1/governance/pluralism/evaluate-object")
                    .bodyValue(Map.of(
                        "objectId", option.optionId(),
                        "objectType", "STRATEGIC_OPTION",
                        "namespace", "nexus",
                        "actorId", "nexus-synthesis",
                        "environment", "production",
                        "attributes", Map.of(
                            "description", option.description(),
                            "affectedEntities", option.affectedEntities()
                        )
                    ))
                    .retrieve()
                    .bodyToMono(PluralismEvaluationResponse.class)
                    .map(response -> mapToMultiSchoolResult(option.optionId(), response))
                    .doOnSuccess(result -> {
                        meterRegistry.counter("nexus.integration.plato.pluralism_validation",
                            "consensus", result.consensusOutcome()).increment();
                        if (result.hasSignificantDivergence()) {
                            meterRegistry.counter("nexus.integration.plato.pluralism_divergence").increment();
                        }
                    });
            })
            .onErrorResume(e -> {
                log.warn("PLATO pluralism validation failed: {}", e.getMessage());
                return fallbackToStandardValidation(option);
            });
    }

    /**
     * Get school disagreements for a previously evaluated option.
     *
     * @param optionId The option ID
     * @return List of school disagreements
     */
    public Mono<List<SchoolDisagreement>> getSchoolDisagreements(String optionId) {
        log.debug("Getting school disagreements for option {}", optionId);

        return getWebClient()
            .get()
            .uri("/api/v1/governance/pluralism/divergences")
            .retrieve()
            .bodyToFlux(DivergenceResponse.class)
            .filter(d -> d.policyId() != null && d.policyId().contains(optionId))
            .map(this::mapToDisagreement)
            .collectList()
            .onErrorReturn(List.of());
    }

    /**
     * Check the pluralism feature status in PLATO.
     *
     * @return Pluralism status
     */
    public Mono<PluralismStatus> checkPluralismStatus() {
        return getWebClient()
            .get()
            .uri("/api/v1/governance/pluralism/status")
            .retrieve()
            .bodyToMono(PluralismStatus.class)
            .onErrorReturn(new PluralismStatus(false, false, 0, 0.0, 0.0));
    }

    /**
     * Get active governance schools from PLATO.
     *
     * @return List of active schools
     */
    public Mono<List<GovernanceSchoolSummary>> getActiveGovernanceSchools() {
        return getWebClient()
            .get()
            .uri("/api/v1/governance/pluralism/schools")
            .retrieve()
            .bodyToFlux(GovernanceSchoolSummary.class)
            .collectList()
            .onErrorReturn(List.of());
    }

    // --- Pluralism Helper Methods ---

    private Mono<MultiSchoolValidationResult> fallbackToStandardValidation(StrategicOption option) {
        return validateOption(option)
            .map(validated -> new MultiSchoolValidationResult(
                option.optionId(),
                false, // pluralismUsed
                validated.platoContribution().status().name().equals("COMPLIANT"),
                1.0, // consensusScore (100% since only one "school")
                0.0, // divergenceScore
                "STANDARD_VALIDATION",
                List.of(),
                List.of(),
                "Pluralism not available, used standard validation"
            ));
    }

    private MultiSchoolValidationResult mapToMultiSchoolResult(String optionId, PluralismEvaluationResponse response) {
        List<SchoolEvaluationResult> schoolResults = response.schoolEvaluations() != null
            ? response.schoolEvaluations().stream()
                .map(se -> new SchoolEvaluationResult(
                    se.schoolCode(),
                    se.schoolName(),
                    se.passed(),
                    se.score(),
                    se.confidence()
                ))
                .toList()
            : List.of();

        List<SchoolDisagreement> disagreements = response.pluralismSummary() != null 
            && response.pluralismSummary().divergencePoints() != null
            ? response.pluralismSummary().divergencePoints().stream()
                .map(dp -> new SchoolDisagreement(
                    dp.category(),
                    dp.disagreeingSchools(),
                    dp.description(),
                    dp.divergenceMagnitude()
                ))
                .toList()
            : List.of();

        return new MultiSchoolValidationResult(
            optionId,
            true, // pluralismUsed
            response.passed(),
            response.pluralismSummary() != null ? response.pluralismSummary().consensusScore() : 0.0,
            response.pluralismSummary() != null ? response.pluralismSummary().divergenceScore() : 0.0,
            response.pluralismSummary() != null ? response.pluralismSummary().consensusOutcome() : "UNKNOWN",
            schoolResults,
            disagreements,
            response.pluralismSummary() != null ? response.pluralismSummary().consensusRationale() : null
        );
    }

    private SchoolDisagreement mapToDisagreement(DivergenceResponse response) {
        return new SchoolDisagreement(
            response.divergenceCategory(),
            response.involvedSchools(),
            response.description(),
            response.divergenceMagnitude()
        );
    }

    // ============== Policy Constraint Validation ==============

    /**
     * Validate a strategic option against applicable PLATO meta-spec policies.
     * <p>
     * This method checks the option against all policies that apply to the
     * affected entities and contexts, returning detailed constraint information.
     *
     * @param option The strategic option to validate
     * @param contextId The context (world/scope) for policy lookup
     * @return PolicyValidationResult with constraint details and warnings
     */
    public Mono<PolicyValidationResult> validateWithPolicyConstraints(StrategicOption option, String contextId) {
        log.debug("Validating option {} against policy constraints for context {}", option.optionId(), contextId);

        return getApplicablePolicies(contextId, option.affectedEntities())
            .flatMap(policies -> {
                if (policies.isEmpty()) {
                    log.debug("No applicable policies found for context {}", contextId);
                    return Mono.just(PolicyValidationResult.noApplicablePolicies(option.optionId()));
                }

                return getWebClient()
                    .post()
                    .uri("/api/v1/policies/validate")
                    .bodyValue(Map.of(
                        "optionId", option.optionId(),
                        "contextId", contextId,
                        "affectedEntities", option.affectedEntities(),
                        "policyIds", policies.stream().map(PolicySummary::policyId).toList(),
                        "optionAttributes", Map.of(
                            "description", option.description(),
                            "perceptionConfidence", option.perceptionContribution() != null 
                                ? option.perceptionContribution().confidence() : 0.0,
                            "odysseyConfidence", option.odysseyContribution() != null 
                                ? option.odysseyContribution().confidence() : 0.0,
                            "overallConfidence", option.synthesis() != null 
                                ? option.synthesis().overallConfidence() : 0.0
                        )
                    ))
                    .retrieve()
                    .bodyToMono(PolicyValidationResponse.class)
                    .map(response -> mapToPolicyValidationResult(option.optionId(), response, policies))
                    .doOnSuccess(result -> {
                        meterRegistry.counter("nexus.integration.plato.policy_validation",
                            "passed", String.valueOf(result.passed()),
                            "violationCount", String.valueOf(result.violations().size())).increment();
                    });
            })
            .onErrorResume(e -> {
                log.warn("Policy constraint validation failed: {}", e.getMessage());
                return Mono.just(PolicyValidationResult.error(option.optionId(), e.getMessage()));
            });
    }

    /**
     * Get applicable policies for a context and set of entities.
     *
     * @param contextId The context ID (world/scope)
     * @param affectedEntities List of affected entity IDs
     * @return List of applicable policy summaries
     */
    public Mono<List<PolicySummary>> getApplicablePolicies(String contextId, List<String> affectedEntities) {
        log.debug("Getting applicable policies for context {} with {} entities", contextId, affectedEntities.size());

        return getWebClient()
            .post()
            .uri("/api/v1/policies/applicable")
            .bodyValue(Map.of(
                "contextId", contextId,
                "entityIds", affectedEntities
            ))
            .retrieve()
            .bodyToFlux(PolicySummary.class)
            .collectList()
            .doOnSuccess(policies -> {
                log.debug("Found {} applicable policies for context {}", policies.size(), contextId);
            })
            .onErrorReturn(List.of());
    }

    /**
     * Get a specific policy by ID.
     *
     * @param policyId The policy ID
     * @return Policy details
     */
    public Mono<PolicyDetails> getPolicy(String policyId) {
        log.debug("Getting policy: {}", policyId);

        return getWebClient()
            .get()
            .uri("/api/v1/policies/{policyId}", policyId)
            .retrieve()
            .bodyToMono(PolicyDetails.class)
            .onErrorResume(e -> {
                log.warn("Failed to get policy {}: {}", policyId, e.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Wire policy violations into a strategic option as warnings.
     * <p>
     * Updates the option's synthesis with warnings derived from policy violations,
     * adjusting confidence and adding risk assessments.
     *
     * @param option The original option
     * @param validationResult The policy validation result
     * @return Updated option with policy warnings incorporated
     */
    public StrategicOption incorporatePolicyWarnings(StrategicOption option, PolicyValidationResult validationResult) {
        if (validationResult.passed() || validationResult.violations().isEmpty()) {
            return option;
        }

        List<StrategicOption.Risk> augmentedRisks = new java.util.ArrayList<>(
            option.synthesis() != null ? option.synthesis().identifiedRisks() : List.of()
        );

        // Convert policy violations to risks
        for (PolicyViolation violation : validationResult.violations()) {
            augmentedRisks.add(new StrategicOption.Risk(
                UUID.randomUUID().toString(),
                "Policy violation: " + violation.description(),
                violation.severity(),
                0.9, // High probability since violation is detected
                violation.remediation() != null ? violation.remediation() : "Review policy compliance"
            ));
        }

        // Adjust confidence based on violation severity
        double maxSeverity = validationResult.violations().stream()
            .mapToDouble(PolicyViolation::severity)
            .max()
            .orElse(0.0);
        double confidenceReduction = Math.min(0.3, maxSeverity * 0.3);
        double adjustedConfidence = option.synthesis() != null 
            ? option.synthesis().overallConfidence() * (1.0 - confidenceReduction)
            : 0.5;

        // Update synthesis with policy warnings
        String originalRecommendation = option.synthesis() != null 
            ? option.synthesis().recommendedAction() 
            : "";
        String warningPrefix = validationResult.violations().size() > 1 
            ? "[" + validationResult.violations().size() + " POLICY VIOLATIONS] "
            : "[POLICY VIOLATION] ";

        StrategicOption.Synthesis adjustedSynthesis = new StrategicOption.Synthesis(
            adjustedConfidence,
            augmentedRisks,
            option.synthesis() != null ? option.synthesis().reversibilityWindow() : null,
            warningPrefix + originalRecommendation,
            (int) (adjustedConfidence * 10),
            option.synthesis() != null ? option.synthesis().dependencies() : List.of(),
            option.synthesis() != null ? option.synthesis().confidenceBySystem() : Map.of()
        );

        // Update PLATO contribution with policy constraint information
        List<StrategicOption.GovernanceConstraint> constraints = new java.util.ArrayList<>(
            option.platoContribution() != null ? option.platoContribution().governanceConstraints() : List.of()
        );
        for (PolicyViolation violation : validationResult.violations()) {
            constraints.add(new StrategicOption.GovernanceConstraint(
                violation.policyId(),
                "POLICY_VIOLATION",
                violation.description(),
                false
            ));
        }

        StrategicOption.PlatoContribution updatedPlato = new StrategicOption.PlatoContribution(
            constraints,
            option.platoContribution() != null ? option.platoContribution().validationProofs() : List.of(),
            validationResult.passed() 
                ? StrategicOption.GovernanceStatus.COMPLIANT 
                : StrategicOption.GovernanceStatus.NON_COMPLIANT,
            option.platoContribution() != null ? option.platoContribution().requiredApprovals() : List.of()
        );

        return StrategicOption.builder()
            .optionId(option.optionId())
            .description(option.description())
            .affectedEntities(option.affectedEntities())
            .perceptionContribution(option.perceptionContribution())
            .odysseyContribution(option.odysseyContribution())
            .capsuleContribution(option.capsuleContribution())
            .platoContribution(updatedPlato)
            .synthesis(adjustedSynthesis)
            .createdAt(option.createdAt())
            .build();
    }

    // --- Policy Validation Helper Methods ---

    private PolicyValidationResult mapToPolicyValidationResult(
            String optionId, PolicyValidationResponse response, List<PolicySummary> policies) {
        List<PolicyViolation> violations = response.violations() != null
            ? response.violations().stream()
                .map(v -> new PolicyViolation(
                    v.policyId(),
                    v.policyName(),
                    v.violationType(),
                    v.description(),
                    v.severity(),
                    v.remediation()
                ))
                .toList()
            : List.of();

        List<PolicyWarning> warnings = response.warnings() != null
            ? response.warnings().stream()
                .map(w -> new PolicyWarning(
                    w.policyId(),
                    w.warningType(),
                    w.description(),
                    w.recommendation()
                ))
                .toList()
            : List.of();

        return new PolicyValidationResult(
            optionId,
            response.passed(),
            violations,
            warnings,
            policies,
            response.evaluatedAt(),
            null
        );
    }

    /**
     * Check if an entity complies with PLATO specs.
     *
     * @param nodeId The RIM node ID
     * @return Compliance check result
     */
    public Mono<ComplianceResult> checkCompliance(String nodeId) {
        log.debug("Checking PLATO compliance for node {}", nodeId);

        return getWebClient()
            .get()
            .uri("/api/v1/specs/compliance/{nodeId}", nodeId)
            .retrieve()
            .bodyToMono(ComplianceResult.class)
            .doOnSuccess(result -> {
                meterRegistry.counter("nexus.integration.plato.compliance",
                    "compliant", String.valueOf(result.compliant())).increment();
            })
            .onErrorReturn(new ComplianceResult(true, List.of(), List.of()));
    }

    /**
     * Get active specs for a scope.
     *
     * @param scopeId The scope ID
     * @return List of active spec summaries
     */
    public Mono<List<SpecSummary>> getActiveSpecs(String scopeId) {
        return getWebClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/specs")
                .queryParam("scope", scopeId)
                .queryParam("status", "ACTIVE")
                .build())
            .retrieve()
            .bodyToFlux(SpecSummary.class)
            .collectList();
    }

    // --- Event handlers ---

    private void handleProofVerified(Map<String, Object> event) {
        String proofId = (String) event.get("object_id");
        String specId = (String) event.get("related_spec_id");
        
        // Generate learning signal for PERCEPTION
        LearningSignal signal = LearningSignal.builder()
            .signalId(UUID.randomUUID().toString())
            .sourceSystem("PLATO")
            .targetSystem("PERCEPTION")
            .signalType(LearningSignal.SignalType.VALIDATION_RESULT)
            .payload(Map.of(
                "proofId", proofId,
                "specId", specId != null ? specId : "unknown",
                "verified", true
            ))
            .confidence(0.9)
            .build();

        healthService.recordLearningSignal(signal).subscribe();
        log.debug("Recorded proof verification for learning: {}", proofId);
    }

    private void handleGovernanceViolation(Map<String, Object> event) {
        String objectId = (String) event.get("object_id");
        String violationType = (String) event.get("governance_violation_type");
        
        log.warn("Governance violation detected: {} - {}", objectId, violationType);

        // Generate learning signal
        LearningSignal signal = LearningSignal.builder()
            .signalId(UUID.randomUUID().toString())
            .sourceSystem("PLATO")
            .targetSystem("PERCEPTION")
            .signalType(LearningSignal.SignalType.VALIDATION_RESULT)
            .payload(Map.of(
                "objectId", objectId,
                "violationType", violationType != null ? violationType : "UNKNOWN",
                "action", "NEEDS_ATTENTION"
            ))
            .confidence(1.0)
            .build();

        healthService.recordLearningSignal(signal).subscribe();

        meterRegistry.counter("nexus.integration.plato.violation",
            "type", violationType != null ? violationType : "unknown").increment();
    }

    private void handleSpecStatusChanged(Map<String, Object> event) {
        String specId = (String) event.get("object_id");
        String newStatus = (String) event.get("new_status");

        log.debug("Spec {} status changed to {}", specId, newStatus);
        
        // Could trigger re-validation of affected options
        meterRegistry.counter("nexus.integration.plato.spec_change",
            "status", newStatus != null ? newStatus : "unknown").increment();
    }

    // --- Private helpers ---

    private WebClient getWebClient() {
        return webClientBuilder.baseUrl(platoUrl).build();
    }

    private StrategicOption updateOptionWithValidation(StrategicOption option, ValidationResponse response) {
        List<StrategicOption.GovernanceConstraint> constraints = response.constraints().stream()
            .map(c -> new StrategicOption.GovernanceConstraint(
                c.specId(),
                c.constraintType(),
                c.description(),
                c.satisfied()
            ))
            .toList();

        List<StrategicOption.ValidationProof> proofs = response.proofs().stream()
            .map(p -> new StrategicOption.ValidationProof(
                p.proofId(),
                p.proofType(),
                p.valid(),
                p.validatedAt()
            ))
            .toList();

        StrategicOption.GovernanceStatus status = switch (response.status()) {
            case "COMPLIANT" -> StrategicOption.GovernanceStatus.COMPLIANT;
            case "NON_COMPLIANT" -> StrategicOption.GovernanceStatus.NON_COMPLIANT;
            case "REQUIRES_REVIEW" -> StrategicOption.GovernanceStatus.REQUIRES_REVIEW;
            default -> StrategicOption.GovernanceStatus.PARTIALLY_COMPLIANT;
        };

        StrategicOption.PlatoContribution platoContribution = new StrategicOption.PlatoContribution(
            constraints,
            proofs,
            status,
            response.requiredApprovals()
        );

        return StrategicOption.builder()
            .optionId(option.optionId())
            .description(option.description())
            .affectedEntities(option.affectedEntities())
            .perceptionContribution(option.perceptionContribution())
            .odysseyContribution(option.odysseyContribution())
            .capsuleContribution(option.capsuleContribution())
            .platoContribution(platoContribution)
            .synthesis(option.synthesis())
            .createdAt(option.createdAt())
            .build();
    }

    // --- DTOs ---

    record ValidationResponse(
        String status,
        List<ConstraintResult> constraints,
        List<ProofResult> proofs,
        List<String> requiredApprovals
    ) {}

    record ConstraintResult(
        String specId,
        String constraintType,
        String description,
        boolean satisfied
    ) {}

    record ProofResult(
        String proofId,
        String proofType,
        boolean valid,
        java.time.Instant validatedAt
    ) {}

    public record ComplianceResult(
        boolean compliant,
        List<String> violations,
        List<String> warnings
    ) {}

    public record SpecSummary(
        String specId,
        String name,
        String specType,
        String status,
        String scope
    ) {}

    // --- Policy Validation DTOs ---

    /**
     * Summary of a policy applicable to a context.
     */
    public record PolicySummary(
        String policyId,
        String policyName,
        String policyType,
        String scope,
        double priority
    ) {}

    /**
     * Detailed policy information.
     */
    public record PolicyDetails(
        String policyId,
        String policyName,
        String policyType,
        String scope,
        String description,
        List<PolicyRule> rules,
        Map<String, Object> metadata,
        java.time.Instant createdAt,
        java.time.Instant updatedAt
    ) {}

    /**
     * A single rule within a policy.
     */
    public record PolicyRule(
        String ruleId,
        String ruleType,
        String condition,
        String action,
        double severity
    ) {}

    /**
     * Response from PLATO policy validation endpoint.
     */
    record PolicyValidationResponse(
        boolean passed,
        List<ViolationEntry> violations,
        List<WarningEntry> warnings,
        java.time.Instant evaluatedAt
    ) {
        record ViolationEntry(
            String policyId,
            String policyName,
            String violationType,
            String description,
            double severity,
            String remediation
        ) {}

        record WarningEntry(
            String policyId,
            String warningType,
            String description,
            String recommendation
        ) {}
    }

    /**
     * Result of policy validation for a strategic option.
     */
    public record PolicyValidationResult(
        String optionId,
        boolean passed,
        List<PolicyViolation> violations,
        List<PolicyWarning> warnings,
        List<PolicySummary> evaluatedPolicies,
        java.time.Instant evaluatedAt,
        String error
    ) {
        public static PolicyValidationResult noApplicablePolicies(String optionId) {
            return new PolicyValidationResult(
                optionId, true, List.of(), List.of(), List.of(), java.time.Instant.now(), null
            );
        }

        public static PolicyValidationResult error(String optionId, String errorMessage) {
            return new PolicyValidationResult(
                optionId, false, List.of(), List.of(), List.of(), java.time.Instant.now(), errorMessage
            );
        }
    }

    /**
     * A policy violation found during validation.
     */
    public record PolicyViolation(
        String policyId,
        String policyName,
        String violationType,
        String description,
        double severity,
        String remediation
    ) {}

    /**
     * A policy warning (non-blocking) found during validation.
     */
    public record PolicyWarning(
        String policyId,
        String warningType,
        String description,
        String recommendation
    ) {}

    // --- Pluralism DTOs ---

    /**
     * Result of multi-school validation for a strategic option.
     */
    public record MultiSchoolValidationResult(
        String optionId,
        boolean pluralismUsed,
        boolean overallPassed,
        double consensusScore,
        double divergenceScore,
        String consensusOutcome,
        List<SchoolEvaluationResult> schoolResults,
        List<SchoolDisagreement> disagreements,
        String rationale
    ) {
        public boolean hasSignificantDivergence() {
            return divergenceScore > 0.3;
        }

        public boolean requiresHumanReview() {
            return divergenceScore > 0.4 || "NO_CONSENSUS".equals(consensusOutcome);
        }
    }

    /**
     * Result from a single governance school's evaluation.
     */
    public record SchoolEvaluationResult(
        String schoolCode,
        String schoolName,
        boolean passed,
        double score,
        double confidence
    ) {}

    /**
     * A disagreement between governance schools.
     */
    public record SchoolDisagreement(
        String category,
        List<String> disagreeingSchools,
        String description,
        double magnitude
    ) {}

    /**
     * PLATO pluralism feature status.
     */
    public record PluralismStatus(
        boolean configEnabled,
        boolean fullyOperational,
        long activeSchoolCount,
        double consensusThreshold,
        double divergenceCriticalThreshold
    ) {}

    /**
     * Summary of a governance school.
     */
    public record GovernanceSchoolSummary(
        String schoolCode,
        String name,
        String archetype,
        double trustScore,
        boolean active
    ) {}

    // --- Pluralism Response DTOs (from PLATO API) ---

    record PluralismEvaluationResponse(
        boolean passed,
        List<SchoolEvaluationDto> schoolEvaluations,
        PluralismSummaryDto pluralismSummary
    ) {}

    record SchoolEvaluationDto(
        String schoolCode,
        String schoolName,
        boolean passed,
        double score,
        double confidence
    ) {}

    record PluralismSummaryDto(
        double consensusScore,
        double divergenceScore,
        String consensusOutcome,
        List<DivergencePointDto> divergencePoints,
        String consensusRationale,
        boolean requiresHumanReview
    ) {}

    record DivergencePointDto(
        String category,
        List<String> disagreeingSchools,
        String description,
        double divergenceMagnitude
    ) {}

    record DivergenceResponse(
        String divergenceId,
        String policyId,
        List<String> involvedSchools,
        String divergenceCategory,
        double divergenceMagnitude,
        String description,
        String status
    ) {}
}

