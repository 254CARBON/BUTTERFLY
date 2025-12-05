package com.z254.butterfly.aurora.stream;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.z254.butterfly.aurora.domain.model.AnomalySignal;
import com.z254.butterfly.aurora.observability.AuroraMetrics;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.util.*;

/**
 * Kafka Streams processor for anomaly events.
 * <p>
 * Builds the stream topology for:
 * <ul>
 *     <li>Consuming anomaly events from PERCEPTION</li>
 *     <li>Windowed aggregation for correlation</li>
 *     <li>Enrichment with context</li>
 *     <li>Routing to RCA engine</li>
 * </ul>
 */
@Slf4j
@Component
public class AnomalyStreamProcessor {

    private final AuroraProperties auroraProperties;
    private final EnrichmentPipeline enrichmentPipeline;
    private final AuroraMetrics metrics;
    private final AuroraStructuredLogger logger;
    private final IncidentIngestionService incidentIngestionService;
    private final Map<String, Object> serdeConfig;

    // State store names
    public static final String ANOMALY_WINDOW_STORE = "anomaly-window-store";
    public static final String INCIDENT_STATE_STORE = "incident-state-store";

    public AnomalyStreamProcessor(AuroraProperties auroraProperties,
                                   EnrichmentPipeline enrichmentPipeline,
                                   AuroraMetrics metrics,
                                   AuroraStructuredLogger logger,
                                   IncidentIngestionService incidentIngestionService,
                                   Map<String, Object> serdeConfig) {
        this.auroraProperties = auroraProperties;
        this.enrichmentPipeline = enrichmentPipeline;
        this.metrics = metrics;
        this.logger = logger;
        this.incidentIngestionService = incidentIngestionService;
        this.serdeConfig = serdeConfig;
    }

    /**
     * Build the Kafka Streams topology for anomaly processing.
     */
    @Autowired
    public void buildPipeline(StreamsBuilder builder) {
        log.info("Building AURORA anomaly stream topology");

        AuroraProperties.Kafka.Topics topics = auroraProperties.getKafka().getTopics();
        Duration correlationWindow = auroraProperties.getRca().getCorrelationWindow();
        Duration gracePeriod = auroraProperties.getRca().getGracePeriod();

        // Create Avro Serdes
        SpecificAvroSerde<Object> anomalySerde = new SpecificAvroSerde<>();
        anomalySerde.configure(serdeConfig, false);

        // Add state stores
        builder.addStateStore(
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(INCIDENT_STATE_STORE),
                        Serdes.String(),
                        Serdes.String()
                )
        );

        JsonSerde<EnrichedAnomalyBatch> enrichedSerde = new JsonSerde<>(EnrichedAnomalyBatch.class);
        enrichedSerde.deserializer().addTrustedPackages("*");

        // Source: Consume anomaly events from PERCEPTION
        KStream<String, Object> anomalyStream = builder
                .stream(topics.getAnomaliesInput(), 
                        Consumed.with(Serdes.String(), anomalySerde)
                                .withName("anomaly-source"));

        // Step 1: Log and record metrics for each anomaly
        KStream<String, Object> loggedStream = anomalyStream
                .peek((key, value) -> {
                    metrics.recordAnomalyProcessed();
                    log.debug("Received anomaly event: key={}", key);
                });

        // Step 2: Group by component/rimNodeId for correlation
        KGroupedStream<String, Object> groupedByComponent = loggedStream
                .selectKey((key, value) -> extractGroupKey(value))
                .groupByKey(Grouped.with(Serdes.String(), anomalySerde));

        // Step 3: Window anomalies for correlation
        TimeWindowedKStream<String, Object> windowedAnomalies = groupedByComponent
                .windowedBy(TimeWindows.ofSizeAndGrace(correlationWindow, gracePeriod));

        // Step 4: Aggregate anomalies within window
        KTable<Windowed<String>, AnomalyAggregate> aggregatedAnomalies = windowedAnomalies
                .aggregate(
                        AnomalyAggregate::new,
                        (key, value, aggregate) -> aggregate.add(value),
                        Materialized.with(Serdes.String(), new AnomalyAggregateSerde())
                );

        // Step 5: Filter for correlation threshold
        KStream<Windowed<String>, AnomalyAggregate> correlatedAnomalies = aggregatedAnomalies
                .toStream()
                .filter((windowedKey, aggregate) -> 
                        aggregate != null && 
                        aggregate.getCount() >= auroraProperties.getRca().getMinAnomaliesForRca());

        // Step 6: Enrich with context
        KStream<String, EnrichedAnomalyBatch> enrichedStream = correlatedAnomalies
                .map((windowedKey, aggregate) -> {
                    String key = windowedKey.key();
                    EnrichedAnomalyBatch enriched = enrichmentPipeline.enrich(key, aggregate);
                    metrics.recordAnomalyEnriched();
                    return KeyValue.pair(key, enriched);
                });

        // Step 7: Output to enriched topic for RCA
        enrichedStream
                .peek((key, batch) -> incidentIngestionService.process(batch))
                .to(topics.getAnomaliesEnriched(),
                        Produced.with(Serdes.String(), enrichedSerde)
                                .withName("enriched-anomaly-sink"));

        // Error handling: DLQ for failed processing
        // This is handled at the infrastructure level via DeserializationExceptionHandler

        log.info("AURORA anomaly stream topology built successfully");
    }

    /**
     * Extract grouping key from anomaly event.
     * Groups by component to correlate related anomalies.
     */
    private String extractGroupKey(Object anomalyEvent) {
        if (anomalyEvent instanceof org.apache.avro.generic.GenericRecord record) {
            Object rimNodeId = record.get("rimNodeId");
            if (rimNodeId != null) {
                return rimNodeId.toString();
            }
        }
        return "unknown";
    }

    /**
     * Aggregator for windowed anomalies.
     */
    public static class AnomalyAggregate {
        private final List<AnomalySignal> anomalies = new ArrayList<>();
        private double maxSeverity = 0.0;
        private Set<String> affectedComponents = new HashSet<>();
        private long firstTimestamp = Long.MAX_VALUE;
        private long lastTimestamp = 0;

        public AnomalyAggregate add(Object anomaly) {
            if (anomaly instanceof org.apache.avro.generic.GenericRecord record) {
                AnomalySignal signal = AnomalySignal.fromRecord(record);
                if (signal != null) {
                    anomalies.add(signal);
                    maxSeverity = Math.max(maxSeverity, signal.getSeverity());
                    affectedComponents.addAll(signal.getAffectedComponents());
                    firstTimestamp = Math.min(firstTimestamp, signal.getTimestamp());
                    lastTimestamp = Math.max(lastTimestamp, signal.getTimestamp());
                }
            }
            return this;
        }

        public int getCount() {
            return anomalies.size();
        }

        public List<AnomalySignal> getAnomalies() {
            return anomalies;
        }

        public double getMaxSeverity() {
            return maxSeverity;
        }

        public Set<String> getAffectedComponents() {
            return affectedComponents;
        }

        public long getDurationMs() {
            return lastTimestamp - firstTimestamp;
        }
    }

    /**
     * Serde for AnomalyAggregate.
     */
    public static class AnomalyAggregateSerde extends Serdes.WrapperSerde<AnomalyAggregate> {
        public AnomalyAggregateSerde() {
            super(new AnomalyAggregateSerializer(), new AnomalyAggregateDeserializer());
        }
    }

    // Placeholder serializers - would be properly implemented in production
    public static class AnomalyAggregateSerializer implements org.apache.kafka.common.serialization.Serializer<AnomalyAggregate> {
        private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

        @Override
        public byte[] serialize(String topic, AnomalyAggregate data) {
            if (data == null) {
                return null;
            }
            try {
                return MAPPER.writeValueAsBytes(data);
            } catch (JsonProcessingException e) {
                throw new org.apache.kafka.common.errors.SerializationException("Failed to serialize aggregate", e);
            }
        }
    }

    public static class AnomalyAggregateDeserializer implements org.apache.kafka.common.serialization.Deserializer<AnomalyAggregate> {
        private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

        @Override
        public AnomalyAggregate deserialize(String topic, byte[] data) {
            if (data == null || data.length == 0) {
                return new AnomalyAggregate();
            }
            try {
                return MAPPER.readValue(data, AnomalyAggregate.class);
            } catch (Exception e) {
                throw new org.apache.kafka.common.errors.SerializationException("Failed to deserialize aggregate", e);
            }
        }
    }
}
