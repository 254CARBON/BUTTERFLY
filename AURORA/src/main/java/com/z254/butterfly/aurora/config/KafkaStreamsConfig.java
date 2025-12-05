package com.z254.butterfly.aurora.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Streams configuration for AURORA service.
 * <p>
 * Configures Kafka Streams with:
 * <ul>
 *     <li>Schema Registry integration for Avro serialization</li>
 *     <li>Exactly-once processing semantics</li>
 *     <li>State store configuration for RCA windowing</li>
 *     <li>Error handling with DLQ routing</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    private final KafkaProperties kafkaProperties;
    private final AuroraProperties auroraProperties;

    public KafkaStreamsConfig(KafkaProperties kafkaProperties, AuroraProperties auroraProperties) {
        this.kafkaProperties = kafkaProperties;
        this.auroraProperties = auroraProperties;
    }

    /**
     * Kafka Streams configuration bean.
     * <p>
     * Key configurations:
     * <ul>
     *     <li>Exactly-once semantics for reliable processing</li>
     *     <li>Schema Registry for Avro serde</li>
     *     <li>Configurable stream threads</li>
     *     <li>State directory for local state stores</li>
     * </ul>
     */
    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfiguration() {
        Map<String, Object> props = new HashMap<>();
        
        AuroraProperties.Kafka.Streams streamsConfig = auroraProperties.getKafka().getStreams();
        
        // Basic configuration
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, streamsConfig.getApplicationId());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        
        // Default serdes
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class.getName());
        
        // Schema Registry
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, 
                auroraProperties.getKafka().getSchemaRegistryUrl());
        
        // Processing guarantee (exactly-once)
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, streamsConfig.getProcessingGuarantee());
        
        // Number of stream threads
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, streamsConfig.getNumStreamThreads());
        
        // Commit interval
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 
                streamsConfig.getCommitInterval().toMillis());
        
        // Replication factor for internal topics
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, streamsConfig.getReplicationFactor());
        
        // State directory
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/aurora-streams");
        
        // Consumer configuration
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        
        // Error handling - log and continue for deserialization errors
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class.getName());
        
        // Metrics
        props.put(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "INFO");
        
        // Add any additional properties
        props.putAll(streamsConfig.getAdditionalProperties());
        
        log.info("Initializing Kafka Streams with application.id={}, bootstrap.servers={}, processing.guarantee={}",
                streamsConfig.getApplicationId(),
                kafkaProperties.getBootstrapServers(),
                streamsConfig.getProcessingGuarantee());
        
        return new KafkaStreamsConfiguration(props);
    }

    /**
     * Schema Registry URL for Avro serdes.
     */
    @Bean
    public String schemaRegistryUrl() {
        return auroraProperties.getKafka().getSchemaRegistryUrl();
    }

    /**
     * Serde configuration map for Avro serdes.
     */
    @Bean
    public Map<String, Object> serdeConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, 
                auroraProperties.getKafka().getSchemaRegistryUrl());
        return config;
    }
}
