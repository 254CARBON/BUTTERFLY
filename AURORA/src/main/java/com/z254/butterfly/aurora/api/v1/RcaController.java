package com.z254.butterfly.aurora.api.v1;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.rca.RootCauseAnalyzer;
import com.z254.butterfly.aurora.stream.EnrichedAnomalyBatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API controller for Root Cause Analysis operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rca")
@Tag(name = "RCA", description = "Root Cause Analysis operations")
public class RcaController {

    private final RootCauseAnalyzer rootCauseAnalyzer;
    private final AuroraProperties auroraProperties;

    // In-memory storage for hypotheses
    private final Map<String, RcaHypothesis> hypotheses = new ConcurrentHashMap<>();

    public RcaController(RootCauseAnalyzer rootCauseAnalyzer,
                         AuroraProperties auroraProperties) {
        this.rootCauseAnalyzer = rootCauseAnalyzer;
        this.auroraProperties = auroraProperties;
    }

    @PostMapping("/analyze")
    @Operation(summary = "Trigger RCA", description = "Trigger manual RCA analysis")
    public Mono<ResponseEntity<AnalysisResponse>> triggerAnalysis(
            @RequestBody AnalysisRequest request) {

        log.info("Manual RCA triggered for incident: {}", request.getIncidentId());

        // Build enriched batch from request
        EnrichedAnomalyBatch batch = EnrichedAnomalyBatch.builder()
                .incidentId(request.getIncidentId())
                .componentKey(request.getPrimaryComponent())
                .anomalies(new ArrayList<>()) // Would be populated from actual anomalies
                .maxSeverity(request.getSeverity())
                .affectedComponents(new HashSet<>(request.getAffectedComponents()))
                .correlationId(request.getCorrelationId())
                .build();

        return rootCauseAnalyzer.analyze(batch)
                .collectList()
                .map(hyps -> {
                    // Store hypotheses
                    hyps.forEach(h -> hypotheses.put(h.getHypothesisId(), h));

                    return ResponseEntity.ok(AnalysisResponse.builder()
                            .incidentId(request.getIncidentId())
                            .hypothesesCount(hyps.size())
                            .hypotheses(hyps)
                            .analysisTimestamp(Instant.now())
                            .build());
                })
                .onErrorResume(error -> {
                    log.error("RCA analysis failed: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(AnalysisResponse.builder()
                                    .incidentId(request.getIncidentId())
                                    .error(error.getMessage())
                                    .build()));
                });
    }

    @GetMapping("/hypotheses")
    @Operation(summary = "Query hypotheses", description = "Query RCA hypotheses with filters")
    public Mono<ResponseEntity<HypothesesResponse>> queryHypotheses(
            @Parameter(description = "Filter by incident ID") 
            @RequestParam(required = false) String incidentId,
            @Parameter(description = "Filter by component") 
            @RequestParam(required = false) String component,
            @Parameter(description = "Minimum confidence threshold") 
            @RequestParam(required = false) Double minConfidence,
            @Parameter(description = "Page number") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size) {

        return Mono.fromCallable(() -> {
            double threshold = minConfidence != null ? minConfidence : 
                    auroraProperties.getRca().getMinConfidenceThreshold();

            List<RcaHypothesis> filtered = hypotheses.values().stream()
                    .filter(h -> incidentId == null || h.getIncidentId().equals(incidentId))
                    .filter(h -> component == null || 
                            h.getRootCauseComponent().contains(component))
                    .filter(h -> h.getConfidence() >= threshold)
                    .sorted(Comparator.comparingDouble(RcaHypothesis::getConfidence).reversed())
                    .skip((long) page * size)
                    .limit(size)
                    .toList();

            return ResponseEntity.ok(HypothesesResponse.builder()
                    .hypotheses(filtered)
                    .total(filtered.size())
                    .page(page)
                    .size(size)
                    .build());
        });
    }

    @GetMapping("/hypotheses/{id}")
    @Operation(summary = "Get hypothesis", description = "Get hypothesis details by ID")
    public Mono<ResponseEntity<RcaHypothesis>> getHypothesis(
            @Parameter(description = "Hypothesis ID") @PathVariable String id) {

        return Mono.justOrEmpty(hypotheses.get(id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/config")
    @Operation(summary = "Get RCA config", description = "Get current RCA configuration")
    public Mono<ResponseEntity<RcaConfig>> getRcaConfig() {
        AuroraProperties.Rca rcaConfig = auroraProperties.getRca();
        
        return Mono.just(ResponseEntity.ok(RcaConfig.builder()
                .correlationWindow(rcaConfig.getCorrelationWindow().toString())
                .minConfidenceThreshold(rcaConfig.getMinConfidenceThreshold())
                .maxHypotheses(rcaConfig.getMaxHypotheses())
                .minAnomaliesForRca(rcaConfig.getMinAnomaliesForRca())
                .topologyDistanceWeight(rcaConfig.getTopologyDistanceWeight())
                .timingCorrelationWeight(rcaConfig.getTimingCorrelationWeight())
                .symptomSimilarityWeight(rcaConfig.getSymptomSimilarityWeight())
                .build()));
    }

    // Internal method to store hypotheses
    public void storeHypothesis(RcaHypothesis hypothesis) {
        hypotheses.put(hypothesis.getHypothesisId(), hypothesis);
    }

    // ========== Request/Response DTOs ==========

    @lombok.Data
    public static class AnalysisRequest {
        private String incidentId;
        private String primaryComponent;
        private List<String> affectedComponents;
        private double severity;
        private String correlationId;
    }

    @lombok.Data
    @lombok.Builder
    public static class AnalysisResponse {
        private String incidentId;
        private int hypothesesCount;
        private List<RcaHypothesis> hypotheses;
        private Instant analysisTimestamp;
        private String error;
    }

    @lombok.Data
    @lombok.Builder
    public static class HypothesesResponse {
        private List<RcaHypothesis> hypotheses;
        private long total;
        private int page;
        private int size;
    }

    @lombok.Data
    @lombok.Builder
    public static class RcaConfig {
        private String correlationWindow;
        private double minConfidenceThreshold;
        private int maxHypotheses;
        private int minAnomaliesForRca;
        private double topologyDistanceWeight;
        private double timingCorrelationWeight;
        private double symptomSimilarityWeight;
    }
}
