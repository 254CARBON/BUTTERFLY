package com.z254.butterfly.aurora.kafka;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.ImmunityRule;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.immunity.ResiliencePatternLearner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka producer for chaos learning events.
 */
@Slf4j
@Component
public class ChaosLearningProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuroraProperties auroraProperties;

    public ChaosLearningProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                  AuroraProperties auroraProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.auroraProperties = auroraProperties;
    }

    /**
     * Emit a chaos learning event.
     */
    public void emit(ImmunityRule rule, Incident incident,
                     ResiliencePatternLearner.ResiliencePattern pattern) {
        String topic = auroraProperties.getKafka().getTopics().getChaosLearnings();

        Map<String, Object> event = new HashMap<>();
        event.put("learningId", UUID.randomUUID().toString());
        event.put("incidentId", incident.getId());
        event.put("ruleId", rule.getRuleId());
        event.put("patternType", pattern.getPatternType().name());
        event.put("component", rule.getComponent());
        event.put("anomalyType", rule.getAnomalyType());
        event.put("confidence", pattern.getConfidence());
        event.put("recommendedAction", rule.getRecommendedAction());
        event.put("parameters", rule.getActionParameters());
        event.put("validFrom", rule.getValidFrom().toEpochMilli());
        event.put("validUntil", rule.getValidUntil() != null ? 
                rule.getValidUntil().toEpochMilli() : null);
        event.put("mttrMs", incident.getMttrMs());
        event.put("timestamp", Instant.now().toEpochMilli());
        event.put("correlationId", incident.getCorrelationId());
        event.put("tenantId", incident.getTenantId());

        kafkaTemplate.send(topic, rule.getComponent(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to emit chaos learning event: ruleId={}, error={}",
                                rule.getRuleId(), ex.getMessage());
                    } else {
                        log.info("Emitted chaos learning event: ruleId={}, topic={}, partition={}",
                                rule.getRuleId(), topic,
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
