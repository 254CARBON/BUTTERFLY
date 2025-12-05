package com.z254.butterfly.aurora.rca;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.AnomalySignal;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fault propagation model for identifying root causes.
 * <p>
 * Analyzes how faults propagate through the system to identify:
 * <ul>
 *     <li>Potential root cause components</li>
 *     <li>Fault propagation paths</li>
 *     <li>Cascade detection</li>
 * </ul>
 */
@Slf4j
@Component
public class FaultPropagationModel {

    private final AuroraProperties auroraProperties;

    public FaultPropagationModel(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
    }

    /**
     * Find potential root cause components from the causal graph.
     *
     * @param graph the causal graph
     * @return set of potential root cause component IDs
     */
    public Set<String> findPotentialRoots(CausalGraph.ComponentGraph graph) {
        Set<String> potentialRoots = new HashSet<>();

        // Strategy 1: Find nodes with no incoming edges (true roots)
        Set<String> graphRoots = findGraphRoots(graph);
        potentialRoots.addAll(graphRoots);

        // Strategy 2: Find nodes with highest severity that are upstream
        Set<String> severityRoots = findSeverityBasedRoots(graph);
        potentialRoots.addAll(severityRoots);

        // Strategy 3: Find earliest anomalies (temporal roots)
        Set<String> temporalRoots = findTemporalRoots(graph);
        potentialRoots.addAll(temporalRoots);

        // Strategy 4: Apply cascade detection rules
        Set<String> cascadeRoots = detectCascadeOrigins(graph);
        potentialRoots.addAll(cascadeRoots);

        log.debug("Found {} potential root causes: graphRoots={}, severityRoots={}, temporalRoots={}, cascadeRoots={}",
                potentialRoots.size(), graphRoots.size(), severityRoots.size(), 
                temporalRoots.size(), cascadeRoots.size());

        return potentialRoots;
    }

    /**
     * Calculate fault propagation score for a component.
     *
     * @param graph the causal graph
     * @param componentId the component to score
     * @return propagation score (higher = more likely root cause)
     */
    public PropagationScore calculatePropagationScore(CausalGraph.ComponentGraph graph, 
                                                       String componentId) {
        CausalGraph.ComponentNode node = graph.getNode(componentId);
        if (node == null) {
            return PropagationScore.zero(componentId);
        }

        // Factor 1: Topology position (upstream = higher score)
        Set<String> downstream = graph.findDownstream(componentId);
        double topologyScore = calculateTopologyScore(node.getDepth(), downstream.size());

        // Factor 2: Severity
        double severityScore = node.getMaxSeverity();

        // Factor 3: Anomaly density
        double densityScore = Math.min(node.getAnomalyCount() / 5.0, 1.0);

        // Factor 4: Propagation reach
        double reachScore = Math.min(downstream.size() / 10.0, 1.0);

        // Weighted combination
        AuroraProperties.Rca rcaConfig = auroraProperties.getRca();
        double combinedScore = 
                (topologyScore * rcaConfig.getTopologyDistanceWeight()) +
                (severityScore * 0.2) +
                (densityScore * 0.2) +
                (reachScore * rcaConfig.getTimingCorrelationWeight());

        return PropagationScore.builder()
                .componentId(componentId)
                .topologyScore(topologyScore)
                .severityScore(severityScore)
                .densityScore(densityScore)
                .reachScore(reachScore)
                .combinedScore(combinedScore)
                .downstreamCount(downstream.size())
                .depth(node.getDepth())
                .build();
    }

    /**
     * Detect cascade pattern and find origin.
     */
    public CascadeAnalysis analyzeCascade(CausalGraph.ComponentGraph graph,
                                           List<AnomalySignal> anomalies) {
        CascadeAnalysis.Builder builder = CascadeAnalysis.builder();

        // Extract temporal sequence
        List<TimedAnomaly> timedAnomalies = extractTimedAnomalies(anomalies);
        
        if (timedAnomalies.isEmpty()) {
            return builder.cascadeDetected(false).build();
        }

        // Sort by timestamp
        timedAnomalies.sort(Comparator.comparingLong(TimedAnomaly::timestamp));

        // Check for cascade pattern (sequential failures)
        boolean cascadeDetected = detectCascadePattern(timedAnomalies, graph);
        builder.cascadeDetected(cascadeDetected);

        if (cascadeDetected) {
            // Find cascade origin
            String origin = timedAnomalies.get(0).componentId();
            builder.cascadeOrigin(origin);

            // Calculate cascade depth
            int depth = calculateCascadeDepth(origin, graph);
            builder.cascadeDepth(depth);

            // Calculate propagation rate
            double rate = calculatePropagationRate(timedAnomalies);
            builder.propagationRateMs(rate);
        }

        return builder.build();
    }

    // ========== Private Helper Methods ==========

    private Set<String> findGraphRoots(CausalGraph.ComponentGraph graph) {
        return graph.getNodes().values().stream()
                .filter(node -> graph.getIncomingEdges(node.getId()).isEmpty())
                .map(CausalGraph.ComponentNode::getId)
                .collect(Collectors.toSet());
    }

    private Set<String> findSeverityBasedRoots(CausalGraph.ComponentGraph graph) {
        // Find nodes with severity > 0.7 that have downstream impact
        return graph.getNodes().values().stream()
                .filter(node -> node.getMaxSeverity() > 0.7)
                .filter(node -> !graph.findDownstream(node.getId()).isEmpty())
                .map(CausalGraph.ComponentNode::getId)
                .collect(Collectors.toSet());
    }

    private Set<String> findTemporalRoots(CausalGraph.ComponentGraph graph) {
        // Find nodes at depth 0 (earliest in temporal chain)
        return graph.getNodes().values().stream()
                .filter(node -> node.getDepth() == 0)
                .map(CausalGraph.ComponentNode::getId)
                .collect(Collectors.toSet());
    }

    private Set<String> detectCascadeOrigins(CausalGraph.ComponentGraph graph) {
        Set<String> origins = new HashSet<>();
        
        // Find nodes that have significant downstream impact
        for (CausalGraph.ComponentNode node : graph.getNodes().values()) {
            Set<String> downstream = graph.findDownstream(node.getId());
            
            // If this node affects more than 3 downstream components
            // and has high severity, it's likely a cascade origin
            if (downstream.size() >= 3 && node.getMaxSeverity() > 0.5) {
                origins.add(node.getId());
            }
        }
        
        return origins;
    }

    private double calculateTopologyScore(int depth, int downstreamCount) {
        // Lower depth = more upstream = higher score
        double depthFactor = 1.0 / (depth + 1);
        // More downstream impact = higher score
        double impactFactor = Math.min(downstreamCount / 5.0, 1.0);
        return (depthFactor * 0.6) + (impactFactor * 0.4);
    }

    private List<TimedAnomaly> extractTimedAnomalies(List<AnomalySignal> anomalies) {
        List<TimedAnomaly> timed = new ArrayList<>();
        for (AnomalySignal signal : anomalies) {
            if (signal.getRimNodeId() != null) {
                timed.add(new TimedAnomaly(
                        signal.getRimNodeId(),
                        signal.getTimestamp(),
                        signal.getSeverity()
                ));
            }
        }
        return timed;
    }

    private boolean detectCascadePattern(List<TimedAnomaly> timedAnomalies, 
                                          CausalGraph.ComponentGraph graph) {
        if (timedAnomalies.size() < 2) return false;

        // Check if anomalies follow dependency order
        int cascadeMatches = 0;
        for (int i = 0; i < timedAnomalies.size() - 1; i++) {
            String earlier = timedAnomalies.get(i).componentId();
            String later = timedAnomalies.get(i + 1).componentId();
            
            // Check if there's a path from earlier to later in the graph
            if (graph.findDownstream(earlier).contains(later)) {
                cascadeMatches++;
            }
        }

        // If more than half the transitions follow dependency order, it's a cascade
        return cascadeMatches > timedAnomalies.size() / 2;
    }

    private int calculateCascadeDepth(String origin, CausalGraph.ComponentGraph graph) {
        Set<String> downstream = graph.findDownstream(origin);
        int maxDepth = 0;
        
        for (String node : downstream) {
            CausalGraph.ComponentNode nodeObj = graph.getNode(node);
            if (nodeObj != null) {
                maxDepth = Math.max(maxDepth, nodeObj.getDepth());
            }
        }
        
        return maxDepth;
    }

    private double calculatePropagationRate(List<TimedAnomaly> timedAnomalies) {
        if (timedAnomalies.size() < 2) return 0;
        
        long totalTime = timedAnomalies.get(timedAnomalies.size() - 1).timestamp() - 
                         timedAnomalies.get(0).timestamp();
        
        return totalTime > 0 ? (double) totalTime / (timedAnomalies.size() - 1) : 0;
    }

    // ========== Data Classes ==========

    private record TimedAnomaly(String componentId, long timestamp, double severity) {}

    @Data
    @lombok.Builder
    public static class PropagationScore {
        private final String componentId;
        private final double topologyScore;
        private final double severityScore;
        private final double densityScore;
        private final double reachScore;
        private final double combinedScore;
        private final int downstreamCount;
        private final int depth;

        public static PropagationScore zero(String componentId) {
            return PropagationScore.builder()
                    .componentId(componentId)
                    .topologyScore(0)
                    .severityScore(0)
                    .densityScore(0)
                    .reachScore(0)
                    .combinedScore(0)
                    .downstreamCount(0)
                    .depth(0)
                    .build();
        }
    }

    @Data
    @lombok.Builder
    public static class CascadeAnalysis {
        private final boolean cascadeDetected;
        private final String cascadeOrigin;
        private final int cascadeDepth;
        private final double propagationRateMs;
    }
}
