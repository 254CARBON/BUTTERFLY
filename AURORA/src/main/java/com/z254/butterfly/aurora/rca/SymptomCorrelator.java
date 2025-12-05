package com.z254.butterfly.aurora.rca;

import com.z254.butterfly.aurora.config.AuroraProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Symptom correlator for RCA.
 * <p>
 * Correlates symptoms (anomalies) across components to identify:
 * <ul>
 *     <li>Related symptom clusters</li>
 *     <li>Symptom patterns</li>
 *     <li>Cross-component correlations</li>
 * </ul>
 */
@Slf4j
@Component
public class SymptomCorrelator {

    private final AuroraProperties auroraProperties;

    public SymptomCorrelator(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
    }

    /**
     * Correlate anomalies into symptom clusters.
     *
     * @param anomalies the list of anomaly events
     * @param potentialRoots potential root cause components
     * @return symptom cluster with correlation information
     */
    public SymptomCluster correlate(List<Object> anomalies, Set<String> potentialRoots) {
        log.debug("Correlating symptoms from {} anomalies across {} potential roots",
                anomalies.size(), potentialRoots.size());

        // Extract symptoms from anomalies
        List<Symptom> symptoms = extractSymptoms(anomalies);

        // Group symptoms by type
        Map<String, List<Symptom>> symptomsByType = symptoms.stream()
                .collect(Collectors.groupingBy(Symptom::getAnomalyType));

        // Group symptoms by component
        Map<String, List<Symptom>> symptomsByComponent = symptoms.stream()
                .collect(Collectors.groupingBy(Symptom::getComponentId));

        // Calculate symptom correlations
        List<SymptomCorrelation> correlations = calculateCorrelations(symptoms);

        // Calculate cluster statistics
        double avgSeverity = symptoms.stream()
                .mapToDouble(Symptom::getSeverity)
                .average()
                .orElse(0.0);

        double maxSeverity = symptoms.stream()
                .mapToDouble(Symptom::getSeverity)
                .max()
                .orElse(0.0);

        // Find dominant symptom pattern
        String dominantPattern = findDominantPattern(symptomsByType);

        // Calculate temporal spread
        long temporalSpreadMs = calculateTemporalSpread(symptoms);

        return SymptomCluster.builder()
                .symptoms(symptoms)
                .symptomsByType(symptomsByType)
                .symptomsByComponent(symptomsByComponent)
                .correlations(correlations)
                .avgSeverity(avgSeverity)
                .maxSeverity(maxSeverity)
                .dominantPattern(dominantPattern)
                .temporalSpreadMs(temporalSpreadMs)
                .clusterSize(symptoms.size())
                .uniqueComponents(symptomsByComponent.keySet().size())
                .uniqueTypes(symptomsByType.keySet().size())
                .build();
    }

    /**
     * Calculate correlation between a symptom cluster and a potential root cause.
     */
    public double calculateRootCauseCorrelation(SymptomCluster cluster, 
                                                 String rootCauseComponent,
                                                 CausalGraph.ComponentGraph graph) {
        // Factor 1: Does the root cause component have symptoms?
        List<Symptom> rootSymptoms = cluster.getSymptomsByComponent()
                .getOrDefault(rootCauseComponent, Collections.emptyList());
        double symptomPresenceFactor = rootSymptoms.isEmpty() ? 0.3 : 1.0;

        // Factor 2: Severity alignment
        double rootSeverity = rootSymptoms.stream()
                .mapToDouble(Symptom::getSeverity)
                .max()
                .orElse(0.0);
        double severityAlignmentFactor = Math.min(rootSeverity / cluster.getMaxSeverity(), 1.0);

        // Factor 3: Temporal primacy
        long rootEarliestTime = rootSymptoms.stream()
                .mapToLong(Symptom::getTimestamp)
                .min()
                .orElse(Long.MAX_VALUE);
        long clusterEarliestTime = cluster.getSymptoms().stream()
                .mapToLong(Symptom::getTimestamp)
                .min()
                .orElse(Long.MAX_VALUE);
        double temporalPrimacyFactor = rootEarliestTime <= clusterEarliestTime ? 1.0 : 0.5;

        // Factor 4: Downstream symptom coverage
        Set<String> downstream = graph.findDownstream(rootCauseComponent);
        long downstreamWithSymptoms = downstream.stream()
                .filter(c -> cluster.getSymptomsByComponent().containsKey(c))
                .count();
        double coverageFactor = downstream.isEmpty() ? 0.5 : 
                (double) downstreamWithSymptoms / downstream.size();

        // Weighted combination
        return (symptomPresenceFactor * 0.2) +
               (severityAlignmentFactor * 0.3) +
               (temporalPrimacyFactor * 0.3) +
               (coverageFactor * 0.2);
    }

    // ========== Private Helper Methods ==========

    private List<Symptom> extractSymptoms(List<Object> anomalies) {
        List<Symptom> symptoms = new ArrayList<>();
        
        for (Object anomaly : anomalies) {
            if (anomaly instanceof org.apache.avro.generic.GenericRecord record) {
                Symptom symptom = Symptom.builder()
                        .anomalyId(getStringField(record, "anomalyId"))
                        .componentId(getStringField(record, "rimNodeId"))
                        .anomalyType(getStringField(record, "anomalyType"))
                        .severity(getDoubleField(record, "severity"))
                        .timestamp(getLongField(record, "timestamp"))
                        .metrics(getMapField(record, "metrics"))
                        .build();
                symptoms.add(symptom);
            }
        }
        
        return symptoms;
    }

    private List<SymptomCorrelation> calculateCorrelations(List<Symptom> symptoms) {
        List<SymptomCorrelation> correlations = new ArrayList<>();
        
        for (int i = 0; i < symptoms.size(); i++) {
            for (int j = i + 1; j < symptoms.size(); j++) {
                Symptom s1 = symptoms.get(i);
                Symptom s2 = symptoms.get(j);
                
                double score = calculateCorrelationScore(s1, s2);
                if (score > 0.3) { // Only include significant correlations
                    correlations.add(SymptomCorrelation.builder()
                            .symptom1Id(s1.getAnomalyId())
                            .symptom2Id(s2.getAnomalyId())
                            .correlationType(determineCorrelationType(s1, s2))
                            .score(score)
                            .build());
                }
            }
        }
        
        return correlations;
    }

    private double calculateCorrelationScore(Symptom s1, Symptom s2) {
        double score = 0.0;
        
        // Same component = high correlation
        if (s1.getComponentId().equals(s2.getComponentId())) {
            score += 0.4;
        }
        
        // Same type = medium correlation
        if (s1.getAnomalyType().equals(s2.getAnomalyType())) {
            score += 0.3;
        }
        
        // Close in time = correlation
        long timeDiff = Math.abs(s1.getTimestamp() - s2.getTimestamp());
        long windowMs = auroraProperties.getRca().getCorrelationWindow().toMillis();
        score += 0.3 * (1.0 - Math.min(timeDiff / (double) windowMs, 1.0));
        
        return score;
    }

    private String determineCorrelationType(Symptom s1, Symptom s2) {
        if (s1.getComponentId().equals(s2.getComponentId())) {
            return "SAME_COMPONENT";
        }
        if (s1.getAnomalyType().equals(s2.getAnomalyType())) {
            return "SAME_TYPE";
        }
        return "TEMPORAL";
    }

    private String findDominantPattern(Map<String, List<Symptom>> symptomsByType) {
        return symptomsByType.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }

    private long calculateTemporalSpread(List<Symptom> symptoms) {
        if (symptoms.isEmpty()) return 0;
        
        long min = symptoms.stream().mapToLong(Symptom::getTimestamp).min().orElse(0);
        long max = symptoms.stream().mapToLong(Symptom::getTimestamp).max().orElse(0);
        
        return max - min;
    }

    // Field extraction helpers
    private String getStringField(org.apache.avro.generic.GenericRecord record, String field) {
        Object value = record.get(field);
        return value != null ? value.toString() : "";
    }

    private double getDoubleField(org.apache.avro.generic.GenericRecord record, String field) {
        Object value = record.get(field);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private long getLongField(org.apache.avro.generic.GenericRecord record, String field) {
        Object value = record.get(field);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> getMapField(org.apache.avro.generic.GenericRecord record, String field) {
        Object value = record.get(field);
        if (value instanceof Map) {
            Map<String, Double> result = new HashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> {
                if (v instanceof Number) {
                    result.put(k.toString(), ((Number) v).doubleValue());
                }
            });
            return result;
        }
        return Collections.emptyMap();
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class Symptom {
        private String anomalyId;
        private String componentId;
        private String anomalyType;
        private double severity;
        private long timestamp;
        private Map<String, Double> metrics;
    }

    @Data
    @Builder
    public static class SymptomCluster {
        private List<Symptom> symptoms;
        private Map<String, List<Symptom>> symptomsByType;
        private Map<String, List<Symptom>> symptomsByComponent;
        private List<SymptomCorrelation> correlations;
        private double avgSeverity;
        private double maxSeverity;
        private String dominantPattern;
        private long temporalSpreadMs;
        private int clusterSize;
        private int uniqueComponents;
        private int uniqueTypes;

        public boolean hasSymptomsForComponent(String componentId) {
            return symptomsByComponent.containsKey(componentId);
        }
    }

    @Data
    @Builder
    public static class SymptomCorrelation {
        private String symptom1Id;
        private String symptom2Id;
        private String correlationType;
        private double score;
    }
}
