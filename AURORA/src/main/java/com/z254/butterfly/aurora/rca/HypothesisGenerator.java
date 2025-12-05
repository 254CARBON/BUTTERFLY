package com.z254.butterfly.aurora.rca;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import com.z254.butterfly.aurora.stream.EnrichedAnomalyBatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hypothesis generator for RCA.
 * <p>
 * Generates ranked hypotheses based on:
 * <ul>
 *     <li>Causal graph analysis</li>
 *     <li>Symptom correlation</li>
 *     <li>Fault propagation scores</li>
 *     <li>Historical patterns</li>
 * </ul>
 */
@Slf4j
@Component
public class HypothesisGenerator {

    private final FaultPropagationModel faultPropagationModel;
    private final SymptomCorrelator symptomCorrelator;
    private final AuroraProperties auroraProperties;
    private final AuroraStructuredLogger logger;

    // Root cause type mapping based on anomaly patterns
    private static final Map<String, String> ANOMALY_TYPE_TO_ROOT_CAUSE = Map.ofEntries(
            Map.entry("LATENCY_SPIKE", "SERVICE_DEGRADATION"),
            Map.entry("ERROR_RATE", "SERVICE_DEGRADATION"),
            Map.entry("RESOURCE_EXHAUSTION", "RESOURCE_CONTENTION"),
            Map.entry("DRIFT", "CONFIGURATION_ERROR"),
            Map.entry("THROUGHPUT_DROP", "LOAD_SPIKE"),
            Map.entry("CONNECTION_FAILURE", "NETWORK_ISSUE"),
            Map.entry("TIMEOUT", "DEPENDENCY_FAILURE"),
            Map.entry("MEMORY_LEAK", "RESOURCE_CONTENTION"),
            Map.entry("CPU_SATURATION", "RESOURCE_CONTENTION"),
            Map.entry("DISK_FULL", "RESOURCE_CONTENTION"),
            Map.entry("NETWORK_PARTITION", "INFRASTRUCTURE_FAILURE")
    );

    // Remediation suggestions per root cause type
    private static final Map<String, List<SuggestedRemediation>> REMEDIATION_TEMPLATES = Map.ofEntries(
            Map.entry("SERVICE_DEGRADATION", List.of(
                    new SuggestedRemediation("RESTART", "Restart affected service", 1, 0.8),
                    new SuggestedRemediation("SCALE_UP", "Scale up service instances", 2, 0.6)
            )),
            Map.entry("RESOURCE_CONTENTION", List.of(
                    new SuggestedRemediation("SCALE_UP", "Add more resources", 1, 0.7),
                    new SuggestedRemediation("THROTTLE", "Apply rate limiting", 2, 0.5),
                    new SuggestedRemediation("CACHE_CLEAR", "Clear caches", 3, 0.4)
            )),
            Map.entry("DEPENDENCY_FAILURE", List.of(
                    new SuggestedRemediation("CIRCUIT_BREAK", "Enable circuit breaker", 1, 0.9),
                    new SuggestedRemediation("FAILOVER", "Trigger failover", 2, 0.7),
                    new SuggestedRemediation("TRAFFIC_REDIRECT", "Redirect traffic", 3, 0.5)
            )),
            Map.entry("NETWORK_ISSUE", List.of(
                    new SuggestedRemediation("FAILOVER", "Trigger network failover", 1, 0.7),
                    new SuggestedRemediation("TRAFFIC_REDIRECT", "Redirect traffic to healthy zone", 2, 0.6)
            )),
            Map.entry("CONFIGURATION_ERROR", List.of(
                    new SuggestedRemediation("ROLLBACK", "Rollback recent configuration", 1, 0.8),
                    new SuggestedRemediation("RESTART", "Restart to apply fix", 2, 0.5)
            )),
            Map.entry("INFRASTRUCTURE_FAILURE", List.of(
                    new SuggestedRemediation("FAILOVER", "Trigger infrastructure failover", 1, 0.9),
                    new SuggestedRemediation("NOTIFY", "Alert operations team", 2, 0.3)
            )),
            Map.entry("LOAD_SPIKE", List.of(
                    new SuggestedRemediation("SCALE_UP", "Auto-scale to handle load", 1, 0.8),
                    new SuggestedRemediation("THROTTLE", "Apply rate limiting", 2, 0.6)
            ))
    );

    public HypothesisGenerator(FaultPropagationModel faultPropagationModel,
                               SymptomCorrelator symptomCorrelator,
                               AuroraProperties auroraProperties,
                               AuroraStructuredLogger logger) {
        this.faultPropagationModel = faultPropagationModel;
        this.symptomCorrelator = symptomCorrelator;
        this.auroraProperties = auroraProperties;
        this.logger = logger;
    }

    /**
     * Generate RCA hypotheses.
     *
     * @param incidentId the incident identifier
     * @param graph the causal graph
     * @param cluster the symptom cluster
     * @param potentialRoots potential root cause components
     * @param batch the enriched anomaly batch
     * @return list of hypotheses, sorted by confidence
     */
    public List<RcaHypothesis> generate(String incidentId,
                                         CausalGraph.ComponentGraph graph,
                                         SymptomCorrelator.SymptomCluster cluster,
                                         Set<String> potentialRoots,
                                         EnrichedAnomalyBatch batch) {
        log.debug("Generating hypotheses for incident {} from {} potential roots",
                incidentId, potentialRoots.size());

        List<RcaHypothesis> hypotheses = new ArrayList<>();

        for (String rootComponent : potentialRoots) {
            try {
                RcaHypothesis hypothesis = generateHypothesis(
                        incidentId, rootComponent, graph, cluster, batch);
                
                if (hypothesis.getConfidence() >= auroraProperties.getRca().getMinConfidenceThreshold()) {
                    hypotheses.add(hypothesis);
                    
                    logger.logRcaEvent(incidentId, hypothesis.getHypothesisId(),
                            AuroraStructuredLogger.RcaEventType.HYPOTHESIS_GENERATED,
                            "Generated RCA hypothesis",
                            Map.of(
                                    "rootCauseComponent", rootComponent,
                                    "rootCauseType", hypothesis.getRootCauseType(),
                                    "confidence", hypothesis.getConfidence()
                            ));
                }
            } catch (Exception e) {
                log.warn("Failed to generate hypothesis for component {}: {}", 
                        rootComponent, e.getMessage());
            }
        }

        // Sort by confidence descending
        hypotheses.sort(Comparator.comparingDouble(RcaHypothesis::getConfidence).reversed());

        // Limit to max hypotheses
        return hypotheses.stream()
                .limit(auroraProperties.getRca().getMaxHypotheses())
                .collect(Collectors.toList());
    }

    /**
     * Generate a single hypothesis for a potential root cause.
     */
    private RcaHypothesis generateHypothesis(String incidentId,
                                              String rootComponent,
                                              CausalGraph.ComponentGraph graph,
                                              SymptomCorrelator.SymptomCluster cluster,
                                              EnrichedAnomalyBatch batch) {
        // Calculate propagation score
        FaultPropagationModel.PropagationScore propagationScore = 
                faultPropagationModel.calculatePropagationScore(graph, rootComponent);

        // Calculate symptom correlation
        double symptomCorrelation = symptomCorrelator.calculateRootCauseCorrelation(
                cluster, rootComponent, graph);

        // Determine root cause type
        String rootCauseType = determineRootCauseType(rootComponent, cluster);

        // Calculate overall confidence
        double confidence = calculateConfidence(propagationScore, symptomCorrelation, 
                cluster, rootComponent);

        // Build causal chain
        List<String> causalChain = buildCausalChain(rootComponent, graph);

        // Get supporting evidence
        List<String> evidence = buildSupportingEvidence(rootComponent, graph, cluster, 
                propagationScore);

        // Get affected components
        Set<String> affectedComponents = graph.findDownstream(rootComponent);
        affectedComponents.add(rootComponent);

        // Get anomaly IDs
        List<String> anomalyIds = cluster.getSymptomsByComponent()
                .getOrDefault(rootComponent, Collections.emptyList())
                .stream()
                .map(SymptomCorrelator.Symptom::getAnomalyId)
                .collect(Collectors.toList());

        // Generate suggested remediations
        List<RcaHypothesis.SuggestedRemediation> remediations = 
                generateSuggestedRemediations(rootCauseType, rootComponent);

        return RcaHypothesis.builder()
                .hypothesisId(generateHypothesisId(incidentId, rootComponent))
                .incidentId(incidentId)
                .timestamp(Instant.now())
                .rootCauseComponent(rootComponent)
                .rootCauseType(rootCauseType)
                .confidence(confidence)
                .rank(0) // Will be set after sorting
                .supportingEvidence(evidence)
                .anomalyIds(anomalyIds)
                .affectedComponents(affectedComponents)
                .causalChain(causalChain)
                .suggestedRemediations(remediations)
                .analysisMetrics(RcaHypothesis.AnalysisMetrics.builder()
                        .analysisTimeMs(0L) // Will be set by caller
                        .anomaliesAnalyzed(cluster.getClusterSize())
                        .componentsEvaluated(graph.getNodes().size())
                        .topologyDepth(propagationScore.getDepth())
                        .build())
                .correlationId(batch.getCorrelationId())
                .tenantId(batch.getTenantId())
                .build();
    }

    private String determineRootCauseType(String component, 
                                           SymptomCorrelator.SymptomCluster cluster) {
        // Get the dominant anomaly type for this component
        List<SymptomCorrelator.Symptom> componentSymptoms = 
                cluster.getSymptomsByComponent().getOrDefault(component, Collections.emptyList());

        if (componentSymptoms.isEmpty()) {
            return "UNKNOWN";
        }

        // Find most common anomaly type
        Map<String, Long> typeCounts = componentSymptoms.stream()
                .collect(Collectors.groupingBy(
                        SymptomCorrelator.Symptom::getAnomalyType, 
                        Collectors.counting()));

        String dominantType = typeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");

        return ANOMALY_TYPE_TO_ROOT_CAUSE.getOrDefault(dominantType, "UNKNOWN");
    }

    private double calculateConfidence(FaultPropagationModel.PropagationScore propagationScore,
                                        double symptomCorrelation,
                                        SymptomCorrelator.SymptomCluster cluster,
                                        String component) {
        AuroraProperties.Rca rcaConfig = auroraProperties.getRca();
        
        // Factor weights from configuration
        double topologyWeight = rcaConfig.getTopologyDistanceWeight();
        double timingWeight = rcaConfig.getTimingCorrelationWeight();
        double symptomWeight = rcaConfig.getSymptomSimilarityWeight();

        // Combined score
        double score = 
                (propagationScore.getCombinedScore() * topologyWeight) +
                (symptomCorrelation * symptomWeight) +
                (calculateTimingScore(cluster, component) * timingWeight);

        // Normalize to 0.0 - 1.0
        return Math.min(Math.max(score, 0.0), 1.0);
    }

    private double calculateTimingScore(SymptomCorrelator.SymptomCluster cluster, 
                                         String component) {
        List<SymptomCorrelator.Symptom> componentSymptoms = 
                cluster.getSymptomsByComponent().getOrDefault(component, Collections.emptyList());

        if (componentSymptoms.isEmpty()) {
            return 0.0;
        }

        // Get earliest timestamp for this component
        long componentEarliest = componentSymptoms.stream()
                .mapToLong(SymptomCorrelator.Symptom::getTimestamp)
                .min()
                .orElse(Long.MAX_VALUE);

        // Get earliest timestamp overall
        long overallEarliest = cluster.getSymptoms().stream()
                .mapToLong(SymptomCorrelator.Symptom::getTimestamp)
                .min()
                .orElse(Long.MAX_VALUE);

        // If this component had the earliest symptom, high timing score
        if (componentEarliest == overallEarliest) {
            return 1.0;
        }

        // Otherwise, decay based on how late
        long delay = componentEarliest - overallEarliest;
        long windowMs = auroraProperties.getRca().getCorrelationWindow().toMillis();
        
        return Math.max(0.0, 1.0 - ((double) delay / windowMs));
    }

    private List<String> buildCausalChain(String rootComponent, 
                                           CausalGraph.ComponentGraph graph) {
        List<String> chain = new ArrayList<>();
        chain.add(rootComponent);

        // BFS to build the chain
        Set<String> visited = new HashSet<>();
        visited.add(rootComponent);
        Queue<String> queue = new LinkedList<>();
        queue.add(rootComponent);

        while (!queue.isEmpty() && chain.size() < 10) {
            String current = queue.poll();
            for (CausalGraph.DependencyEdge edge : graph.getOutgoingEdges(current)) {
                if (visited.add(edge.getTarget())) {
                    chain.add(edge.getTarget());
                    queue.add(edge.getTarget());
                }
            }
        }

        return chain;
    }

    private List<String> buildSupportingEvidence(String component,
                                                  CausalGraph.ComponentGraph graph,
                                                  SymptomCorrelator.SymptomCluster cluster,
                                                  FaultPropagationModel.PropagationScore score) {
        List<String> evidence = new ArrayList<>();

        // Evidence 1: Topology position
        if (score.getDepth() == 0) {
            evidence.add("Component is at root of dependency tree");
        }

        // Evidence 2: Downstream impact
        if (score.getDownstreamCount() > 0) {
            evidence.add(String.format("Component affects %d downstream services", 
                    score.getDownstreamCount()));
        }

        // Evidence 3: Severity
        if (score.getSeverityScore() > 0.7) {
            evidence.add("High severity anomalies detected");
        }

        // Evidence 4: Temporal primacy
        List<SymptomCorrelator.Symptom> componentSymptoms = 
                cluster.getSymptomsByComponent().getOrDefault(component, Collections.emptyList());
        if (!componentSymptoms.isEmpty()) {
            long earliest = componentSymptoms.stream()
                    .mapToLong(SymptomCorrelator.Symptom::getTimestamp)
                    .min()
                    .orElse(Long.MAX_VALUE);
            long clusterEarliest = cluster.getSymptoms().stream()
                    .mapToLong(SymptomCorrelator.Symptom::getTimestamp)
                    .min()
                    .orElse(Long.MAX_VALUE);
            
            if (earliest == clusterEarliest) {
                evidence.add("First component to show symptoms");
            }
        }

        // Evidence 5: Anomaly density
        if (score.getDensityScore() > 0.5) {
            evidence.add("High concentration of anomalies");
        }

        return evidence;
    }

    private List<RcaHypothesis.SuggestedRemediation> generateSuggestedRemediations(
            String rootCauseType, String targetComponent) {
        
        List<SuggestedRemediation> templates = REMEDIATION_TEMPLATES.getOrDefault(
                rootCauseType, 
                List.of(new SuggestedRemediation("NOTIFY", "Notify operations team", 1, 0.3))
        );

        return templates.stream()
                .map(template -> RcaHypothesis.SuggestedRemediation.builder()
                        .actionType(template.actionType())
                        .targetComponent(targetComponent)
                        .description(template.description())
                        .priority(template.priority())
                        .estimatedImpact(template.estimatedImpact())
                        .parameters(new HashMap<>())
                        .build())
                .collect(Collectors.toList());
    }

    private String generateHypothesisId(String incidentId, String component) {
        return String.format("HYP-%s-%s-%d", 
                incidentId.substring(0, Math.min(8, incidentId.length())),
                component.hashCode(),
                System.currentTimeMillis() % 10000);
    }

    private record SuggestedRemediation(String actionType, String description, 
                                         int priority, double estimatedImpact) {}
}
