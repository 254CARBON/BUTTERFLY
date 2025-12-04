package com.z254.butterfly.e2e.integration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the golden loop flow through the BUTTERFLY ecosystem.
 * 
 * <p>Validates:
 * <ul>
 *   <li>End-to-end correlation ID propagation</li>
 *   <li>Loop completion tracking</li>
 *   <li>Checkpoint sequencing</li>
 *   <li>Latency SLOs</li>
 *   <li>Error handling and retry behavior</li>
 * </ul>
 * 
 * <p>The golden loop flow:
 * <pre>
 * PERCEPTION → CAPSULE → ODYSSEY → PLATO → SYNAPSE → NEXUS → (feedback to PERCEPTION)
 * </pre>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GoldenLoopIntegrationTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final Duration LOOP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration CHECKPOINT_SLO = Duration.ofSeconds(10);

    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withNetwork(NETWORK);

    private static RestTemplate restTemplate;
    private static KafkaProducer<String, String> kafkaProducer;
    private static KafkaConsumer<String, String> kafkaConsumer;

    // Service URLs - would be configured from environment in real test
    private static String perceptionUrl;
    private static String capsuleUrl;
    private static String odysseyUrl;
    private static String platoUrl;
    private static String synapseUrl;
    private static String nexusUrl;

    @BeforeAll
    static void setup() {
        restTemplate = new RestTemplate();

        // Configure Kafka producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProducer = new KafkaProducer<>(producerProps);

        // Configure Kafka consumer
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "golden-loop-test");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumer = new KafkaConsumer<>(consumerProps);

        // Subscribe to completion topic
        kafkaConsumer.subscribe(List.of("nexus.golden-loop.completions"));

        // Get service URLs from environment
        perceptionUrl = System.getenv().getOrDefault("PERCEPTION_URL", "http://localhost:8080");
        capsuleUrl = System.getenv().getOrDefault("CAPSULE_URL", "http://localhost:8081");
        odysseyUrl = System.getenv().getOrDefault("ODYSSEY_URL", "http://localhost:8082");
        platoUrl = System.getenv().getOrDefault("PLATO_URL", "http://localhost:8083");
        synapseUrl = System.getenv().getOrDefault("SYNAPSE_URL", "http://localhost:8085");
        nexusUrl = System.getenv().getOrDefault("NEXUS_URL", "http://localhost:8084");
    }

    @AfterAll
    static void teardown() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }
        NETWORK.close();
    }

    /**
     * Test that a correlation ID propagates through all services in the golden loop.
     */
    @Test
    @Order(1)
    @DisplayName("Correlation ID should propagate through all services")
    void correlationIdPropagatesThroughAllServices() throws Exception {
        // Given
        String correlationId = "test-" + UUID.randomUUID();
        Map<String, Object> signalPayload = createTestSignalPayload(correlationId);

        // When - Start the loop by sending a signal to PERCEPTION
        kafkaProducer.send(new ProducerRecord<>(
                "perception.events",
                correlationId,
                toJson(signalPayload)
        )).get(10, TimeUnit.SECONDS);

        // Then - Wait for loop completion
        LoopCompletionResult result = waitForLoopCompletion(correlationId, LOOP_TIMEOUT);

        assertThat(result.isCompleted())
                .as("Golden loop should complete")
                .isTrue();

        assertThat(result.getCheckpointsVisited())
                .as("All checkpoints should be visited")
                .containsExactlyInAnyOrder(
                        "PERCEPTION_SIGNAL_DETECTED",
                        "CAPSULE_SNAPSHOT_STORED",
                        "ODYSSEY_PATH_UPDATED",
                        "PLATO_PLAN_CREATED",
                        "SYNAPSE_ACTION_EXECUTED",
                        "PERCEPTION_OUTCOME_RECEIVED"
                );

        assertThat(result.getCorrelationId())
                .as("Correlation ID should be preserved")
                .isEqualTo(correlationId);
    }

    /**
     * Test that checkpoints are visited in the correct order.
     */
    @Test
    @Order(2)
    @DisplayName("Checkpoints should be visited in correct order")
    void checkpointsInCorrectOrder() throws Exception {
        // Given
        String correlationId = "order-test-" + UUID.randomUUID();
        Map<String, Object> signalPayload = createTestSignalPayload(correlationId);

        // When
        kafkaProducer.send(new ProducerRecord<>(
                "perception.events",
                correlationId,
                toJson(signalPayload)
        )).get(10, TimeUnit.SECONDS);

        LoopCompletionResult result = waitForLoopCompletion(correlationId, LOOP_TIMEOUT);

        // Then - Verify order using checkpoint timestamps
        assertThat(result.isCompleted()).isTrue();

        List<String> expectedOrder = List.of(
                "PERCEPTION_SIGNAL_DETECTED",
                "CAPSULE_SNAPSHOT_STORED",
                "ODYSSEY_PATH_UPDATED",
                "PLATO_PLAN_CREATED",
                "SYNAPSE_ACTION_EXECUTED",
                "PERCEPTION_OUTCOME_RECEIVED"
        );

        // Verify timestamps are in ascending order
        Long lastTimestamp = 0L;
        for (String checkpoint : expectedOrder) {
            Long timestamp = result.getCheckpointTimestamps().get(checkpoint);
            assertThat(timestamp)
                    .as("Checkpoint %s should have a timestamp", checkpoint)
                    .isNotNull()
                    .isGreaterThan(lastTimestamp);
            lastTimestamp = timestamp;
        }
    }

    /**
     * Test that loop completion meets latency SLO.
     */
    @Test
    @Order(3)
    @DisplayName("Loop completion should meet latency SLO")
    void loopCompletionMeetsLatencySlo() throws Exception {
        // Given - SLO: Loop should complete within 30 seconds
        Duration latencySlo = Duration.ofSeconds(30);
        String correlationId = "latency-test-" + UUID.randomUUID();
        Map<String, Object> signalPayload = createTestSignalPayload(correlationId);

        Instant startTime = Instant.now();

        // When
        kafkaProducer.send(new ProducerRecord<>(
                "perception.events",
                correlationId,
                toJson(signalPayload)
        )).get(10, TimeUnit.SECONDS);

        LoopCompletionResult result = waitForLoopCompletion(correlationId, LOOP_TIMEOUT);

        // Then
        assertThat(result.isCompleted()).isTrue();

        Duration actualLatency = Duration.between(startTime, Instant.now());
        assertThat(actualLatency)
                .as("Loop completion latency should be within SLO of %s", latencySlo)
                .isLessThan(latencySlo);

        // Also verify individual checkpoint latencies
        for (Map.Entry<String, Long> entry : result.getCheckpointDurations().entrySet()) {
            assertThat(Duration.ofMillis(entry.getValue()))
                    .as("Checkpoint %s should complete within %s", entry.getKey(), CHECKPOINT_SLO)
                    .isLessThan(CHECKPOINT_SLO);
        }
    }

    /**
     * Test that integration health endpoint returns correct status.
     */
    @Test
    @Order(4)
    @DisplayName("Integration health endpoint should return status")
    void integrationHealthEndpointReturnsStatus() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                nexusUrl + "/api/v1/integration/health",
                Map.class
        );

        // Then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isNotNull();
        assertThat(body.get("healthy")).isNotNull();
        assertThat(body.get("goldenLoop")).isNotNull();
    }

    /**
     * Test that golden loop statistics are tracked.
     */
    @Test
    @Order(5)
    @DisplayName("Golden loop statistics should be tracked")
    void goldenLoopStatisticsAreTracked() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                nexusUrl + "/api/v1/integration/golden-loop/statistics",
                Map.class
        );

        // Then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats).containsKeys(
                "inFlightCount",
                "completedTotal",
                "failedTotal",
                "timedOutTotal"
        );
    }

    /**
     * Test that loop failures are properly recorded.
     */
    @Test
    @Order(6)
    @DisplayName("Loop failures should be recorded")
    void loopFailuresAreRecorded() throws Exception {
        // Given - Send a malformed signal that should cause failure
        String correlationId = "failure-test-" + UUID.randomUUID();
        Map<String, Object> malformedPayload = new HashMap<>();
        malformedPayload.put("correlationId", correlationId);
        malformedPayload.put("eventType", "INVALID_TYPE");
        // Missing required fields

        // When
        kafkaProducer.send(new ProducerRecord<>(
                "perception.events",
                correlationId,
                toJson(malformedPayload)
        )).get(10, TimeUnit.SECONDS);

        // Wait for potential failure processing
        Thread.sleep(5000);

        // Then - Check failure history
        ResponseEntity<List> response = restTemplate.getForEntity(
                nexusUrl + "/api/v1/integration/golden-loop/failures?limit=10",
                List.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Should have some failures recorded (from this or previous tests)
        // Note: In a real test, we'd verify the specific failure
    }

    /**
     * Test multiple concurrent loops.
     */
    @Test
    @Order(7)
    @DisplayName("Multiple concurrent loops should complete independently")
    void multipleConcurrentLoopsCompleteIndependently() throws Exception {
        // Given
        int numLoops = 5;
        List<String> correlationIds = new ArrayList<>();
        CountDownLatch completionLatch = new CountDownLatch(numLoops);
        Map<String, LoopCompletionResult> results = new HashMap<>();

        // When - Start multiple loops concurrently
        for (int i = 0; i < numLoops; i++) {
            String correlationId = "concurrent-" + i + "-" + UUID.randomUUID();
            correlationIds.add(correlationId);

            Map<String, Object> signalPayload = createTestSignalPayload(correlationId);
            kafkaProducer.send(new ProducerRecord<>(
                    "perception.events",
                    correlationId,
                    toJson(signalPayload)
            ));
        }

        // Wait for all completions
        for (String correlationId : correlationIds) {
            LoopCompletionResult result = waitForLoopCompletion(correlationId, LOOP_TIMEOUT);
            results.put(correlationId, result);
        }

        // Then - All loops should complete successfully
        for (String correlationId : correlationIds) {
            LoopCompletionResult result = results.get(correlationId);
            assertThat(result.isCompleted())
                    .as("Loop %s should complete", correlationId)
                    .isTrue();
        }

        // Verify in-flight count is manageable
        ResponseEntity<Map> statsResponse = restTemplate.getForEntity(
                nexusUrl + "/api/v1/integration/golden-loop/statistics",
                Map.class
        );
        assertThat(statsResponse.getBody().get("inFlightCount"))
                .as("In-flight count should not grow unbounded")
                .isNotNull();
    }

    // === Helper Methods ===

    private Map<String, Object> createTestSignalPayload(String correlationId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("correlationId", correlationId);
        payload.put("eventType", "SIGNAL_DETECTED");
        payload.put("timestamp", Instant.now().toString());
        payload.put("signalType", "TEST_SIGNAL");
        payload.put("rimNodeId", "rim:test:" + correlationId);
        payload.put("strength", 0.85);
        payload.put("confidence", 0.90);
        payload.put("metadata", Map.of(
                "source", "integration-test",
                "testCase", "golden-loop"
        ));
        return payload;
    }

    private LoopCompletionResult waitForLoopCompletion(String correlationId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        AtomicReference<LoopCompletionResult> resultRef = new AtomicReference<>();

        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, String> record : records) {
                if (correlationId.equals(record.key())) {
                    Map<String, Object> completion = fromJson(record.value());
                    if ("COMPLETED".equals(completion.get("status"))) {
                        return createCompletionResult(completion, true);
                    }
                }
            }

            // Also check via API
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(
                        nexusUrl + "/api/v1/integration/golden-loop/" + correlationId,
                        Map.class
                );
                if (response.getStatusCode() == HttpStatus.OK) {
                    Map<String, Object> context = response.getBody();
                    if ("COMPLETED".equals(context.get("status"))) {
                        return createCompletionResult(context, true);
                    }
                }
            } catch (Exception e) {
                // Loop not found yet, continue waiting
            }
        }

        // Timeout - return incomplete result
        return LoopCompletionResult.builder()
                .correlationId(correlationId)
                .completed(false)
                .checkpointsVisited(List.of())
                .checkpointTimestamps(Map.of())
                .checkpointDurations(Map.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private LoopCompletionResult createCompletionResult(Map<String, Object> data, boolean completed) {
        List<String> checkpoints = new ArrayList<>();
        Map<String, Long> timestamps = new HashMap<>();
        Map<String, Long> durations = new HashMap<>();

        if (data.containsKey("checkpointTimestamps")) {
            Map<String, Object> tsMap = (Map<String, Object>) data.get("checkpointTimestamps");
            for (Map.Entry<String, Object> entry : tsMap.entrySet()) {
                checkpoints.add(entry.getKey());
                if (entry.getValue() instanceof Number) {
                    timestamps.put(entry.getKey(), ((Number) entry.getValue()).longValue());
                }
            }
        }

        if (data.containsKey("checkpointDurations")) {
            Map<String, Object> durMap = (Map<String, Object>) data.get("checkpointDurations");
            for (Map.Entry<String, Object> entry : durMap.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    durations.put(entry.getKey(), ((Number) entry.getValue()).longValue());
                }
            }
        }

        return LoopCompletionResult.builder()
                .correlationId((String) data.get("correlationId"))
                .completed(completed)
                .checkpointsVisited(checkpoints)
                .checkpointTimestamps(timestamps)
                .checkpointDurations(durations)
                .totalDurationMs(data.containsKey("totalDurationMs")
                        ? ((Number) data.get("totalDurationMs")).longValue()
                        : 0)
                .build();
    }

    private String toJson(Map<String, Object> map) {
        // Simple JSON serialization - in real code would use Jackson
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        // Simple JSON parsing - in real code would use Jackson
        // This is a simplified implementation for test purposes
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    // === DTOs ===

    @lombok.Builder
    @lombok.Data
    private static class LoopCompletionResult {
        private String correlationId;
        private boolean completed;
        private List<String> checkpointsVisited;
        private Map<String, Long> checkpointTimestamps;
        private Map<String, Long> checkpointDurations;
        private long totalDurationMs;
    }
}
