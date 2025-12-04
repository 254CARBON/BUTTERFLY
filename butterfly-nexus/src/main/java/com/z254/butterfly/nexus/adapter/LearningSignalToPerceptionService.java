package com.z254.butterfly.nexus.adapter;

import com.z254.butterfly.nexus.client.PerceptionClient;
import com.z254.butterfly.nexus.domain.evolution.LearningSignal;
import com.z254.butterfly.nexus.kafka.LearningSignalPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service that routes CAPSULE learning signals back to PERCEPTION for trust score updates.
 * 
 * <p>Implements the learning feedback loop per CAPSULE/ROADMAP.md Milestone 4.3:
 * <ul>
 *   <li>PREDICTION_ACCURACY - Calibrates trust based on prediction outcomes</li>
 *   <li>TRUST_SCORE_UPDATE - Direct trust score adjustments</li>
 *   <li>PATTERN_VALIDATION - Validates detected patterns against actual outcomes</li>
 * </ul>
 * 
 * <p>This service consumes from:
 * <ul>
 *   <li>{@code capsule.detections} - CAPSULE detection events</li>
 *   <li>{@code nexus.learning.outcomes} - Learning outcome events</li>
 * </ul>
 * 
 * <p>And updates PERCEPTION via:
 * <ul>
 *   <li>PUT /api/v1/integrity/trust/{sourceId} - Trust score updates</li>
 * </ul>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "nexus.learning-signal-loop", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LearningSignalToPerceptionService {

    private static final String SERVICE_NAME = "nexus-learning-signal";
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;
    private static final double TRUST_ADJUSTMENT_FACTOR = 0.1;

    private final PerceptionClient perceptionClient;
    private final LearningSignalPublisher learningSignalPublisher;
    
    // Metrics
    private final Counter trustUpdatesCounter;
    private final Counter predictionAccuracyCounter;
    private final Counter learningSignalsProcessedCounter;
    private final Counter learningSignalsFailedCounter;
    private final Timer signalProcessingTimer;

    public LearningSignalToPerceptionService(
            PerceptionClient perceptionClient,
            LearningSignalPublisher learningSignalPublisher,
            MeterRegistry meterRegistry) {
        
        this.perceptionClient = perceptionClient;
        this.learningSignalPublisher = learningSignalPublisher;
        
        this.trustUpdatesCounter = Counter.builder("nexus.learning.trust_updates")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        this.predictionAccuracyCounter = Counter.builder("nexus.learning.prediction_accuracy")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        this.learningSignalsProcessedCounter = Counter.builder("nexus.learning.signals.processed")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        this.learningSignalsFailedCounter = Counter.builder("nexus.learning.signals.failed")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        this.signalProcessingTimer = Timer.builder("nexus.learning.signal.processing.time")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);
        
        log.info("Initialized LearningSignalToPerceptionService");
    }

    /**
     * Consume CAPSULE detection events and generate learning signals.
     */
    @KafkaListener(
            topics = "${capsule.detections.topic:capsule.detections}",
            groupId = "${nexus.learning-signal-loop.consumer-group:nexus-learning-signal}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCapsuleDetectionEvent(ConsumerRecord<String, CapsuleDetectionEvent> record) {
        CapsuleDetectionEvent event = record.value();
        
        log.debug("Received CAPSULE detection event: detectionId={}, scopeId={}, type={}",
                event.detectionId(), event.scopeId(), event.detectionType());
        
        Timer.Sample sample = Timer.start();
        
        try {
            processDetectionForLearning(event);
            learningSignalsProcessedCounter.increment();
            sample.stop(signalProcessingTimer);
        } catch (Exception e) {
            sample.stop(signalProcessingTimer);
            learningSignalsFailedCounter.increment();
            log.error("Failed to process detection for learning: detectionId={}, error={}",
                    event.detectionId(), e.getMessage(), e);
        }
    }

    /**
     * Consume learning outcome events and propagate trust updates.
     */
    @KafkaListener(
            topics = "${nexus.learning.outcomes.topic:nexus.learning.outcomes}",
            groupId = "${nexus.learning-signal-loop.consumer-group:nexus-learning-signal}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onLearningOutcomeEvent(ConsumerRecord<String, LearningOutcomeEvent> record) {
        LearningOutcomeEvent event = record.value();
        
        log.debug("Received learning outcome event: signalId={}, success={}",
                event.signalId(), event.success());
        
        Timer.Sample sample = Timer.start();
        
        try {
            processLearningOutcome(event);
            learningSignalsProcessedCounter.increment();
            sample.stop(signalProcessingTimer);
        } catch (Exception e) {
            sample.stop(signalProcessingTimer);
            learningSignalsFailedCounter.increment();
            log.error("Failed to process learning outcome: signalId={}, error={}",
                    event.signalId(), e.getMessage(), e);
        }
    }

    /**
     * Process a detection event and generate trust updates.
     */
    private void processDetectionForLearning(CapsuleDetectionEvent event) {
        String scopeId = event.scopeId();
        double confidence = event.confidence();
        String sourcePerceptionEventId = event.sourcePerceptionEventId();
        
        // Only process high-confidence detections for learning
        if (confidence < HIGH_CONFIDENCE_THRESHOLD) {
            log.debug("Skipping low-confidence detection for learning: confidence={}", confidence);
            return;
        }
        
        // If this detection originated from PERCEPTION, update trust for the source
        if (sourcePerceptionEventId != null) {
            // High-confidence CAPSULE validation increases trust in the PERCEPTION source
            double trustAdjustment = TRUST_ADJUSTMENT_FACTOR * confidence;
            updatePerceptionTrust(scopeId, sourcePerceptionEventId, trustAdjustment);
            
            // Generate PREDICTION_ACCURACY learning signal
            generatePredictionAccuracySignal(event, true);
        }
        
        // For anomaly detections, potentially decrease trust
        if ("ANOMALY".equals(event.detectionType()) && event.severity() != null) {
            String severity = event.severity();
            if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                // High-severity anomalies may indicate trust issues
                generateTrustCalibrationSignal(event);
            }
        }
    }

    /**
     * Process a learning outcome and update PERCEPTION trust.
     */
    private void processLearningOutcome(LearningOutcomeEvent event) {
        // Only process outcomes targeting PERCEPTION
        if (!"PERCEPTION".equals(event.targetSystem())) {
            return;
        }
        
        // Extract source ID from signal details
        String sourceId = event.signalId();
        boolean success = event.success();
        
        // Adjust trust based on outcome
        double trustAdjustment = success ? TRUST_ADJUSTMENT_FACTOR : -TRUST_ADJUSTMENT_FACTOR;
        
        // Use sourceSystem as the node ID since we're feeding back to the source
        String nodeId = event.sourceSystem().toLowerCase() + "_node";
        
        updatePerceptionTrust(nodeId, sourceId, trustAdjustment);
        
        // Publish outcome for observability
        log.info("Applied learning outcome to PERCEPTION: signalId={}, success={}, trustAdjustment={}",
                event.signalId(), success, trustAdjustment);
    }

    /**
     * Update trust score in PERCEPTION.
     */
    private void updatePerceptionTrust(String nodeId, String sourceId, double trustAdjustment) {
        // Calculate new trust (bounded 0-1)
        double newTrust = Math.max(0.0, Math.min(1.0, 0.5 + trustAdjustment));
        
        perceptionClient.updateTrustScore(nodeId, sourceId, newTrust)
                .doOnSuccess(v -> {
                    trustUpdatesCounter.increment();
                    log.debug("Trust score updated in PERCEPTION: nodeId={}, sourceId={}, newTrust={}",
                            nodeId, sourceId, newTrust);
                })
                .doOnError(e -> log.error("Failed to update trust in PERCEPTION: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * Generate a PREDICTION_ACCURACY learning signal.
     */
    private void generatePredictionAccuracySignal(CapsuleDetectionEvent event, boolean accurate) {
        String signalId = UUID.randomUUID().toString();
        
        LearningSignal signal = LearningSignal.builder()
                .signalId(signalId)
                .sourceSystem("CAPSULE")
                .targetSystem("PERCEPTION")
                .signalType(LearningSignal.SignalType.CONFIDENCE_CALIBRATION)
                .payload(Map.of(
                        "detectionId", event.detectionId(),
                        "scopeId", event.scopeId(),
                        "detectionType", event.detectionType(),
                        "confidence", event.confidence(),
                        "accurate", accurate
                ))
                .confidence(event.confidence())
                .timestamp(Instant.now())
                .applied(false)
                .build();
        
        // Publish for tracking (fire-and-forget with error logging)
        learningSignalPublisher.publishLearningOutcome(
                signalId, signal, accurate, "Detection validation from CAPSULE"
        ).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Failed to publish learning outcome for signal {}: {}", signalId, error.getMessage());
            }
        });
        
        predictionAccuracyCounter.increment();
        
        log.info("Generated PREDICTION_ACCURACY signal: signalId={}, scopeId={}, accurate={}",
                signalId, event.scopeId(), accurate);
    }

    /**
     * Generate a trust calibration signal for anomaly detections.
     */
    private void generateTrustCalibrationSignal(CapsuleDetectionEvent event) {
        String signalId = UUID.randomUUID().toString();
        
        LearningSignal signal = LearningSignal.builder()
                .signalId(signalId)
                .sourceSystem("CAPSULE")
                .targetSystem("PERCEPTION")
                .signalType(LearningSignal.SignalType.CONFIDENCE_CALIBRATION)
                .payload(Map.of(
                        "detectionId", event.detectionId(),
                        "scopeId", event.scopeId(),
                        "anomalyType", event.anomalyType() != null ? event.anomalyType() : "unknown",
                        "severity", event.severity(),
                        "calibrationDirection", "decrease"
                ))
                .confidence(event.confidence())
                .timestamp(Instant.now())
                .applied(false)
                .build();
        
        // Publish for tracking (fire-and-forget with error logging)
        learningSignalPublisher.publishLearningOutcome(
                signalId, signal, false, "Anomaly-based trust calibration"
        ).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Failed to publish trust calibration signal {}: {}", signalId, error.getMessage());
            }
        });
        
        log.info("Generated trust calibration signal: signalId={}, scopeId={}, severity={}",
                signalId, event.scopeId(), event.severity());
    }

    // --- DTO Records ---

    public record CapsuleDetectionEvent(
            String detectionId,
            String detectionType,
            String patternType,
            String anomalyType,
            String scopeId,
            String capsuleId,
            Instant timestamp,
            int resolution,
            String severity,
            double confidence,
            String description,
            String sourcePerceptionEventId,
            String correlationId
    ) {}

    public record LearningOutcomeEvent(
            String signalId,
            String sourceSystem,
            String targetSystem,
            String signalType,
            boolean success,
            String details,
            Instant timestamp
    ) {}
}

