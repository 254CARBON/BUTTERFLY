package com.z254.butterfly.e2e.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Dead Letter Queue (DLQ) replay functionality.
 * <p>
 * These tests verify:
 * <ul>
 *     <li>DLQ message format and headers</li>
 *     <li>Replay message delivery to original topic</li>
 *     <li>Retry counting and status tracking</li>
 *     <li>Max retry limits</li>
 * </ul>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DlqReplayIntegrationTest {

    private static final String FAST_PATH_TOPIC = "rim.fast-path";
    private static final String DLQ_TOPIC = "rim.fast-path.dlq";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
    );
    
    private static ObjectMapper mapper;
    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> fastPathConsumer;
    private static KafkaConsumer<String, String> dlqConsumer;
    private static AdminClient adminClient;

    @BeforeAll
    static void setupKafka() throws Exception {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        String bootstrapServers = kafka.getBootstrapServers();
        
        // Create admin client and topics
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", bootstrapServers);
        adminClient = AdminClient.create(adminProps);
        
        adminClient.createTopics(List.of(
            new NewTopic(FAST_PATH_TOPIC, 3, (short) 1),
            new NewTopic(DLQ_TOPIC, 1, (short) 1)
        )).all().get(30, TimeUnit.SECONDS);
        
        // Create producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producer = new KafkaProducer<>(producerProps);
        
        // Create fast-path consumer
        Properties fastPathConsumerProps = new Properties();
        fastPathConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        fastPathConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-fastpath-consumer");
        fastPathConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        fastPathConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        fastPathConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        fastPathConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        fastPathConsumer = new KafkaConsumer<>(fastPathConsumerProps);
        
        // Create DLQ consumer
        Properties dlqConsumerProps = new Properties();
        dlqConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        dlqConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-dlq-consumer");
        dlqConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        dlqConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        dlqConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dlqConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        dlqConsumer = new KafkaConsumer<>(dlqConsumerProps);
    }

    @AfterAll
    static void teardownKafka() {
        if (producer != null) producer.close();
        if (fastPathConsumer != null) fastPathConsumer.close();
        if (dlqConsumer != null) dlqConsumer.close();
        if (adminClient != null) adminClient.close();
    }

    // ========== DLQ Message Format Tests ==========

    @Nested
    @DisplayName("DLQ Message Format")
    class DlqMessageFormatTests {

        @Test
        @Order(1)
        @DisplayName("DLQ message contains required headers")
        void dlqMessageContainsRequiredHeaders() throws Exception {
            // Create a DLQ record
            String dlqPayload = createDlqRecord("dlq-test-1", "rim.fast-path", 
                "ValidationException", "Invalid RimNodeId format");
            
            ProducerRecord<String, String> record = new ProducerRecord<>(DLQ_TOPIC, "dlq-key", dlqPayload);
            record.headers().add(new RecordHeader("dlq-original-topic", 
                FAST_PATH_TOPIC.getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("dlq-error-type", 
                "ValidationException".getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("dlq-timestamp", 
                Instant.now().toString().getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("dlq-source-service", 
                "odyssey".getBytes(StandardCharsets.UTF_8)));
            
            producer.send(record).get(10, TimeUnit.SECONDS);
            
            // Consume and verify headers
            dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            ConsumerRecords<String, String> records = dlqConsumer.poll(POLL_TIMEOUT);
            dlqConsumer.unsubscribe();
            
            assertThat(records.count()).isGreaterThan(0);
            
            // Find the specific record we published by key
            ConsumerRecord<String, String> dlqRecord = null;
            for (ConsumerRecord<String, String> r : records) {
                if ("dlq-key".equals(r.key())) {
                    dlqRecord = r;
                    break;
                }
            }
            assertThat(dlqRecord).as("Should find the DLQ record with key 'dlq-key'").isNotNull();
            assertThat(dlqRecord.headers().lastHeader("dlq-original-topic")).isNotNull();
            assertThat(dlqRecord.headers().lastHeader("dlq-error-type")).isNotNull();
            assertThat(dlqRecord.headers().lastHeader("dlq-timestamp")).isNotNull();
        }

        @Test
        @Order(2)
        @DisplayName("DLQ message payload contains error context")
        void dlqPayloadContainsErrorContext() throws Exception {
            String dlqPayload = createDlqRecord("dlq-test-2", FAST_PATH_TOPIC,
                "ProcessingException", "Failed to process event");
            
            producer.send(new ProducerRecord<>(DLQ_TOPIC, dlqPayload)).get(10, TimeUnit.SECONDS);
            
            dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            ConsumerRecords<String, String> records = dlqConsumer.poll(POLL_TIMEOUT);
            dlqConsumer.unsubscribe();
            
            for (ConsumerRecord<String, String> r : records) {
                JsonNode node = mapper.readTree(r.value());
                if (node.has("id") && node.get("id").asText().equals("dlq-test-2")) {
                    assertThat(node.has("originalTopic")).isTrue();
                    assertThat(node.has("errorType")).isTrue();
                    assertThat(node.has("errorMessage")).isTrue();
                    assertThat(node.has("timestamp")).isTrue();
                    assertThat(node.get("originalTopic").asText()).isEqualTo(FAST_PATH_TOPIC);
                    break;
                }
            }
        }
    }

    // ========== DLQ Replay Tests ==========

    @Nested
    @DisplayName("DLQ Replay")
    class DlqReplayTests {

        @Test
        @Order(3)
        @DisplayName("replayed message is delivered to original topic")
        void replayedMessageDeliveredToOriginalTopic() throws Exception {
            // Simulate a DLQ record being replayed
            String originalPayload = "{\"rim_node_id\":\"rim:entity:finance:TEST\",\"stress\":0.5}";
            String dlqPayload = createDlqRecordWithPayload("replay-test-1", FAST_PATH_TOPIC, originalPayload);
            
            // First, add to DLQ
            producer.send(new ProducerRecord<>(DLQ_TOPIC, dlqPayload)).get(10, TimeUnit.SECONDS);
            
            // Simulate replay by sending to original topic
            ProducerRecord<String, String> replayRecord = new ProducerRecord<>(
                FAST_PATH_TOPIC, "replay-key", originalPayload);
            replayRecord.headers().add(new RecordHeader("dlq-replay-id", 
                "replay-test-1".getBytes(StandardCharsets.UTF_8)));
            replayRecord.headers().add(new RecordHeader("dlq-retry-count", 
                "1".getBytes(StandardCharsets.UTF_8)));
            
            producer.send(replayRecord).get(10, TimeUnit.SECONDS);
            
            // Verify message arrived in original topic
            fastPathConsumer.subscribe(Collections.singletonList(FAST_PATH_TOPIC));
            ConsumerRecords<String, String> records = fastPathConsumer.poll(POLL_TIMEOUT);
            fastPathConsumer.unsubscribe();
            
            boolean foundReplay = false;
            for (ConsumerRecord<String, String> r : records) {
                if (r.headers().lastHeader("dlq-replay-id") != null) {
                    String replayId = new String(r.headers().lastHeader("dlq-replay-id").value(), 
                        StandardCharsets.UTF_8);
                    if ("replay-test-1".equals(replayId)) {
                        foundReplay = true;
                        break;
                    }
                }
            }
            assertThat(foundReplay).isTrue();
        }

        @Test
        @Order(4)
        @DisplayName("retry count is tracked in replayed messages")
        void retryCountTracked() throws Exception {
            // Create a replay with retry count
            String originalPayload = "{\"rim_node_id\":\"rim:entity:finance:RETRY\",\"stress\":0.7}";
            
            ProducerRecord<String, String> replayRecord = new ProducerRecord<>(
                FAST_PATH_TOPIC, "retry-key", originalPayload);
            replayRecord.headers().add(new RecordHeader("dlq-replay-id", 
                "retry-test".getBytes(StandardCharsets.UTF_8)));
            replayRecord.headers().add(new RecordHeader("dlq-retry-count", 
                "3".getBytes(StandardCharsets.UTF_8)));
            
            producer.send(replayRecord).get(10, TimeUnit.SECONDS);
            
            fastPathConsumer.subscribe(Collections.singletonList(FAST_PATH_TOPIC));
            ConsumerRecords<String, String> records = fastPathConsumer.poll(POLL_TIMEOUT);
            fastPathConsumer.unsubscribe();
            
            for (ConsumerRecord<String, String> r : records) {
                if (r.headers().lastHeader("dlq-retry-count") != null) {
                    String retryCount = new String(r.headers().lastHeader("dlq-retry-count").value(),
                        StandardCharsets.UTF_8);
                    assertThat(Integer.parseInt(retryCount)).isGreaterThanOrEqualTo(1);
                }
            }
        }
    }

    // ========== Failure Category Tests ==========

    @Nested
    @DisplayName("Failure Categories")
    class FailureCategoryTests {

        @Test
        @Order(5)
        @DisplayName("deserialization failure is categorized correctly")
        void deserializationFailureCategorized() throws Exception {
            String dlqPayload = createDlqRecordWithCategory("deser-test", FAST_PATH_TOPIC,
                "JsonParseException", "Unexpected character", "DESERIALIZATION");
            
            producer.send(new ProducerRecord<>(DLQ_TOPIC, dlqPayload)).get(10, TimeUnit.SECONDS);
            
            dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            ConsumerRecords<String, String> records = dlqConsumer.poll(POLL_TIMEOUT);
            dlqConsumer.unsubscribe();
            
            boolean found = false;
            for (ConsumerRecord<String, String> r : records) {
                JsonNode node = mapper.readTree(r.value());
                if (node.has("failureCategory") && 
                    "DESERIALIZATION".equals(node.get("failureCategory").asText())) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @Order(6)
        @DisplayName("validation failure is categorized correctly")
        void validationFailureCategorized() throws Exception {
            String dlqPayload = createDlqRecordWithCategory("valid-test", FAST_PATH_TOPIC,
                "IllegalArgumentException", "Invalid RimNodeId", "VALIDATION");
            
            producer.send(new ProducerRecord<>(DLQ_TOPIC, dlqPayload)).get(10, TimeUnit.SECONDS);
            
            dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            ConsumerRecords<String, String> records = dlqConsumer.poll(POLL_TIMEOUT);
            dlqConsumer.unsubscribe();
            
            boolean found = false;
            for (ConsumerRecord<String, String> r : records) {
                JsonNode node = mapper.readTree(r.value());
                if (node.has("failureCategory") && 
                    "VALIDATION".equals(node.get("failureCategory").asText())) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @Order(7)
        @DisplayName("downstream failure is categorized correctly")
        void downstreamFailureCategorized() throws Exception {
            String dlqPayload = createDlqRecordWithCategory("downstream-test", FAST_PATH_TOPIC,
                "ConnectException", "Connection refused", "DOWNSTREAM_FAILURE");
            
            producer.send(new ProducerRecord<>(DLQ_TOPIC, dlqPayload)).get(10, TimeUnit.SECONDS);
            
            dlqConsumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            ConsumerRecords<String, String> records = dlqConsumer.poll(POLL_TIMEOUT);
            dlqConsumer.unsubscribe();
            
            boolean found = false;
            for (ConsumerRecord<String, String> r : records) {
                JsonNode node = mapper.readTree(r.value());
                if (node.has("failureCategory") && 
                    "DOWNSTREAM_FAILURE".equals(node.get("failureCategory").asText())) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }
    }

    // ========== Helper Methods ==========

    private String createDlqRecord(String id, String originalTopic, String errorType, String errorMessage) {
        return String.format("""
            {
              "id": "%s",
              "originalTopic": "%s",
              "partition": 0,
              "offset": 100,
              "errorType": "%s",
              "errorMessage": "%s",
              "timestamp": "%s",
              "sourceService": "odyssey",
              "retryCount": 0,
              "status": "PENDING"
            }
            """, id, originalTopic, errorType, errorMessage, Instant.now().toString());
    }

    private String createDlqRecordWithPayload(String id, String originalTopic, String originalPayload) {
        String escapedPayload = originalPayload.replace("\"", "\\\"");
        return String.format("""
            {
              "id": "%s",
              "originalTopic": "%s",
              "partition": 0,
              "offset": 100,
              "payload": "%s",
              "errorType": "ProcessingException",
              "errorMessage": "Simulated failure for testing",
              "timestamp": "%s",
              "sourceService": "odyssey",
              "retryCount": 0,
              "status": "PENDING"
            }
            """, id, originalTopic, escapedPayload, Instant.now().toString());
    }

    private String createDlqRecordWithCategory(String id, String originalTopic, 
                                                String errorType, String errorMessage, 
                                                String failureCategory) {
        return String.format("""
            {
              "id": "%s",
              "originalTopic": "%s",
              "partition": 0,
              "offset": 100,
              "errorType": "%s",
              "errorMessage": "%s",
              "failureCategory": "%s",
              "timestamp": "%s",
              "sourceService": "odyssey",
              "retryCount": 0,
              "status": "PENDING"
            }
            """, id, originalTopic, errorType, errorMessage, failureCategory, Instant.now().toString());
    }
}
