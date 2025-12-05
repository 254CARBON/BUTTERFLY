package com.z254.butterfly.aurora.stream;

import com.z254.butterfly.aurora.client.CapsuleEvidenceClient;
import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.AnomalySignal;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Enrichment pipeline for anomaly events.
 * <p>
 * Adds context to anomalies including:
 * <ul>
 *     <li>Component dependency graph</li>
 *     <li>Historical incident data</li>
 *     <li>Clustering scores</li>
 *     <li>Topology information</li>
 * </ul>
 */
@Slf4j
@Component
public class EnrichmentPipeline {

    private final AuroraProperties auroraProperties;
    private final CapsuleEvidenceClient capsuleClient;
    private final AuroraStructuredLogger logger;

    // Local cache for dependency graph
    private final Map<String, ComponentNode> dependencyCache = new HashMap<>();

    public EnrichmentPipeline(AuroraProperties auroraProperties,
                              CapsuleEvidenceClient capsuleClient,
                              AuroraStructuredLogger logger) {
        this.auroraProperties = auroraProperties;
        this.capsuleClient = capsuleClient;
        this.logger = logger;
    }

    /**
     * Enrich an anomaly aggregate with context.
     *
     * @param componentKey the component key (rimNodeId)
     * @param aggregate the anomaly aggregate
     * @return enriched anomaly batch
     */
    public EnrichedAnomalyBatch enrich(String componentKey,
                                        AnomalyStreamProcessor.AnomalyAggregate aggregate) {
        log.debug("Enriching anomaly batch for component: {}, count: {}", 
                componentKey, aggregate.getCount());

        EnrichedAnomalyBatch.Builder builder = EnrichedAnomalyBatch.builder()
                .componentKey(componentKey)
                .anomalies(aggregate.getAnomalies())
                .maxSeverity(aggregate.getMaxSeverity())
                .affectedComponents(aggregate.getAffectedComponents())
                .durationMs(aggregate.getDurationMs())
                .enrichedAt(Instant.now());

        // Step 1: Add dependency context
        DependencyContext dependencyContext = fetchDependencyContext(componentKey, 
                aggregate.getAffectedComponents());
        builder.dependencyContext(dependencyContext);

        // Step 2: Add historical incident context
        HistoricalContext historicalContext = fetchHistoricalContext(componentKey, 
                aggregate.getAffectedComponents());
        builder.historicalContext(historicalContext);

        // Step 3: Calculate clustering score
        double clusteringScore = calculateClusteringScore(aggregate, dependencyContext);
        builder.clusteringScore(clusteringScore);

        // Step 4: Determine incident priority
        int priority = determinePriority(aggregate, dependencyContext, historicalContext);
        builder.priority(priority);

        // Step 5: Generate incident ID if this is a new incident
        String incidentId = generateIncidentId(componentKey);
        builder.incidentId(incidentId);

        EnrichedAnomalyBatch result = builder.build();

        logger.logIncidentEvent(incidentId, 
                AuroraStructuredLogger.IncidentEventType.CREATED,
                "Incident created from correlated anomalies",
                Map.of(
                        "anomalyCount", aggregate.getCount(),
                        "maxSeverity", aggregate.getMaxSeverity(),
                        "affectedComponents", aggregate.getAffectedComponents().size(),
                        "clusteringScore", clusteringScore,
                        "priority", priority
                ));

        return result;
    }

    /**
     * Fetch dependency context for affected components.
     */
    private DependencyContext fetchDependencyContext(String componentKey, 
                                                      Set<String> affectedComponents) {
        DependencyContext.Builder builder = DependencyContext.builder();
        
        Set<String> upstreamComponents = new HashSet<>();
        Set<String> downstreamComponents = new HashSet<>();
        Map<String, Integer> topologyDepths = new HashMap<>();

        // Get cached or fetch dependency information
        ComponentNode node = dependencyCache.computeIfAbsent(componentKey, 
                k -> fetchComponentNode(k));

        if (node != null) {
            upstreamComponents.addAll(node.getUpstream());
            downstreamComponents.addAll(node.getDownstream());
            topologyDepths.put(componentKey, node.getDepth());
        }

        // Calculate impact radius
        int impactRadius = upstreamComponents.size() + downstreamComponents.size();
        
        // Determine if this is a leaf, internal, or root node
        String nodeType = determineNodeType(upstreamComponents, downstreamComponents);

        return builder
                .upstreamComponents(upstreamComponents)
                .downstreamComponents(downstreamComponents)
                .topologyDepths(topologyDepths)
                .impactRadius(impactRadius)
                .nodeType(nodeType)
                .build();
    }

    /**
     * Fetch historical incident context.
     */
    private HistoricalContext fetchHistoricalContext(String componentKey, 
                                                      Set<String> affectedComponents) {
        HistoricalContext.Builder builder = HistoricalContext.builder();

        try {
            // Query CAPSULE for historical incidents
            // This is a simplified version - real impl would call CAPSULE API
            List<PreviousIncident> previousIncidents = Collections.emptyList();
            
            // Calculate recurrence metrics
            int recurrenceCount = previousIncidents.size();
            long avgMttrMs = calculateAverageMttr(previousIncidents);
            String lastRemediationType = getLastSuccessfulRemediation(previousIncidents);

            return builder
                    .previousIncidents(previousIncidents)
                    .recurrenceCount(recurrenceCount)
                    .averageMttrMs(avgMttrMs)
                    .lastSuccessfulRemediation(lastRemediationType)
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to fetch historical context for {}: {}", componentKey, e.getMessage());
            return builder.build();
        }
    }

    /**
     * Calculate clustering score based on anomaly correlation.
     */
    private double calculateClusteringScore(AnomalyStreamProcessor.AnomalyAggregate aggregate,
                                            DependencyContext dependencyContext) {
        // Factors contributing to clustering:
        // 1. Number of anomalies in window
        // 2. Topology proximity of affected components
        // 3. Temporal proximity
        // 4. Severity correlation

        double countFactor = Math.min(aggregate.getCount() / 10.0, 1.0);
        double topologyFactor = calculateTopologyProximity(aggregate, dependencyContext);
        double temporalFactor = calculateTemporalProximity(aggregate);
        double severityFactor = aggregate.getMaxSeverity();

        // Weighted combination
        return (countFactor * 0.2) + 
               (topologyFactor * 0.3) + 
               (temporalFactor * 0.3) + 
               (severityFactor * 0.2);
    }

    /**
     * Determine incident priority based on context.
     */
    private int determinePriority(AnomalyStreamProcessor.AnomalyAggregate aggregate,
                                  DependencyContext dependencyContext,
                                  HistoricalContext historicalContext) {
        // Priority 1 = Critical, 5 = Low
        
        if (aggregate.getMaxSeverity() > 0.9) return 1;
        if (aggregate.getMaxSeverity() > 0.7) return 2;
        if (historicalContext.getRecurrenceCount() > 3) return Math.min(2, determinePriority(aggregate));
        if (dependencyContext.getImpactRadius() > 10) return Math.min(2, determinePriority(aggregate));
        if (aggregate.getMaxSeverity() > 0.5) return 3;
        if (aggregate.getCount() > 5) return 3;
        return 4;
    }

    private int determinePriority(AnomalyStreamProcessor.AnomalyAggregate aggregate) {
        if (aggregate.getMaxSeverity() > 0.7) return 2;
        if (aggregate.getMaxSeverity() > 0.5) return 3;
        return 4;
    }

    private String generateIncidentId(String componentKey) {
        return "INC-" + componentKey.hashCode() + "-" + System.currentTimeMillis();
    }

    private ComponentNode fetchComponentNode(String componentKey) {
        // Simplified - would fetch from ODYSSEY/CAPSULE
        return ComponentNode.builder()
                .id(componentKey)
                .upstream(Collections.emptySet())
                .downstream(Collections.emptySet())
                .depth(0)
                .build();
    }

    private String determineNodeType(Set<String> upstream, Set<String> downstream) {
        if (upstream.isEmpty() && !downstream.isEmpty()) return "ROOT";
        if (!upstream.isEmpty() && downstream.isEmpty()) return "LEAF";
        return "INTERNAL";
    }

    private long calculateAverageMttr(List<PreviousIncident> incidents) {
        if (incidents.isEmpty()) return 0;
        return (long) incidents.stream()
                .mapToLong(PreviousIncident::getMttrMs)
                .average()
                .orElse(0);
    }

    private String getLastSuccessfulRemediation(List<PreviousIncident> incidents) {
        return incidents.stream()
                .filter(PreviousIncident::isResolved)
                .findFirst()
                .map(PreviousIncident::getRemediationType)
                .orElse(null);
    }

    private double calculateTopologyProximity(AnomalyStreamProcessor.AnomalyAggregate aggregate,
                                              DependencyContext context) {
        // Higher score if affected components are topologically related
        Set<String> affected = aggregate.getAffectedComponents();
        Set<String> related = new HashSet<>();
        related.addAll(context.getUpstreamComponents());
        related.addAll(context.getDownstreamComponents());
        
        if (related.isEmpty()) return 0.5;
        
        long overlap = affected.stream().filter(related::contains).count();
        return (double) overlap / affected.size();
    }

    private double calculateTemporalProximity(AnomalyStreamProcessor.AnomalyAggregate aggregate) {
        // Higher score if anomalies occurred close together in time
        long durationMs = aggregate.getDurationMs();
        long windowMs = auroraProperties.getRca().getCorrelationWindow().toMillis();
        
        if (durationMs == 0) return 1.0;
        return 1.0 - ((double) durationMs / windowMs);
    }

    // ========== Inner Classes ==========

    @lombok.Builder
    @lombok.Data
    public static class ComponentNode {
        private String id;
        private Set<String> upstream;
        private Set<String> downstream;
        private int depth;
    }

    @lombok.Builder
    @lombok.Data
    public static class PreviousIncident {
        private String incidentId;
        private long timestamp;
        private long mttrMs;
        private String remediationType;
        private boolean resolved;
    }
}
