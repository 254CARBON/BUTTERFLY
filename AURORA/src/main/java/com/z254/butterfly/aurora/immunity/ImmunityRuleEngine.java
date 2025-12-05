package com.z254.butterfly.aurora.immunity;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.ImmunityRule;
import com.z254.butterfly.aurora.domain.model.Incident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Engine for generating and applying immunity rules.
 */
@Slf4j
@Component
public class ImmunityRuleEngine {

    private final AuroraProperties auroraProperties;

    public ImmunityRuleEngine(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
    }

    /**
     * Generate an immunity rule from a resilience pattern.
     */
    public ImmunityRule generateRule(ResiliencePatternLearner.ResiliencePattern pattern) {
        log.debug("Generating immunity rule from pattern: {}", pattern.getPatternId());

        Instant validFrom = Instant.now();
        Instant validUntil = validFrom.plus(auroraProperties.getImmunity().getRuleTtl());

        return ImmunityRule.builder()
                .ruleId(generateRuleId(pattern))
                .component(pattern.getComponent())
                .patternType(pattern.getPatternType().name())
                .anomalyType(pattern.getAnomalyType())
                .triggerCondition(pattern.getTriggerCondition())
                .recommendedAction(pattern.getRecommendedAction())
                .actionParameters(pattern.getParameters())
                .confidence(pattern.getConfidence())
                .sourcePatternId(pattern.getPatternId())
                .sourceIncidentId(pattern.getSourceIncidentId())
                .validFrom(validFrom)
                .validUntil(validUntil)
                .active(true)
                .priority(calculatePriority(pattern))
                .cooldownMs(calculateCooldown(pattern))
                .appliedCount(0)
                .successCount(0)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Check if a rule is applicable to an incident.
     */
    public boolean isApplicable(ImmunityRule rule, Incident incident) {
        // Check if rule is active
        if (!rule.isActive()) {
            return false;
        }

        // Check validity period
        Instant now = Instant.now();
        if (now.isBefore(rule.getValidFrom()) || 
                (rule.getValidUntil() != null && now.isAfter(rule.getValidUntil()))) {
            return false;
        }

        // Check component match
        if (!rule.getComponent().equals(incident.getPrimaryComponent()) &&
                !incident.getAffectedComponents().contains(rule.getComponent())) {
            return false;
        }

        // Check anomaly type match (if specified)
        if (rule.getAnomalyType() != null && !rule.getAnomalyType().isEmpty()) {
            String incidentAnomalyType = extractAnomalyType(incident);
            if (!rule.getAnomalyType().equals(incidentAnomalyType)) {
                return false;
            }
        }

        // Evaluate trigger condition (simplified)
        return evaluateTriggerCondition(rule.getTriggerCondition(), incident);
    }

    /**
     * Rank rules for an incident by relevance.
     */
    public List<ImmunityRule> rankRules(List<ImmunityRule> rules, Incident incident) {
        return rules.stream()
                .sorted(Comparator.comparingDouble(r -> -calculateRelevanceScore(r, incident)))
                .toList();
    }

    // ========== Private Methods ==========

    private String generateRuleId(ResiliencePatternLearner.ResiliencePattern pattern) {
        return String.format("RULE-%s-%s-%d",
                pattern.getComponent().hashCode(),
                pattern.getPatternType().name().substring(0, 3),
                System.currentTimeMillis() % 100000);
    }

    private int calculatePriority(ResiliencePatternLearner.ResiliencePattern pattern) {
        // Higher confidence = higher priority (lower number)
        if (pattern.getConfidence() > 0.9) return 1;
        if (pattern.getConfidence() > 0.7) return 2;
        if (pattern.getConfidence() > 0.5) return 3;
        return 4;
    }

    private long calculateCooldown(ResiliencePatternLearner.ResiliencePattern pattern) {
        // Base cooldown from MTTR, minimum 1 minute
        long baseCooldown = Math.max(pattern.getMttrMs(), 60000);
        
        // Apply multiplier based on pattern type
        double multiplier = switch (pattern.getPatternType()) {
            case CIRCUIT_BREAKER_THRESHOLD -> 2.0;
            case FAILOVER_SEQUENCE -> 3.0;
            case SCALING_TRIGGER -> 1.5;
            default -> 1.0;
        };
        
        return (long) (baseCooldown * multiplier);
    }

    private String extractAnomalyType(Incident incident) {
        if (!incident.getHypotheses().isEmpty()) {
            return incident.getHypotheses().get(0).getRootCauseType();
        }
        return "";
    }

    private boolean evaluateTriggerCondition(String condition, Incident incident) {
        // Simplified condition evaluation
        // In production, use a proper expression evaluator
        
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        // Check for anomaly type match
        if (condition.contains("anomaly_type")) {
            String incidentType = extractAnomalyType(incident);
            return condition.contains(incidentType);
        }

        // Default to true if we can't evaluate
        return true;
    }

    private double calculateRelevanceScore(ImmunityRule rule, Incident incident) {
        double score = 0.0;

        // Confidence weight
        score += rule.getConfidence() * 0.4;

        // Priority weight (inverse)
        score += (5 - rule.getPriority()) / 5.0 * 0.2;

        // Effectiveness weight (if we have history)
        if (rule.getAppliedCount() > 0) {
            double effectiveness = (double) rule.getSuccessCount() / rule.getAppliedCount();
            score += effectiveness * 0.3;
        }

        // Recency weight
        long ageHours = java.time.Duration.between(rule.getCreatedAt(), Instant.now()).toHours();
        score += Math.max(0, 1.0 - ageHours / 720.0) * 0.1; // Decay over 30 days

        return score;
    }
}
