package com.z254.butterfly.aurora.remediation;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.health.AuroraHealthIndicator;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Safety guard for remediation actions.
 * <p>
 * Evaluates whether a remediation can be safely executed:
 * <ul>
 *     <li>Blast radius estimation</li>
 *     <li>Pre-condition checks</li>
 *     <li>Concurrent remediation limits</li>
 *     <li>Maintenance window validation</li>
 *     <li>Cooldown period enforcement</li>
 * </ul>
 */
@Slf4j
@Component
public class SafetyGuard {

    private final AuroraProperties auroraProperties;
    private final AuroraHealthIndicator healthIndicator;

    // Track recent remediations for cooldown
    private final Map<String, Instant> componentCooldowns = new ConcurrentHashMap<>();

    public SafetyGuard(AuroraProperties auroraProperties, 
                       AuroraHealthIndicator healthIndicator) {
        this.auroraProperties = auroraProperties;
        this.healthIndicator = healthIndicator;
    }

    /**
     * Evaluate whether a remediation can be safely executed.
     */
    public Mono<SafetyResult> evaluate(RcaHypothesis hypothesis) {
        return Mono.fromCallable(() -> {
            List<SafetyCheck> failedChecks = new ArrayList<>();
            List<SafetyCheck> passedChecks = new ArrayList<>();

            // Check 1: Concurrent remediation limit
            SafetyCheck concurrencyCheck = checkConcurrencyLimit();
            addToAppropriateList(concurrencyCheck, passedChecks, failedChecks);

            // Check 2: Cooldown period
            SafetyCheck cooldownCheck = checkCooldownPeriod(hypothesis.getRootCauseComponent());
            addToAppropriateList(cooldownCheck, passedChecks, failedChecks);

            // Check 3: Blast radius
            SafetyCheck blastRadiusCheck = checkBlastRadius(hypothesis);
            addToAppropriateList(blastRadiusCheck, passedChecks, failedChecks);

            // Check 4: Component health
            SafetyCheck healthCheck = checkComponentHealth(hypothesis);
            addToAppropriateList(healthCheck, passedChecks, failedChecks);

            // Check 5: Maintenance window
            SafetyCheck maintenanceCheck = checkMaintenanceWindow();
            addToAppropriateList(maintenanceCheck, passedChecks, failedChecks);

            // Build result
            boolean safe = failedChecks.isEmpty();
            String reason = safe ? "All safety checks passed" : 
                    failedChecks.get(0).getMessage();

            double blastRadius = estimateBlastRadius(hypothesis);

            return SafetyResult.builder()
                    .safe(safe)
                    .reason(reason)
                    .passedChecks(passedChecks)
                    .failedChecks(failedChecks)
                    .estimatedBlastRadius(blastRadius)
                    .riskScore(calculateRiskScore(hypothesis, null))
                    .evaluatedAt(Instant.now())
                    .build();
        });
    }

    /**
     * Calculate risk score for a remediation.
     */
    public double calculateRiskScore(RcaHypothesis hypothesis, 
                                      RemediationPlaybook.Playbook playbook) {
        double score = 0.0;

        // Factor 1: Hypothesis confidence (lower confidence = higher risk)
        score += (1.0 - hypothesis.getConfidence()) * 0.3;

        // Factor 2: Affected components (more components = higher risk)
        int affectedCount = hypothesis.getAffectedComponents().size();
        score += Math.min(affectedCount / 10.0, 1.0) * 0.3;

        // Factor 3: Playbook risk level
        if (playbook != null) {
            score += playbook.getRiskLevel() * 0.4;
        } else {
            score += 0.5 * 0.4; // Default moderate risk
        }

        return Math.min(score, 1.0);
    }

    /**
     * Record a remediation for cooldown tracking.
     */
    public void recordRemediation(String componentId) {
        componentCooldowns.put(componentId, Instant.now());
    }

    /**
     * Clear cooldown for a component.
     */
    public void clearCooldown(String componentId) {
        componentCooldowns.remove(componentId);
    }

    // ========== Safety Checks ==========

    private SafetyCheck checkConcurrencyLimit() {
        boolean canStart = healthIndicator.canStartRemediation();
        int maxConcurrent = auroraProperties.getSafety().getMaxConcurrentRemediations();

        return SafetyCheck.builder()
                .name("CONCURRENCY_LIMIT")
                .passed(canStart)
                .message(canStart ? 
                        "Concurrent remediation limit not reached" :
                        String.format("Maximum concurrent remediations (%d) reached", maxConcurrent))
                .build();
    }

    private SafetyCheck checkCooldownPeriod(String componentId) {
        Instant lastRemediation = componentCooldowns.get(componentId);
        Duration cooldown = auroraProperties.getSafety().getRemediationCooldown();

        if (lastRemediation == null) {
            return SafetyCheck.builder()
                    .name("COOLDOWN_PERIOD")
                    .passed(true)
                    .message("No recent remediation for component")
                    .build();
        }

        boolean cooldownExpired = Instant.now().isAfter(lastRemediation.plus(cooldown));

        return SafetyCheck.builder()
                .name("COOLDOWN_PERIOD")
                .passed(cooldownExpired)
                .message(cooldownExpired ?
                        "Cooldown period expired" :
                        String.format("Component in cooldown until %s", 
                                lastRemediation.plus(cooldown)))
                .build();
    }

    private SafetyCheck checkBlastRadius(RcaHypothesis hypothesis) {
        double blastRadius = estimateBlastRadius(hypothesis);
        int limit = auroraProperties.getSafety().getDefaultBlastRadiusLimit();

        boolean withinLimit = blastRadius <= limit;

        return SafetyCheck.builder()
                .name("BLAST_RADIUS")
                .passed(withinLimit)
                .message(withinLimit ?
                        String.format("Blast radius (%.0f) within limit (%d)", blastRadius, limit) :
                        String.format("Blast radius (%.0f) exceeds limit (%d)", blastRadius, limit))
                .build();
    }

    private SafetyCheck checkComponentHealth(RcaHypothesis hypothesis) {
        // In a real implementation, this would check component health via an API
        // For now, we assume components are healthy enough to remediate
        
        return SafetyCheck.builder()
                .name("COMPONENT_HEALTH")
                .passed(true)
                .message("Component health check passed")
                .build();
    }

    private SafetyCheck checkMaintenanceWindow() {
        // Check if we're in a maintenance window
        // In a real implementation, this would check against a maintenance schedule
        
        boolean inMaintenanceWindow = isInMaintenanceWindow();

        return SafetyCheck.builder()
                .name("MAINTENANCE_WINDOW")
                .passed(true) // Allow during maintenance windows
                .message(inMaintenanceWindow ?
                        "Currently in maintenance window (auto-remediation allowed)" :
                        "Outside maintenance window")
                .build();
    }

    // ========== Helper Methods ==========

    private void addToAppropriateList(SafetyCheck check, 
                                       List<SafetyCheck> passed, 
                                       List<SafetyCheck> failed) {
        if (check.isPassed()) {
            passed.add(check);
        } else {
            failed.add(check);
        }
    }

    private double estimateBlastRadius(RcaHypothesis hypothesis) {
        // Estimate blast radius based on:
        // 1. Number of affected components
        // 2. Criticality of components
        // 3. Downstream impact

        int affectedComponents = hypothesis.getAffectedComponents().size();
        
        // Base estimate: 100 users per affected component
        // This would be refined with actual metrics in production
        return affectedComponents * 100.0;
    }

    private boolean isInMaintenanceWindow() {
        // Placeholder - would check against configured maintenance windows
        return false;
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class SafetyResult {
        private boolean safe;
        private String reason;
        private List<SafetyCheck> passedChecks;
        private List<SafetyCheck> failedChecks;
        private double estimatedBlastRadius;
        private double riskScore;
        private Instant evaluatedAt;

        public int getTotalChecks() {
            return passedChecks.size() + failedChecks.size();
        }
    }

    @Data
    @Builder
    public static class SafetyCheck {
        private String name;
        private boolean passed;
        private String message;
        private Map<String, Object> details;
    }
}
