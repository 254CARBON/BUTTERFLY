package com.z254.butterfly.aurora.governance;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.remediation.RemediationPlaybook;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Evaluates blast radius for remediation actions.
 * <p>
 * Calculates:
 * <ul>
 *     <li>Affected user count</li>
 *     <li>Affected transaction volume</li>
 *     <li>Data scope at risk</li>
 *     <li>Downstream service impact</li>
 * </ul>
 */
@Slf4j
@Component
public class BlastRadiusEvaluator {

    private final AuroraProperties auroraProperties;

    // Component criticality scores (higher = more critical)
    private static final Map<String, Double> COMPONENT_CRITICALITY = Map.of(
            "payment-service", 1.0,
            "order-service", 0.9,
            "user-service", 0.8,
            "inventory-service", 0.7,
            "notification-service", 0.3,
            "analytics-service", 0.2
    );

    // Action risk multipliers
    private static final Map<String, Double> ACTION_RISK_MULTIPLIERS = Map.of(
            "RESTART", 1.0,
            "SCALE_UP", 0.3,
            "SCALE_DOWN", 0.7,
            "CIRCUIT_BREAK", 0.5,
            "TRAFFIC_REDIRECT", 0.8,
            "FAILOVER", 1.2,
            "CACHE_CLEAR", 0.2,
            "ROLLBACK", 1.5,
            "THROTTLE", 0.4
    );

    public BlastRadiusEvaluator(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
    }

    /**
     * Evaluate blast radius for a remediation.
     */
    public BlastRadiusAssessment evaluate(RcaHypothesis hypothesis,
                                           RemediationPlaybook.Playbook playbook) {
        log.debug("Evaluating blast radius for component: {}, playbook: {}",
                hypothesis.getRootCauseComponent(), playbook.getName());

        // Calculate affected users
        int estimatedUsers = calculateAffectedUsers(hypothesis, playbook);

        // Calculate affected transactions
        int estimatedTransactions = calculateAffectedTransactions(hypothesis);

        // Calculate data scope
        DataScopeAssessment dataScope = assessDataScope(hypothesis);

        // Calculate downstream impact
        DownstreamImpact downstreamImpact = calculateDownstreamImpact(hypothesis);

        // Calculate risk score
        double riskScore = calculateOverallRisk(hypothesis, playbook, 
                estimatedUsers, downstreamImpact);

        // Build impact summary
        String impactSummary = buildImpactSummary(estimatedUsers, estimatedTransactions,
                dataScope, downstreamImpact);

        // Determine if within limits
        boolean withinLimits = estimatedUsers <= auroraProperties.getSafety()
                .getDefaultBlastRadiusLimit();

        return BlastRadiusAssessment.builder()
                .estimatedAffectedUsers(estimatedUsers)
                .estimatedAffectedTransactions(estimatedTransactions)
                .dataScope(dataScope)
                .downstreamImpact(downstreamImpact)
                .riskScore(riskScore)
                .impactSummary(impactSummary)
                .withinLimits(withinLimits)
                .recommendations(generateRecommendations(riskScore, withinLimits))
                .build();
    }

    /**
     * Quick check if blast radius is within acceptable limits.
     */
    public boolean isWithinLimits(RcaHypothesis hypothesis,
                                   RemediationPlaybook.Playbook playbook) {
        BlastRadiusAssessment assessment = evaluate(hypothesis, playbook);
        return assessment.isWithinLimits();
    }

    // ========== Private Methods ==========

    private int calculateAffectedUsers(RcaHypothesis hypothesis,
                                        RemediationPlaybook.Playbook playbook) {
        // Base estimate per component
        int baseUsers = 100;

        // Adjust for component criticality
        String component = hypothesis.getRootCauseComponent();
        double criticality = COMPONENT_CRITICALITY.getOrDefault(component, 0.5);
        
        // Adjust for affected component count
        int componentMultiplier = hypothesis.getAffectedComponents().size();

        // Adjust for action risk
        double actionMultiplier = ACTION_RISK_MULTIPLIERS.getOrDefault(
                playbook.getPrimaryAction(), 1.0);

        return (int) (baseUsers * criticality * componentMultiplier * actionMultiplier);
    }

    private int calculateAffectedTransactions(RcaHypothesis hypothesis) {
        // Estimate transactions per minute per component
        int tpmPerComponent = 1000;
        
        // Estimate duration of impact (5 minutes typical)
        int impactDurationMinutes = 5;

        return tpmPerComponent * hypothesis.getAffectedComponents().size() * impactDurationMinutes;
    }

    private DataScopeAssessment assessDataScope(RcaHypothesis hypothesis) {
        Set<String> affectedDataTypes = new HashSet<>();
        boolean criticalDataAtRisk = false;

        for (String component : hypothesis.getAffectedComponents()) {
            // Map components to data types (simplified)
            if (component.contains("user") || component.contains("auth")) {
                affectedDataTypes.add("USER_DATA");
                criticalDataAtRisk = true;
            }
            if (component.contains("payment") || component.contains("order")) {
                affectedDataTypes.add("TRANSACTION_DATA");
                criticalDataAtRisk = true;
            }
            if (component.contains("inventory")) {
                affectedDataTypes.add("INVENTORY_DATA");
            }
        }

        return DataScopeAssessment.builder()
                .affectedDataTypes(affectedDataTypes)
                .criticalDataAtRisk(criticalDataAtRisk)
                .dataIntegrityRisk(criticalDataAtRisk ? "HIGH" : "LOW")
                .build();
    }

    private DownstreamImpact calculateDownstreamImpact(RcaHypothesis hypothesis) {
        Set<String> affected = hypothesis.getAffectedComponents();
        int directlyAffected = affected.size();
        
        // Estimate indirectly affected (2x for each component on average)
        int indirectlyAffected = directlyAffected * 2;

        // Check for critical path components
        boolean criticalPathAffected = affected.stream()
                .anyMatch(c -> COMPONENT_CRITICALITY.getOrDefault(c, 0.0) > 0.7);

        return DownstreamImpact.builder()
                .directlyAffectedServices(directlyAffected)
                .indirectlyAffectedServices(indirectlyAffected)
                .criticalPathAffected(criticalPathAffected)
                .estimatedPropagationTimeSeconds(30 * directlyAffected)
                .build();
    }

    private double calculateOverallRisk(RcaHypothesis hypothesis,
                                         RemediationPlaybook.Playbook playbook,
                                         int affectedUsers,
                                         DownstreamImpact downstreamImpact) {
        double risk = 0.0;

        // Factor 1: Playbook risk level (40%)
        risk += playbook.getRiskLevel() * 0.4;

        // Factor 2: Affected users normalized (25%)
        int limit = auroraProperties.getSafety().getDefaultBlastRadiusLimit();
        risk += Math.min((double) affectedUsers / limit, 1.0) * 0.25;

        // Factor 3: Critical path affected (20%)
        risk += (downstreamImpact.isCriticalPathAffected() ? 1.0 : 0.0) * 0.2;

        // Factor 4: Hypothesis confidence inverse (15%)
        risk += (1.0 - hypothesis.getConfidence()) * 0.15;

        return Math.min(risk, 1.0);
    }

    private String buildImpactSummary(int users, int transactions,
                                       DataScopeAssessment dataScope,
                                       DownstreamImpact downstream) {
        return String.format("""
                Impact Assessment:
                - Estimated affected users: %d
                - Estimated affected transactions: %d
                - Data types at risk: %s
                - Critical data at risk: %s
                - Directly affected services: %d
                - Indirectly affected services: %d
                - Critical path affected: %s
                """,
                users,
                transactions,
                dataScope.getAffectedDataTypes(),
                dataScope.isCriticalDataAtRisk() ? "Yes" : "No",
                downstream.getDirectlyAffectedServices(),
                downstream.getIndirectlyAffectedServices(),
                downstream.isCriticalPathAffected() ? "Yes" : "No"
        );
    }

    private List<String> generateRecommendations(double riskScore, boolean withinLimits) {
        List<String> recommendations = new ArrayList<>();

        if (!withinLimits) {
            recommendations.add("Consider phased rollout to reduce blast radius");
            recommendations.add("Enable additional monitoring before execution");
        }

        if (riskScore > 0.7) {
            recommendations.add("Recommend manual approval for high-risk action");
            recommendations.add("Ensure rollback plan is tested and ready");
            recommendations.add("Alert on-call team before execution");
        } else if (riskScore > 0.4) {
            recommendations.add("Monitor closely during execution");
            recommendations.add("Prepare rollback procedures");
        }

        return recommendations;
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class BlastRadiusAssessment {
        private int estimatedAffectedUsers;
        private int estimatedAffectedTransactions;
        private DataScopeAssessment dataScope;
        private DownstreamImpact downstreamImpact;
        private double riskScore;
        private String impactSummary;
        private boolean withinLimits;
        private List<String> recommendations;
    }

    @Data
    @Builder
    public static class DataScopeAssessment {
        private Set<String> affectedDataTypes;
        private boolean criticalDataAtRisk;
        private String dataIntegrityRisk;
    }

    @Data
    @Builder
    public static class DownstreamImpact {
        private int directlyAffectedServices;
        private int indirectlyAffectedServices;
        private boolean criticalPathAffected;
        private int estimatedPropagationTimeSeconds;
    }
}
