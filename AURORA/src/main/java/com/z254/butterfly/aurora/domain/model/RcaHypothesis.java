package com.z254.butterfly.aurora.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

/**
 * Root Cause Analysis hypothesis.
 * <p>
 * Represents a potential root cause identified by the RCA engine
 * with supporting evidence and suggested remediations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcaHypothesis {

    /** Unique hypothesis identifier */
    private String hypothesisId;

    /** Parent incident identifier */
    private String incidentId;

    /** Hypothesis generation timestamp */
    private Instant timestamp;

    /** Identified root cause component */
    private String rootCauseComponent;

    /** Type of root cause */
    private String rootCauseType;

    /** Confidence score (0.0 to 1.0) */
    private double confidence;

    /** Rank among hypotheses (1 = highest confidence) */
    private int rank;

    /** Evidence supporting this hypothesis */
    @Builder.Default
    private List<String> supportingEvidence = new ArrayList<>();

    /** Anomaly IDs that contributed to this hypothesis */
    @Builder.Default
    private List<String> anomalyIds = new ArrayList<>();

    /** Components affected by the root cause */
    @Builder.Default
    private Set<String> affectedComponents = new HashSet<>();

    /** Ordered chain of causality */
    @Builder.Default
    private List<String> causalChain = new ArrayList<>();

    /** Suggested remediation actions */
    @Builder.Default
    private List<SuggestedRemediation> suggestedRemediations = new ArrayList<>();

    /** Analysis metrics */
    private AnalysisMetrics analysisMetrics;

    /** Correlation ID for tracing */
    private String correlationId;

    /** Tenant identifier */
    private String tenantId;

    /** Additional metadata */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Check if this hypothesis meets minimum confidence threshold.
     */
    public boolean isViable(double threshold) {
        return confidence >= threshold;
    }

    /**
     * Get the primary suggested remediation.
     */
    public Optional<SuggestedRemediation> getPrimaryRemediation() {
        return suggestedRemediations.stream()
                .min(Comparator.comparingInt(SuggestedRemediation::getPriority));
    }

    /**
     * Suggested remediation action.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedRemediation {
        private String actionType;
        private String targetComponent;
        private String description;
        private int priority;
        private double estimatedImpact;
        @Builder.Default
        private Map<String, String> parameters = new HashMap<>();
    }

    /**
     * Analysis metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisMetrics {
        private long analysisTimeMs;
        private int anomaliesAnalyzed;
        private int componentsEvaluated;
        private int topologyDepth;
    }
}
