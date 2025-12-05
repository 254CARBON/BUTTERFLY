package com.z254.butterfly.aurora.kafka;

import com.z254.butterfly.aurora.avro.AuroraChaosLearning;
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

        AuroraChaosLearning avro = AuroraChaosLearning.newBuilder()
                .setSchemaVersion("1.0.0")
                .setLearningId(UUID.randomUUID().toString())
                .setIncidentId(incident.getId())
                .setTimestamp(Instant.now().toEpochMilli())
                .setPatternType(pattern.getPatternType().name())
                .setComponent(rule.getComponent())
                .setAnomalyType(rule.getAnomalyType())
                .setImmunityRule(buildRuleRecord(rule))
                .setConfidence(pattern.getConfidence())
                .setEffectiveness(rule.getEffectivenessRate())
                .setAppliedCount(rule.getAppliedCount())
                .setSuccessCount(rule.getSuccessCount())
                .setSourcePattern(buildSourcePattern(pattern, incident))
                .setValidFrom(rule.getValidFrom().toEpochMilli())
                .setValidUntil(rule.getValidUntil() != null ? rule.getValidUntil().toEpochMilli() : null)
                .setCorrelationId(incident.getCorrelationId())
                .setTenantId(incident.getTenantId())
                .setMetadata(new HashMap<>())
                .build();

        kafkaTemplate.send(topic, rule.getComponent(), avro)
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

    private AuroraChaosLearning.ImmunityRule buildRuleRecord(ImmunityRule rule) {
        return AuroraChaosLearning.ImmunityRule.newBuilder()
                .setRuleId(rule.getRuleId())
                .setCondition(rule.getTriggerCondition())
                .setAction(rule.getRecommendedAction())
                .setParameters(rule.getActionParameters())
                .setPriority(rule.getPriority())
                .setCooldownMs(rule.getCooldownMs())
                .build();
    }

    private AuroraChaosLearning.SourcePattern buildSourcePattern(ResiliencePatternLearner.ResiliencePattern pattern,
                                                                 Incident incident) {
        return AuroraChaosLearning.SourcePattern.newBuilder()
                .setIncidentTimeline(Collections.emptyList())
                .setRemediationsTried(Collections.emptyList())
                .setSuccessfulRemediation(pattern.getRecommendedAction())
                .setMttrMs(incident.getMttrMs() != null ? incident.getMttrMs() : 0)
                .setRecurrenceCount(0)
                .build();
    }
}
