package com.z254.butterfly.aurora.rca;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.observability.AuroraMetrics;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import com.z254.butterfly.aurora.stream.EnrichedAnomalyBatch;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Root Cause Analysis orchestrator.
 * <p>
 * Coordinates the RCA pipeline:
 * <ol>
 *     <li>Build causal graph from correlated anomalies</li>
 *     <li>Apply fault propagation model</li>
 *     <li>Correlate symptoms</li>
 *     <li>Generate ranked hypotheses</li>
 * </ol>
 */
@Slf4j
@Service
public class RootCauseAnalyzer {

    private final CausalGraph causalGraph;
    private final FaultPropagationModel faultPropagationModel;
    private final SymptomCorrelator symptomCorrelator;
    private final HypothesisGenerator hypothesisGenerator;
    private final AuroraProperties auroraProperties;
    private final AuroraMetrics metrics;
    private final AuroraStructuredLogger logger;

    public RootCauseAnalyzer(CausalGraph causalGraph,
                             FaultPropagationModel faultPropagationModel,
                             SymptomCorrelator symptomCorrelator,
                             HypothesisGenerator hypothesisGenerator,
                             AuroraProperties auroraProperties,
                             AuroraMetrics metrics,
                             AuroraStructuredLogger logger) {
        this.causalGraph = causalGraph;
        this.faultPropagationModel = faultPropagationModel;
        this.symptomCorrelator = symptomCorrelator;
        this.hypothesisGenerator = hypothesisGenerator;
        this.auroraProperties = auroraProperties;
        this.metrics = metrics;
        this.logger = logger;
    }

    /**
     * Perform root cause analysis on an enriched anomaly batch.
     *
     * @param batch the enriched anomaly batch
     * @return flux of RCA hypotheses, ranked by confidence
     */
    public Flux<RcaHypothesis> analyze(EnrichedAnomalyBatch batch) {
        String incidentId = batch.getIncidentId();
        Timer.Sample timerSample = metrics.startRcaTimer();
        
        logger.logRcaEvent(incidentId, null, 
                AuroraStructuredLogger.RcaEventType.ANALYSIS_STARTED,
                "Starting RCA analysis",
                Map.of(
                        "anomalyCount", batch.getAnomalyCount(),
                        "maxSeverity", batch.getMaxSeverity(),
                        "affectedComponents", batch.getAffectedComponents().size()
                ));

        return Mono.fromCallable(() -> performAnalysis(batch, incidentId, timerSample))
                .flatMapMany(Flux::fromIterable)
                .doOnComplete(() -> log.debug("RCA analysis completed for incident: {}", incidentId))
                .doOnError(error -> {
                    metrics.recordRcaFailed(timerSample);
                    logger.logRcaEvent(incidentId, null,
                            AuroraStructuredLogger.RcaEventType.ANALYSIS_FAILED,
                            "RCA analysis failed: " + error.getMessage(),
                            Map.of("error", error.getClass().getSimpleName()));
                });
    }

    /**
     * Perform RCA analysis on an incident.
     */
    public Flux<RcaHypothesis> analyze(Incident incident) {
        return analyze(convertToEnrichedBatch(incident));
    }

    /**
     * Core analysis logic.
     */
    private List<RcaHypothesis> performAnalysis(EnrichedAnomalyBatch batch, 
                                                 String incidentId,
                                                 Timer.Sample timerSample) {
        Instant analysisStart = Instant.now();
        
        try {
            // Step 1: Build causal graph from anomalies
            log.debug("Building causal graph for incident: {}", incidentId);
            CausalGraph.ComponentGraph graph = causalGraph.buildFromAnomalies(
                    batch.getAnomalies(),
                    batch.getDependencyContext()
            );

            // Step 2: Apply fault propagation model
            log.debug("Applying fault propagation model");
            Set<String> potentialRoots = faultPropagationModel.findPotentialRoots(graph);
            
            if (potentialRoots.isEmpty()) {
                log.warn("No potential root causes identified for incident: {}", incidentId);
                potentialRoots = new HashSet<>(batch.getAffectedComponents());
            }

            // Step 3: Correlate symptoms
            log.debug("Correlating symptoms across {} potential roots", potentialRoots.size());
            SymptomCorrelator.SymptomCluster cluster = symptomCorrelator.correlate(
                    batch.getAnomalies(),
                    potentialRoots
            );

            // Step 4: Generate hypotheses
            log.debug("Generating hypotheses");
            List<RcaHypothesis> hypotheses = hypothesisGenerator.generate(
                    incidentId,
                    graph,
                    cluster,
                    potentialRoots,
                    batch
            );

            // Rank and limit hypotheses
            List<RcaHypothesis> rankedHypotheses = hypotheses.stream()
                    .sorted(Comparator.comparingDouble(RcaHypothesis::getConfidence).reversed())
                    .limit(auroraProperties.getRca().getMaxHypotheses())
                    .collect(Collectors.toList());

            // Update ranks
            for (int i = 0; i < rankedHypotheses.size(); i++) {
                rankedHypotheses.get(i).setRank(i + 1);
            }

            // Calculate analysis metrics
            long analysisTimeMs = java.time.Duration.between(analysisStart, Instant.now()).toMillis();
            
            // Record metrics
            double topConfidence = rankedHypotheses.isEmpty() ? 0.0 : 
                    rankedHypotheses.get(0).getConfidence();
            metrics.recordRcaCompleted(timerSample, rankedHypotheses.size(), topConfidence);

            // Log completion
            logger.logRcaEvent(incidentId, 
                    rankedHypotheses.isEmpty() ? null : rankedHypotheses.get(0).getHypothesisId(),
                    AuroraStructuredLogger.RcaEventType.ANALYSIS_COMPLETED,
                    "RCA analysis completed",
                    Map.of(
                            "hypothesesCount", rankedHypotheses.size(),
                            "topConfidence", topConfidence,
                            "analysisTimeMs", analysisTimeMs,
                            "potentialRootsEvaluated", potentialRoots.size()
                    ));

            // Check confidence threshold
            if (topConfidence < auroraProperties.getRca().getMinConfidenceThreshold()) {
                logger.logRcaEvent(incidentId, null,
                        AuroraStructuredLogger.RcaEventType.LOW_CONFIDENCE,
                        "All hypotheses below confidence threshold",
                        Map.of(
                                "topConfidence", topConfidence,
                                "threshold", auroraProperties.getRca().getMinConfidenceThreshold()
                        ));
            }

            return rankedHypotheses;

        } catch (Exception e) {
            log.error("RCA analysis failed for incident: {}", incidentId, e);
            throw new RcaAnalysisException("RCA analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert an incident to enriched batch format.
     */
    private EnrichedAnomalyBatch convertToEnrichedBatch(Incident incident) {
        return EnrichedAnomalyBatch.builder()
                .incidentId(incident.getId())
                .componentKey(incident.getPrimaryComponent())
                .anomalies(new ArrayList<>(incident.getRelatedAnomalies()))
                .maxSeverity(incident.getSeverity())
                .affectedComponents(incident.getAffectedComponents())
                .correlationId(incident.getCorrelationId())
                .tenantId(incident.getTenantId())
                .build();
    }

    /**
     * Validate if an incident can be analyzed.
     */
    public boolean canAnalyze(EnrichedAnomalyBatch batch) {
        return batch != null &&
                batch.getAnomalyCount() >= auroraProperties.getRca().getMinAnomaliesForRca() &&
                batch.getMaxSeverity() > 0;
    }

    /**
     * Exception for RCA analysis failures.
     */
    public static class RcaAnalysisException extends RuntimeException {
        public RcaAnalysisException(String message) {
            super(message);
        }

        public RcaAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
