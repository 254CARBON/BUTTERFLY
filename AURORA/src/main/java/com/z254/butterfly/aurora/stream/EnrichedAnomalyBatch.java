package com.z254.butterfly.aurora.stream;

import com.z254.butterfly.aurora.domain.model.AnomalySignal;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enriched batch of correlated anomalies ready for RCA.
 */
@Data
@Builder
public class EnrichedAnomalyBatch {
    
    /** Generated incident ID */
    private String incidentId;
    
    /** Component key (rimNodeId) */
    private String componentKey;
    
    /** Raw anomaly events */
    private List<AnomalySignal> anomalies;
    
    /** Maximum severity across anomalies */
    private double maxSeverity;
    
    /** All affected components */
    private Set<String> affectedComponents;
    
    /** Duration of anomaly window in ms */
    private long durationMs;
    
    /** Dependency context */
    private DependencyContext dependencyContext;
    
    /** Historical incident context */
    private HistoricalContext historicalContext;
    
    /** Clustering score (0.0 to 1.0) */
    private double clusteringScore;
    
    /** Incident priority (1 = critical, 5 = low) */
    private int priority;
    
    /** Timestamp of enrichment */
    private Instant enrichedAt;
    
    /** Correlation ID for tracing */
    private String correlationId;
    
    /** Tenant ID */
    private String tenantId;
    
    /**
     * Get the count of anomalies in this batch.
     */
    public int getAnomalyCount() {
        return anomalies != null ? anomalies.size() : 0;
    }
    
    /**
     * Check if this batch should trigger RCA.
     */
    public boolean shouldTriggerRca(int minAnomalies, double minSeverity) {
        return getAnomalyCount() >= minAnomalies && maxSeverity >= minSeverity;
    }
}

/**
 * Dependency context from topology analysis.
 */
@Data
@Builder
class DependencyContext {
    
    /** Upstream dependencies */
    private Set<String> upstreamComponents;
    
    /** Downstream dependents */
    private Set<String> downstreamComponents;
    
    /** Depth of each component in topology */
    private Map<String, Integer> topologyDepths;
    
    /** Total impact radius */
    private int impactRadius;
    
    /** Node type: ROOT, INTERNAL, LEAF */
    private String nodeType;
    
    /**
     * Get total related components count.
     */
    public int getTotalRelatedComponents() {
        int upstream = upstreamComponents != null ? upstreamComponents.size() : 0;
        int downstream = downstreamComponents != null ? downstreamComponents.size() : 0;
        return upstream + downstream;
    }
}

/**
 * Historical context from previous incidents.
 */
@Data
@Builder
class HistoricalContext {
    
    /** Previous incidents for affected components */
    private List<EnrichmentPipeline.PreviousIncident> previousIncidents;
    
    /** Number of recurrences */
    private int recurrenceCount;
    
    /** Average MTTR in milliseconds */
    private long averageMttrMs;
    
    /** Last successful remediation type */
    private String lastSuccessfulRemediation;
    
    /**
     * Check if this is a recurring issue.
     */
    public boolean isRecurring() {
        return recurrenceCount > 0;
    }
    
    /**
     * Check if there's a known remediation.
     */
    public boolean hasKnownRemediation() {
        return lastSuccessfulRemediation != null && !lastSuccessfulRemediation.isEmpty();
    }
}
