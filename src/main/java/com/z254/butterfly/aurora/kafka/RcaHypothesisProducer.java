package com.z254.butterfly.aurora.kafka;

import com.z254.butterfly.aurora.avro.AuroraRcaHypothesis;
import com.z254.butterfly.aurora.avro.AuroraRcaHypothesis.AnalysisMetrics;
import com.z254.butterfly.aurora.avro.AuroraRcaHypothesis.SuggestedRemediation;
import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Publishes RCA hypotheses to the configured Kafka topic for downstream consumers (PLATO, NEXUS).
 */
@Slf4j
@Component
public class RcaHypothesisProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuroraProperties auroraProperties;

    public RcaHypothesisProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                 AuroraProperties auroraProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.auroraProperties = auroraProperties;
    }

    public void publish(RcaHypothesis hypothesis) {
        if (hypothesis == null) {
            return;
        }

        AuroraRcaHypothesis avro = mapToAvro(hypothesis);
        String topic = auroraProperties.getKafka().getTopics().getRcaHypotheses();

        kafkaTemplate.send(topic, hypothesis.getIncidentId(), avro)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish RCA hypothesis {}: {}",
                                hypothesis.getHypothesisId(), error.getMessage());
                    } else if (result != null) {
                        log.debug("Published RCA hypothesis {} to partition {}",
                                hypothesis.getHypothesisId(), result.getRecordMetadata().partition());
                    }
                });
    }

    private AuroraRcaHypothesis mapToAvro(RcaHypothesis hypothesis) {
        List<SuggestedRemediation> remediations = hypothesis.getSuggestedRemediations().stream()
                .map(rem -> SuggestedRemediation.newBuilder()
                        .setActionType(rem.getActionType())
                        .setTargetComponent(rem.getTargetComponent())
                        .setDescription(rem.getDescription())
                        .setPriority(rem.getPriority())
                        .setEstimatedImpact(rem.getEstimatedImpact())
                        .setParameters(rem.getParameters())
                        .build())
                .collect(Collectors.toList());

        AnalysisMetrics metrics = hypothesis.getAnalysisMetrics() != null ?
                AnalysisMetrics.newBuilder()
                        .setAnalysisTimeMs(hypothesis.getAnalysisMetrics().getAnalysisTimeMs())
                        .setAnomaliesAnalyzed(hypothesis.getAnalysisMetrics().getAnomaliesAnalyzed())
                        .setComponentsEvaluated(hypothesis.getAnalysisMetrics().getComponentsEvaluated())
                        .setTopologyDepth(hypothesis.getAnalysisMetrics().getTopologyDepth())
                        .build() :
                AnalysisMetrics.newBuilder()
                        .setAnalysisTimeMs(0L)
                        .setAnomaliesAnalyzed(0)
                        .setComponentsEvaluated(0)
                        .setTopologyDepth(0)
                        .build();

        Map<String, String> metadata = hypothesis.getMetadata() != null ?
                hypothesis.getMetadata() : Map.of();

        return AuroraRcaHypothesis.newBuilder()
                .setSchemaVersion("1.0.0")
                .setHypothesisId(hypothesis.getHypothesisId())
                .setIncidentId(hypothesis.getIncidentId())
                .setTimestamp(hypothesis.getTimestamp() != null ?
                        hypothesis.getTimestamp().toEpochMilli() : Instant.now().toEpochMilli())
                .setRootCauseComponent(hypothesis.getRootCauseComponent())
                .setRootCauseType(hypothesis.getRootCauseType())
                .setConfidence(hypothesis.getConfidence())
                .setRank(hypothesis.getRank())
                .setSupportingEvidence(hypothesis.getSupportingEvidence())
                .setAnomalyIds(hypothesis.getAnomalyIds())
                .setAffectedComponents(hypothesis.getAffectedComponents().stream().toList())
                .setCausalChain(hypothesis.getCausalChain())
                .setSuggestedRemediations(remediations)
                .setAnalysisMetrics(metrics)
                .setCorrelationId(hypothesis.getCorrelationId())
                .setTenantId(hypothesis.getTenantId())
                .setMetadata(metadata)
                .build();
    }
}
