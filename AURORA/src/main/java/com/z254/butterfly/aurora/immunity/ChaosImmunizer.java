package com.z254.butterfly.aurora.immunity;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.ImmunityRule;
import com.z254.butterfly.aurora.kafka.ChaosLearningProducer;
import com.z254.butterfly.aurora.observability.AuroraMetrics;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import com.z254.butterfly.aurora.remediation.AutoRemediator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Chaos immunization service.
 * <p>
 * Learns from incidents and successful remediations to:
 * <ul>
 *     <li>Extract resilience patterns</li>
 *     <li>Generate immunity rules</li>
 *     <li>Apply learned rules to future incidents</li>
 *     <li>Track rule effectiveness</li>
 * </ul>
 */
@Slf4j
@Service
public class ChaosImmunizer {

    private final ResiliencePatternLearner patternLearner;
    private final ImmunityRuleEngine ruleEngine;
    private final ChaosMemory chaosMemory;
    private final ChaosLearningProducer chaosLearningProducer;
    private final AuroraProperties auroraProperties;
    private final AuroraMetrics metrics;
    private final AuroraStructuredLogger logger;

    public ChaosImmunizer(ResiliencePatternLearner patternLearner,
                          ImmunityRuleEngine ruleEngine,
                          ChaosMemory chaosMemory,
                          ChaosLearningProducer chaosLearningProducer,
                          AuroraProperties auroraProperties,
                          AuroraMetrics metrics,
                          AuroraStructuredLogger logger) {
        this.patternLearner = patternLearner;
        this.ruleEngine = ruleEngine;
        this.chaosMemory = chaosMemory;
        this.chaosLearningProducer = chaosLearningProducer;
        this.auroraProperties = auroraProperties;
        this.metrics = metrics;
        this.logger = logger;
    }

    /**
     * Learn from a resolved incident and its remediation.
     */
    public Mono<LearningResult> learnFromIncident(Incident incident,
                                                   AutoRemediator.RemediationResult remediationResult) {
        if (!auroraProperties.getImmunity().isEnabled()) {
            return Mono.just(LearningResult.skipped("Immunity learning disabled"));
        }

        if (remediationResult.getStatus() != AutoRemediator.RemediationStatus.COMPLETED) {
            return Mono.just(LearningResult.skipped("Remediation not successful"));
        }

        log.info("Learning from incident: {} with successful remediation", incident.getId());

        return Mono.fromCallable(() -> {
            // Step 1: Extract resilience pattern
            ResiliencePatternLearner.ResiliencePattern pattern = 
                    patternLearner.extractPattern(incident, remediationResult);

            if (pattern.getConfidence() < auroraProperties.getImmunity().getMinRuleConfidence()) {
                return LearningResult.skipped("Pattern confidence too low: " + pattern.getConfidence());
            }

            // Step 2: Generate immunity rule
            ImmunityRule rule = ruleEngine.generateRule(pattern);

            // Step 3: Store in chaos memory
            chaosMemory.storeRule(rule);
            metrics.recordImmunityRuleCreated();

            // Step 4: Emit learning event
            chaosLearningProducer.emit(rule, incident, pattern);

            logger.logImmunityEvent(
                    rule.getRuleId(),
                    rule.getComponent(),
                    AuroraStructuredLogger.ImmunityEventType.RULE_CREATED,
                    "Immunity rule created from incident",
                    Map.of(
                            "incidentId", incident.getId(),
                            "patternType", pattern.getPatternType().name(),
                            "confidence", pattern.getConfidence()
                    ));

            return LearningResult.success(rule, pattern);
        });
    }

    /**
     * Apply immunity rules to an incident.
     */
    public Flux<AppliedRule> applyImmunityRules(Incident incident) {
        if (!auroraProperties.getImmunity().isEnabled()) {
            return Flux.empty();
        }

        String primaryComponent = incident.getPrimaryComponent();
        
        return chaosMemory.findRulesForComponent(primaryComponent)
                .filter(rule -> ruleEngine.isApplicable(rule, incident))
                .flatMap(rule -> applyRule(rule, incident));
    }

    /**
     * Check if an incident matches a known pattern.
     */
    public Mono<Optional<ImmunityRule>> findMatchingRule(Incident incident) {
        return chaosMemory.findRulesForComponent(incident.getPrimaryComponent())
                .filter(rule -> ruleEngine.isApplicable(rule, incident))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    /**
     * Update rule effectiveness based on outcome.
     */
    public Mono<Void> updateRuleEffectiveness(String ruleId, boolean wasEffective) {
        return chaosMemory.updateRuleEffectiveness(ruleId, wasEffective)
                .doOnSuccess(v -> {
                    if (wasEffective) {
                        metrics.recordImmunityRuleApplied(1.0);
                    }
                });
    }

    /**
     * Get immunity summary for a component.
     */
    public Mono<ImmunitySummary> getImmunitySummary(String componentId) {
        return chaosMemory.findRulesForComponent(componentId)
                .collectList()
                .map(rules -> {
                    int activeRules = (int) rules.stream()
                            .filter(ImmunityRule::isActive)
                            .count();
                    
                    double avgEffectiveness = rules.stream()
                            .filter(r -> r.getAppliedCount() > 0)
                            .mapToDouble(r -> (double) r.getSuccessCount() / r.getAppliedCount())
                            .average()
                            .orElse(0.0);

                    return ImmunitySummary.builder()
                            .componentId(componentId)
                            .totalRules(rules.size())
                            .activeRules(activeRules)
                            .averageEffectiveness(avgEffectiveness)
                            .rules(rules)
                            .build();
                });
    }

    // ========== Private Methods ==========

    private Mono<AppliedRule> applyRule(ImmunityRule rule, Incident incident) {
        log.debug("Applying immunity rule {} to incident {}", rule.getRuleId(), incident.getId());

        return Mono.fromCallable(() -> {
            // Increment applied count
            chaosMemory.incrementAppliedCount(rule.getRuleId()).subscribe();

            logger.logImmunityEvent(
                    rule.getRuleId(),
                    rule.getComponent(),
                    AuroraStructuredLogger.ImmunityEventType.RULE_APPLIED,
                    "Immunity rule applied to incident",
                    Map.of(
                            "incidentId", incident.getId(),
                            "ruleType", rule.getPatternType()
                    ));

            return AppliedRule.builder()
                    .rule(rule)
                    .incidentId(incident.getId())
                    .appliedAt(Instant.now())
                    .recommendedAction(rule.getRecommendedAction())
                    .parameters(rule.getActionParameters())
                    .build();
        });
    }

    // ========== Data Classes ==========

    @lombok.Data
    @lombok.Builder
    public static class LearningResult {
        private boolean success;
        private String skipReason;
        private ImmunityRule rule;
        private ResiliencePatternLearner.ResiliencePattern pattern;

        public static LearningResult success(ImmunityRule rule,
                                              ResiliencePatternLearner.ResiliencePattern pattern) {
            return LearningResult.builder()
                    .success(true)
                    .rule(rule)
                    .pattern(pattern)
                    .build();
        }

        public static LearningResult skipped(String reason) {
            return LearningResult.builder()
                    .success(false)
                    .skipReason(reason)
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class AppliedRule {
        private ImmunityRule rule;
        private String incidentId;
        private Instant appliedAt;
        private String recommendedAction;
        private Map<String, String> parameters;
    }

    @lombok.Data
    @lombok.Builder
    public static class ImmunitySummary {
        private String componentId;
        private int totalRules;
        private int activeRules;
        private double averageEffectiveness;
        private List<ImmunityRule> rules;
    }
}
