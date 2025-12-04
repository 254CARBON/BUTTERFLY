package com.z254.butterfly.nexus.adapter;

import com.z254.butterfly.common.perception.avro.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter that bridges PERCEPTION signal detection outputs to CAPSULE's ML/detection APIs.
 * 
 * <p>This adapter:
 * <ul>
 *   <li>Consumes from {@code perception.signals} Kafka topic</li>
 *   <li>Transforms PERCEPTION detection results to CAPSULE's PatternDetectionRequest/AnomalyDetectionRequest DTOs</li>
 *   <li>Calls CAPSULE's detection APIs: POST /api/v2/detection/patterns, POST /api/v2/detection/anomalies</li>
 *   <li>Stores high-value detection outputs as CAPSULE events suitable for ODYSSEY and PLATO</li>
 * </ul>
 * 
 * @see PerceptionDetectionTypeMapper
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "nexus.perception-capsule-adapter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PerceptionToCapsuleAdapter {

    private static final String CIRCUIT_BREAKER_NAME = "capsule-detection";
    private static final String SERVICE_NAME = "nexus-adapter";

    private final WebClient capsuleWebClient;
    private final PerceptionDetectionTypeMapper typeMapper;
    private final Duration defaultTimeout;
    
    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter patternsDetectedCounter;
    private final Counter anomaliesDetectedCounter;
    private final Counter detectionErrorsCounter;
    private final Timer detectionLatencyTimer;

    public PerceptionToCapsuleAdapter(
            WebClient.Builder webClientBuilder,
            PerceptionDetectionTypeMapper typeMapper,
            MeterRegistry meterRegistry,
            @Value("${nexus.capsule.url:http://localhost:8084}") String capsuleUrl,
            @Value("${nexus.capsule.timeout:5s}") Duration timeout) {
        
        this.capsuleWebClient = webClientBuilder
                .baseUrl(capsuleUrl)
                .build();
        this.typeMapper = typeMapper;
        this.defaultTimeout = timeout;
        
        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("nexus.adapter.events.processed")
                .tag("service", SERVICE_NAME)
                .tag("source", "perception")
                .register(meterRegistry);
        
        this.patternsDetectedCounter = Counter.builder("nexus.adapter.patterns.detected")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        this.anomaliesDetectedCounter = Counter.builder("nexus.adapter.anomalies.detected")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        this.detectionErrorsCounter = Counter.builder("nexus.adapter.detection.errors")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        this.detectionLatencyTimer = Timer.builder("nexus.adapter.detection.latency")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        log.info("Initialized PerceptionToCapsuleAdapter with CAPSULE URL: {}", capsuleUrl);
    }

    /**
     * Kafka listener for perception.signals topic.
     * Consumes PerceptionSignalEvent Avro messages and routes them to CAPSULE.
     */
    @KafkaListener(
            topics = "${perception.signals.topic:perception.signals}",
            groupId = "${nexus.perception-capsule-adapter.consumer-group:nexus-perception-adapter}",
            containerFactory = "perceptionSignalKafkaListenerContainerFactory"
    )
    public void onPerceptionSignalEvent(ConsumerRecord<String, PerceptionSignalEvent> record) {
        PerceptionSignalEvent event = record.value();
        String correlationId = event.getCorrelationId().toString();
        
        log.info("Received perception signal event: eventId={}, detector={}, rimNodeId={}, correlationId={}",
                event.getEventId(), event.getDetectorType(), event.getRimNodeId(), correlationId);
        
        eventsProcessedCounter.increment();
        
        Timer.Sample sample = Timer.start();
        
        try {
            processSignalEvent(event, correlationId);
            sample.stop(detectionLatencyTimer);
        } catch (Exception e) {
            sample.stop(detectionLatencyTimer);
            detectionErrorsCounter.increment();
            log.error("Failed to process perception signal event: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Process a PerceptionSignalEvent by routing it to appropriate CAPSULE detection APIs.
     */
    private void processSignalEvent(PerceptionSignalEvent event, String correlationId) {
        DetectorType detectorType = event.getDetectorType();
        String scopeId = event.getRimNodeId().toString();
        double confidence = event.getConfidence();
        
        // Check if high-value detection (confidence > 0.7)
        boolean isHighValue = confidence >= 0.7;
        
        // Route to pattern detection if applicable
        if (typeMapper.isPatternDetector(detectorType)) {
            Set<CapsulePatternType> patternTypes = typeMapper.getPatternTypes(detectorType);
            callCapsulePatternDetection(scopeId, patternTypes, event, correlationId)
                    .subscribe(
                            result -> {
                                log.debug("Pattern detection completed: scopeId={}, patternsFound={}",
                                        scopeId, result.patternsFound());
                                patternsDetectedCounter.increment(result.patternsFound());
                            },
                            error -> {
                                detectionErrorsCounter.increment();
                                log.error("Pattern detection failed: scopeId={}, error={}",
                                        scopeId, error.getMessage());
                            }
                    );
        }
        
        // Route to anomaly detection if applicable
        if (typeMapper.isAnomalyDetector(detectorType)) {
            Set<CapsuleAnomalyType> anomalyTypes = typeMapper.getAnomalyTypes(detectorType);
            callCapsuleAnomalyDetection(scopeId, anomalyTypes, event, correlationId)
                    .subscribe(
                            result -> {
                                log.debug("Anomaly detection completed: scopeId={}, isAnomaly={}, anomaliesFound={}",
                                        scopeId, result.isAnomaly(), result.anomaliesFound());
                                if (result.isAnomaly()) {
                                    anomaliesDetectedCounter.increment(result.anomaliesFound());
                                }
                            },
                            error -> {
                                detectionErrorsCounter.increment();
                                log.error("Anomaly detection failed: scopeId={}, error={}",
                                        scopeId, error.getMessage());
                            }
                    );
        }
        
        // For high-value detections, also analyze the full scope
        if (isHighValue) {
            callCapsuleScopeAnalysis(scopeId, correlationId)
                    .subscribe(
                            result -> log.info("High-value scope analysis completed: scopeId={}, healthScore={}",
                                    scopeId, result.getHealthScore()),
                            error -> log.warn("High-value scope analysis failed: scopeId={}", scopeId)
                    );
        }
    }

    /**
     * Call CAPSULE's pattern detection API.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "patternDetectionFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public Mono<PatternDetectionResponse> callCapsulePatternDetection(
            String scopeId,
            Set<CapsulePatternType> patternTypes,
            PerceptionSignalEvent event,
            String correlationId) {
        
        List<String> typeStrings = patternTypes.stream()
                .map(Enum::name)
                .toList();
        
        PatternDetectionRequest request = new PatternDetectionRequest(
                scopeId,
                "PT1H", // 1 hour lookback
                typeStrings,
                event.getConfidence(),
                true // use ML model
        );
        
        log.debug("Calling CAPSULE pattern detection: scopeId={}, types={}", scopeId, typeStrings);
        
        return capsuleWebClient.post()
                .uri("/api/v2/detection/patterns")
                .header("X-Correlation-ID", correlationId)
                .header("X-Source-Service", SERVICE_NAME)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PatternDetectionResponse.class)
                .timeout(defaultTimeout)
                .doOnError(e -> log.error("CAPSULE pattern detection error: {}", e.getMessage()));
    }

    /**
     * Call CAPSULE's anomaly detection API.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "anomalyDetectionFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public Mono<AnomalyDetectionResponse> callCapsuleAnomalyDetection(
            String scopeId,
            Set<CapsuleAnomalyType> anomalyTypes,
            PerceptionSignalEvent event,
            String correlationId) {
        
        List<String> typeStrings = anomalyTypes.stream()
                .map(Enum::name)
                .toList();
        
        AnomalyDetectionRequest request = new AnomalyDetectionRequest(
                null, // capsuleId - will use latest
                scopeId,
                "PT1H", // 1 hour lookback
                typeStrings,
                0.5, // threshold
                true // use ML model
        );
        
        log.debug("Calling CAPSULE anomaly detection: scopeId={}, types={}", scopeId, typeStrings);
        
        return capsuleWebClient.post()
                .uri("/api/v2/detection/anomalies")
                .header("X-Correlation-ID", correlationId)
                .header("X-Source-Service", SERVICE_NAME)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AnomalyDetectionResponse.class)
                .timeout(defaultTimeout)
                .doOnError(e -> log.error("CAPSULE anomaly detection error: {}", e.getMessage()));
    }

    /**
     * Call CAPSULE's full scope analysis API for high-value detections.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public Mono<ScopeAnalysisResponse> callCapsuleScopeAnalysis(
            String scopeId,
            String correlationId) {
        
        log.debug("Calling CAPSULE scope analysis: scopeId={}", scopeId);
        
        return capsuleWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v2/detection/analyze/{scopeId}")
                        .queryParam("lookbackPeriod", "PT24H")
                        .queryParam("includePatterns", true)
                        .queryParam("includeAnomalies", true)
                        .build(scopeId))
                .header("X-Correlation-ID", correlationId)
                .header("X-Source-Service", SERVICE_NAME)
                .retrieve()
                .bodyToMono(ScopeAnalysisResponse.class)
                .timeout(defaultTimeout.multipliedBy(2)); // Longer timeout for full analysis
    }

    // --- Fallback methods ---

    public Mono<PatternDetectionResponse> patternDetectionFallback(
            String scopeId,
            Set<CapsulePatternType> patternTypes,
            PerceptionSignalEvent event,
            String correlationId,
            Throwable throwable) {
        
        log.warn("Pattern detection fallback triggered: scopeId={}, error={}", scopeId, throwable.getMessage());
        return Mono.just(PatternDetectionResponse.degraded(scopeId, "Circuit breaker open or service unavailable"));
    }

    public Mono<AnomalyDetectionResponse> anomalyDetectionFallback(
            String scopeId,
            Set<CapsuleAnomalyType> anomalyTypes,
            PerceptionSignalEvent event,
            String correlationId,
            Throwable throwable) {
        
        log.warn("Anomaly detection fallback triggered: scopeId={}, error={}", scopeId, throwable.getMessage());
        return Mono.just(AnomalyDetectionResponse.degraded(scopeId, "Circuit breaker open or service unavailable"));
    }

    // --- Request/Response DTOs ---

    public record PatternDetectionRequest(
            String scopeId,
            String lookbackPeriod,
            List<String> patternTypes,
            double minConfidence,
            boolean useModel
    ) {}

    public record AnomalyDetectionRequest(
            String capsuleId,
            String scopeId,
            String lookbackPeriod,
            List<String> anomalyTypes,
            double threshold,
            boolean useModel
    ) {}

    public record PatternDetectionResponse(
            String scopeId,
            List<DetectedPattern> patterns,
            int patternsFound,
            Instant detectedAt,
            boolean degraded,
            String degradedReason
    ) {
        public static PatternDetectionResponse degraded(String scopeId, String reason) {
            return new PatternDetectionResponse(scopeId, Collections.emptyList(), 0, Instant.now(), true, reason);
        }
    }

    public record AnomalyDetectionResponse(
            String scopeId,
            boolean isAnomaly,
            List<DetectedAnomaly> anomalies,
            int anomaliesFound,
            double overallScore,
            Instant detectedAt,
            boolean degraded,
            String degradedReason
    ) {
        public static AnomalyDetectionResponse degraded(String scopeId, String reason) {
            return new AnomalyDetectionResponse(scopeId, false, Collections.emptyList(), 0, 0.0, Instant.now(), true, reason);
        }
    }

    public record ScopeAnalysisResponse(
            String scopeId,
            double healthScore,
            String status,
            int patternCount,
            int anomalyCount,
            Instant analyzedAt
    ) {
        public double getHealthScore() {
            return healthScore;
        }
    }

    public record DetectedPattern(
            String patternId,
            String type,
            double confidence,
            String description
    ) {}

    public record DetectedAnomaly(
            String anomalyId,
            String type,
            double score,
            String description
    ) {}
}

