package com.z254.butterfly.aurora.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Immunity rule learned from chaos/incidents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImmunityRule {

    /** Unique rule identifier */
    private String ruleId;

    /** Component this rule applies to */
    private String component;

    /** Type of resilience pattern */
    private String patternType;

    /** Anomaly type this rule addresses */
    private String anomalyType;

    /** Condition that triggers this rule */
    private String triggerCondition;

    /** Recommended action when triggered */
    private String recommendedAction;

    /** Parameters for the recommended action */
    @Builder.Default
    private Map<String, String> actionParameters = new HashMap<>();

    /** Confidence score (0.0 to 1.0) */
    private double confidence;

    /** Source pattern ID */
    private String sourcePatternId;

    /** Source incident ID */
    private String sourceIncidentId;

    /** When this rule becomes valid */
    private Instant validFrom;

    /** When this rule expires (null = no expiry) */
    private Instant validUntil;

    /** Whether this rule is active */
    @Builder.Default
    private boolean active = true;

    /** Priority (lower = higher priority) */
    @Builder.Default
    private int priority = 3;

    /** Cooldown in milliseconds between applications */
    @Builder.Default
    private long cooldownMs = 60000;

    /** Number of times this rule has been applied */
    @Builder.Default
    private int appliedCount = 0;

    /** Number of successful applications */
    @Builder.Default
    private int successCount = 0;

    /** Last time this rule was applied */
    private Instant lastAppliedAt;

    /** Creation timestamp */
    private Instant createdAt;

    /** Tenant ID */
    private String tenantId;

    /**
     * Calculate effectiveness rate.
     */
    public double getEffectivenessRate() {
        if (appliedCount == 0) return 0.0;
        return (double) successCount / appliedCount;
    }

    /**
     * Check if rule is currently valid.
     */
    public boolean isValid() {
        if (!active) return false;
        Instant now = Instant.now();
        if (now.isBefore(validFrom)) return false;
        if (validUntil != null && now.isAfter(validUntil)) return false;
        return true;
    }

    /**
     * Check if rule is in cooldown.
     */
    public boolean isInCooldown() {
        if (lastAppliedAt == null) return false;
        return Instant.now().isBefore(lastAppliedAt.plusMillis(cooldownMs));
    }
}
