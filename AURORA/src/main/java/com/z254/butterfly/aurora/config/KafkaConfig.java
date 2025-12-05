package com.z254.butterfly.aurora.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for AURORA service producers and consumers.
 * <p>
 * Provides:
 * <ul>
 *     <li>Avro producer factory with idempotent configuration</li>
 *     <li>Avro consumer factory with manual acknowledgment</li>
 *     <li>Topic definitions for AURORA events</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final AuroraProperties auroraProperties;

    public KafkaConfig(KafkaProperties kafkaProperties, AuroraProperties auroraProperties) {
        this.kafkaProperties = kafkaProperties;
        this.auroraProperties = auroraProperties;
    }

    // ==================== Producer Configuration ====================

    /**
     * Avro producer factory with idempotent configuration.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        
        // Serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        
        // Schema Registry
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, 
                auroraProperties.getKafka().getSchemaRegistryUrl());
        props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        props.put(KafkaAvroSerializerConfig.USE_LATEST_VERSION, false);
        
        // Idempotent producer configuration
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // Retry configuration
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        
        // Batching for efficiency
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        // Compression
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Avro-enabled Kafka template.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        return template;
    }

    // ==================== Consumer Configuration ====================

    /**
     * Avro consumer factory with hardened configuration.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        
        // Deserializers
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        
        // Schema Registry
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, 
                auroraProperties.getKafka().getSchemaRegistryUrl());
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        
        // Consumer offset management
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Isolation level for transactional reads
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        // Fetch configuration
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        
        // Session and heartbeat
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka listener container factory with manual acknowledgment.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    // ==================== Admin Configuration ====================

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        return new KafkaAdmin(configs);
    }

    // ==================== Topic Definitions ====================

    @Bean
    public NewTopic anomaliesEnrichedTopic() {
        return TopicBuilder.name(auroraProperties.getKafka().getTopics().getAnomaliesEnriched())
                .partitions(6)
                .replicas(1)
                .config("retention.ms", "604800000") // 7 days
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic rcaHypothesesTopic() {
        return TopicBuilder.name(auroraProperties.getKafka().getTopics().getRcaHypotheses())
                .partitions(6)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 days
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic remediationActionsTopic() {
        return TopicBuilder.name(auroraProperties.getKafka().getTopics().getRemediationActions())
                .partitions(6)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 days
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic chaosLearningsTopic() {
        return TopicBuilder.name(auroraProperties.getKafka().getTopics().getChaosLearnings())
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "7776000000") // 90 days
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic incidentsDlqTopic() {
        return TopicBuilder.name(auroraProperties.getKafka().getTopics().getIncidentsDlq())
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "7776000000") // 90 days
                .config("cleanup.policy", "delete")
                .build();
    }
}
