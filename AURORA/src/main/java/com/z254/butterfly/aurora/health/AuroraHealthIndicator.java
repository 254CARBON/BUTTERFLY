package com.z254.butterfly.aurora.health;

import com.z254.butterfly.aurora.config.AuroraProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Health indicator for AURORA service.
 * <p>
 * Reports on:
 * <ul>
 *     <li>Kafka Streams state</li>
 *     <li>Active incidents and remediations</li>
 *     <li>Integration client status</li>
 * </ul>
 */
@Slf4j
@Component
public class AuroraHealthIndicator implements ReactiveHealthIndicator {

    private final StreamsBuilderFactoryBean streamsBuilder;
    private final AuroraProperties auroraProperties;
    
    // Mutable state tracked by the health indicator
    private final AtomicInteger activeIncidents = new AtomicInteger(0);
    private final AtomicInteger activeRemediations = new AtomicInteger(0);
    private final AtomicInteger anomaliesInWindow = new AtomicInteger(0);

    public AuroraHealthIndicator(StreamsBuilderFactoryBean streamsBuilder, 
                                  AuroraProperties auroraProperties) {
        this.streamsBuilder = streamsBuilder;
        this.auroraProperties = auroraProperties;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(this::checkHealth);
    }

    private Health checkHealth() {
        Map<String, Object> details = new HashMap<>();
        boolean healthy = true;

        // Check Kafka Streams state
        try {
            KafkaStreams kafkaStreams = streamsBuilder.getKafkaStreams();
            if (kafkaStreams != null) {
                KafkaStreams.State state = kafkaStreams.state();
                details.put("kafkaStreams.state", state.name());
                details.put("kafkaStreams.applicationId", 
                        auroraProperties.getKafka().getStreams().getApplicationId());
                
                if (state != KafkaStreams.State.RUNNING && state != KafkaStreams.State.REBALANCING) {
                    healthy = false;
                    details.put("kafkaStreams.error", "Kafka Streams not running: " + state);
                }
            } else {
                details.put("kafkaStreams.state", "NOT_STARTED");
            }
        } catch (Exception e) {
            healthy = false;
            details.put("kafkaStreams.error", "Failed to get Kafka Streams state: " + e.getMessage());
            log.error("Health check failed for Kafka Streams", e);
        }

        // Add operational metrics
        details.put("activeIncidents", activeIncidents.get());
        details.put("activeRemediations", activeRemediations.get());
        details.put("anomaliesInWindow", anomaliesInWindow.get());
        
        // Check safety limits
        int maxRemediations = auroraProperties.getSafety().getMaxConcurrentRemediations();
        if (activeRemediations.get() >= maxRemediations) {
            details.put("remediationCapacity", "AT_LIMIT");
        } else {
            details.put("remediationCapacity", "AVAILABLE");
        }
        details.put("maxConcurrentRemediations", maxRemediations);

        // Add configuration info
        details.put("rcaCorrelationWindow", auroraProperties.getRca().getCorrelationWindow().toString());
        details.put("safetyDryRunDefault", auroraProperties.getSafety().isDryRunDefault());

        if (healthy) {
            return Health.up()
                    .withDetails(details)
                    .build();
        } else {
            return Health.down()
                    .withDetails(details)
                    .build();
        }
    }

    // Methods to update health state
    public void incrementActiveIncidents() {
        activeIncidents.incrementAndGet();
    }

    public void decrementActiveIncidents() {
        activeIncidents.decrementAndGet();
    }

    public void incrementActiveRemediations() {
        activeRemediations.incrementAndGet();
    }

    public void decrementActiveRemediations() {
        activeRemediations.decrementAndGet();
    }

    public void setAnomaliesInWindow(int count) {
        anomaliesInWindow.set(count);
    }

    public boolean canStartRemediation() {
        return activeRemediations.get() < auroraProperties.getSafety().getMaxConcurrentRemediations();
    }
}
