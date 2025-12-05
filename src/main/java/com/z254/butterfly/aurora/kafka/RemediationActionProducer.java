package com.z254.butterfly.aurora.kafka;

import com.z254.butterfly.aurora.avro.AuroraRemediationAction;
import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.remediation.HealingConnectorClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Publishes remediation actions for auditing as well as SYNAPSE fallback flows.
 */
@Slf4j
@Component
public class RemediationActionProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuroraProperties auroraProperties;

    public RemediationActionProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                     AuroraProperties auroraProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.auroraProperties = auroraProperties;
    }

    public void publish(HealingConnectorClient.HealingRequest request,
                        double blastRadius,
                        double riskScore,
                        boolean requiresApproval) {
        AuroraRemediationAction avro = mapToAvro(request, blastRadius, riskScore, requiresApproval);
        String topic = auroraProperties.getKafka().getTopics().getRemediationActions();

        kafkaTemplate.send(topic, request.getIncidentId(), avro)
                .whenComplete((RecordMetadata metadata, Throwable error) -> {
                    if (error != null) {
                        log.error("Failed to publish remediation action {}: {}",
                                request.getRemediationId(), error.getMessage());
                    } else if (metadata != null) {
                        log.debug("Published remediation action {} to partition {}",
                                request.getRemediationId(), metadata.partition());
                    }
                });
    }

    private AuroraRemediationAction mapToAvro(HealingConnectorClient.HealingRequest request,
                                              double blastRadius,
                                              double riskScore,
                                              boolean requiresApproval) {
        Map<String, String> parameters = request.getParameters() != null ?
                request.getParameters() : Collections.emptyMap();

        return AuroraRemediationAction.newBuilder()
                .setSchemaVersion("1.0.0")
                .setRemediationId(request.getRemediationId())
                .setIncidentId(request.getIncidentId())
                .setHypothesisId(request.getHypothesisId())
                .setTimestamp(Instant.now().toEpochMilli())
                .setActionType(request.getActionType())
                .setTargetComponent(request.getTargetComponent())
                .setParameters(parameters)
                .setExecutionMode(AuroraRemediationAction.ExecutionMode.valueOf(request.getExecutionMode().name()))
                .setPriority(0)
                .setBlastRadius(blastRadius)
                .setRiskScore(riskScore)
                .setPlatoPlanId(request.getPlatoPlanId())
                .setPlatoApprovalId(request.getPlatoApprovalId())
                .setRequiresApproval(requiresApproval)
                .setTimeoutMs(request.getTimeoutMs())
                .setMaxRetries(auroraProperties.getRemediation().getMaxRetryAttempts())
                .setSynapseToolId(buildToolId(request.getActionType()))
                .setCorrelationId(request.getCorrelationId())
                .setTenantId(null)
                .setMetadata(Collections.emptyMap())
                .build();
    }

    private String buildToolId(String actionType) {
        return auroraProperties.getRemediation().getHealingToolsPrefix() +
                actionType.toLowerCase().replace('_', '-');
    }
}
