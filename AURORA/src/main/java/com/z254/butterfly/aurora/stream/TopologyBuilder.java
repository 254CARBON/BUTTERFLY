package com.z254.butterfly.aurora.stream;

import com.z254.butterfly.aurora.config.AuroraProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.Topology;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Kafka Streams topology builder and manager.
 * <p>
 * Responsible for:
 * <ul>
 *     <li>Building and initializing the stream topology</li>
 *     <li>Providing topology description for debugging</li>
 *     <li>Managing state stores</li>
 * </ul>
 */
@Slf4j
@Component
public class TopologyBuilder {

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;
    private final AuroraProperties auroraProperties;

    public TopologyBuilder(StreamsBuilderFactoryBean streamsBuilderFactoryBean,
                           AuroraProperties auroraProperties) {
        this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
        this.auroraProperties = auroraProperties;
    }

    @PostConstruct
    public void logTopology() {
        try {
            Topology topology = streamsBuilderFactoryBean.getTopology();
            if (topology != null) {
                log.info("AURORA Kafka Streams Topology:\n{}", topology.describe());
            }
        } catch (Exception e) {
            log.warn("Could not log topology description: {}", e.getMessage());
        }
    }

    /**
     * Get the current topology description.
     */
    public String getTopologyDescription() {
        try {
            Topology topology = streamsBuilderFactoryBean.getTopology();
            return topology != null ? topology.describe().toString() : "Topology not available";
        } catch (Exception e) {
            return "Error getting topology: " + e.getMessage();
        }
    }

    /**
     * Get topic configuration summary.
     */
    public String getTopicConfiguration() {
        AuroraProperties.Kafka.Topics topics = auroraProperties.getKafka().getTopics();
        return String.format("""
                AURORA Topic Configuration:
                  Input:
                    - anomalies-input: %s
                  Internal:
                    - anomalies-enriched: %s
                  Output:
                    - rca-hypotheses: %s
                    - remediation-actions: %s
                    - chaos-learnings: %s
                  Error:
                    - incidents-dlq: %s
                """,
                topics.getAnomaliesInput(),
                topics.getAnomaliesEnriched(),
                topics.getRcaHypotheses(),
                topics.getRemediationActions(),
                topics.getChaosLearnings(),
                topics.getIncidentsDlq()
        );
    }

    /**
     * Get streams configuration summary.
     */
    public String getStreamsConfiguration() {
        AuroraProperties.Kafka.Streams streams = auroraProperties.getKafka().getStreams();
        return String.format("""
                AURORA Streams Configuration:
                  application-id: %s
                  num-stream-threads: %d
                  processing-guarantee: %s
                  commit-interval: %s
                  replication-factor: %d
                """,
                streams.getApplicationId(),
                streams.getNumStreamThreads(),
                streams.getProcessingGuarantee(),
                streams.getCommitInterval(),
                streams.getReplicationFactor()
        );
    }
}
