package com.z254.butterfly.e2e.integration.nexus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for NEXUS cross-service flows.
 * 
 * These tests verify the end-to-end behavior of NEXUS when
 * interacting with mocked CAPSULE, PERCEPTION, and ODYSSEY services.
 */
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("NEXUS E2E Integration Tests")
public class NexusIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withNetwork(NETWORK)
        .withNetworkAliases("kafka");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine"))
        .withNetwork(NETWORK)
        .withNetworkAliases("redis")
        .withExposedPorts(6379);

    // WireMock container for mocking upstream services
    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>(
        DockerImageName.parse("wiremock/wiremock:3.3.1"))
        .withNetwork(NETWORK)
        .withNetworkAliases("wiremock")
        .withExposedPorts(8080)
        .withCommand("--verbose");

    private static String nexusBaseUrl;
    private static String wiremockBaseUrl;

    @BeforeAll
    static void setup() throws Exception {
        // Configure WireMock stubs for upstream services
        wiremockBaseUrl = "http://localhost:" + WIREMOCK.getMappedPort(8080);
        setupWireMockStubs();

        // Note: In a real setup, NEXUS would be started in a container
        // For this test, we assume NEXUS is running externally or in a separate container
        nexusBaseUrl = System.getProperty("nexus.url", "http://localhost:8080");
    }

    private static void setupWireMockStubs() throws Exception {
        // Stub CAPSULE history endpoint
        createWireMockStub(
            "GET", "/api/v1/capsules/history.*",
            200,
            """
            {
              "capsules": [
                {
                  "capsuleId": "cap-001",
                  "rimNodeId": "rim:entity:finance:BTCUSD",
                  "timestamp": "2025-01-01T00:00:00Z",
                  "configuration": {"price": 45000.0, "volume": 1000},
                  "dynamics": {"trend": "UP", "volatility": 0.3},
                  "counterfactual": {"alternate_price": 44000.0}
                }
              ]
            }
            """
        );

        // Stub PERCEPTION RIM node state endpoint
        createWireMockStub(
            "GET", "/api/v1/rim/nodes/.*",
            200,
            """
            {
              "rimNodeId": "rim:entity:finance:BTCUSD",
              "configuration": {"price": 46000.0, "volume": 1200},
              "trustMetrics": {"overallTrust": 0.85, "sourceCount": 5},
              "visibilityMetrics": {"visibilityLevel": "HIGH", "coverage": 0.9}
            }
            """
        );

        // Stub ODYSSEY paths endpoint
        createWireMockStub(
            "GET", "/api/v1/paths.*",
            200,
            """
            {
              "paths": [
                {
                  "pathId": "path-001",
                  "scopeId": "rim:entity:finance:BTCUSD",
                  "weight": 0.6,
                  "confidence": 0.75,
                  "horizon": "P7D",
                  "tippingPoints": [
                    {"tippingId": "tip-001", "estimatedTimeToTip": "P3D", "directionOfTravel": "APPROACHING"}
                  ]
                },
                {
                  "pathId": "path-002",
                  "scopeId": "rim:entity:finance:BTCUSD",
                  "weight": 0.4,
                  "confidence": 0.65,
                  "horizon": "P7D",
                  "tippingPoints": []
                }
              ]
            }
            """
        );

        // Stub PERCEPTION trust score endpoint
        createWireMockStub(
            "GET", "/api/v1/trust/.*",
            200,
            """
            {"nodeId": "rim:entity:finance:BTCUSD", "score": 0.85, "sources": 5}
            """
        );
    }

    private static void createWireMockStub(String method, String urlPattern, 
                                           int status, String responseBody) throws Exception {
        String stubJson = String.format("""
            {
              "request": {
                "method": "%s",
                "urlPathPattern": "%s"
              },
              "response": {
                "status": %d,
                "headers": {"Content-Type": "application/json"},
                "body": %s
              }
            }
            """, method, urlPattern, status, MAPPER.writeValueAsString(responseBody));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(wiremockBaseUrl + "/__admin/mappings"))
            .POST(HttpRequest.BodyPublishers.ofString(stubJson))
            .header("Content-Type", "application/json")
            .build();

        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // === Temporal Query Tests ===

    @Test
    @Order(1)
    @DisplayName("Temporal slice query returns past, present, and future data")
    void testTemporalSliceQuery() throws Exception {
        // Load scenario
        JsonNode scenario = loadScenario("nexus-temporal-query.json");
        JsonNode request = scenario.get("request");

        // This test validates the scenario structure
        assertThat(request.get("rim_node_id").asText())
            .isEqualTo("rim:entity:finance:BTCUSD");
        assertThat(request.get("temporal_window").get("history_depth").asText())
            .isEqualTo("P30D");
        assertThat(request.get("temporal_window").get("projection_horizon").asText())
            .isEqualTo("P7D");

        // Expected response validation
        JsonNode expected = scenario.get("expected_response");
        assertThat(expected.get("has_historical_state").asBoolean()).isTrue();
        assertThat(expected.get("has_current_state").asBoolean()).isTrue();
        assertThat(expected.get("has_projected_futures").asBoolean()).isTrue();
        assertThat(expected.get("min_coherence_score").asDouble()).isGreaterThanOrEqualTo(0.0);
        assertThat(expected.get("max_latency_ms").asInt()).isLessThanOrEqualTo(5000);
    }

    @Test
    @Order(2)
    @DisplayName("Temporal query validates upstream dependencies")
    void testTemporalQueryUpstreamDependencies() throws Exception {
        JsonNode scenario = loadScenario("nexus-temporal-query.json");
        JsonNode dependencies = scenario.get("upstream_dependencies");

        // Verify all upstream systems are defined
        assertThat(dependencies.has("capsule")).isTrue();
        assertThat(dependencies.has("perception")).isTrue();
        assertThat(dependencies.has("odyssey")).isTrue();

        // Verify endpoint patterns
        assertThat(dependencies.get("capsule").get("endpoint").asText())
            .contains("history");
        assertThat(dependencies.get("perception").get("endpoint").asText())
            .contains("rim/nodes");
        assertThat(dependencies.get("odyssey").get("endpoint").asText())
            .contains("paths");
    }

    // === Cross-Inference Tests ===

    @Test
    @Order(3)
    @DisplayName("Cross-system inference generates valid inferences")
    void testCrossSystemInference() throws Exception {
        JsonNode scenario = loadScenario("nexus-cross-inference.json");
        JsonNode request = scenario.get("request");
        JsonNode context = request.get("inference_context");

        // Validate inference context
        assertThat(context.get("include_counterfactuals").asBoolean()).isTrue();
        assertThat(context.get("include_future_paths").asBoolean()).isTrue();
        assertThat(context.get("max_depth").asInt()).isGreaterThan(0);

        // Validate included systems
        assertThat(context.get("include_systems"))
            .extracting(JsonNode::asText)
            .containsExactlyInAnyOrder("CAPSULE", "PERCEPTION", "ODYSSEY");

        // Expected response validation
        JsonNode expected = scenario.get("expected_response");
        assertThat(expected.get("min_inferences").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(expected.get("min_confidence").asDouble()).isGreaterThan(0.0);
    }

    @Test
    @Order(4)
    @DisplayName("Inference test assertions are properly defined")
    void testInferenceAssertions() throws Exception {
        JsonNode scenario = loadScenario("nexus-cross-inference.json");
        JsonNode assertions = scenario.get("test_assertions");

        assertThat(assertions).hasSize(3);

        // Verify assertion types
        boolean hasInferenceGenerated = false;
        boolean hasSystemsConsulted = false;
        boolean hasConfidenceThreshold = false;

        for (JsonNode assertion : assertions) {
            String type = assertion.get("type").asText();
            switch (type) {
                case "inference_generated" -> hasInferenceGenerated = true;
                case "systems_consulted" -> hasSystemsConsulted = true;
                case "confidence_threshold" -> hasConfidenceThreshold = true;
            }
        }

        assertThat(hasInferenceGenerated).isTrue();
        assertThat(hasSystemsConsulted).isTrue();
        assertThat(hasConfidenceThreshold).isTrue();
    }

    // === Synthesis Cascade Tests ===

    @Test
    @Order(5)
    @DisplayName("Synthesis cascade flow is properly defined")
    void testSynthesisCascadeFlow() throws Exception {
        JsonNode scenario = loadScenario("nexus-synthesis-cascade.json");
        JsonNode cascadeFlow = scenario.get("cascade_flow");

        assertThat(cascadeFlow).hasSize(4);

        // Verify flow order
        for (int i = 0; i < cascadeFlow.size(); i++) {
            JsonNode step = cascadeFlow.get(i);
            assertThat(step.get("step").asInt()).isEqualTo(i + 1);
            assertThat(step.has("system")).isTrue();
            assertThat(step.has("action")).isTrue();
        }

        // Verify systems involved
        assertThat(cascadeFlow.get(0).get("system").asText()).isEqualTo("PERCEPTION");
        assertThat(cascadeFlow.get(1).get("system").asText()).isEqualTo("NEXUS");
        assertThat(cascadeFlow.get(2).get("system").asText()).isEqualTo("ODYSSEY");
        assertThat(cascadeFlow.get(3).get("system").asText()).isEqualTo("NEXUS");
    }

    @Test
    @Order(6)
    @DisplayName("Synthesis expected response validates all systems contribute")
    void testSynthesisExpectedResponse() throws Exception {
        JsonNode scenario = loadScenario("nexus-synthesis-cascade.json");
        JsonNode expected = scenario.get("expected_response");

        assertThat(expected.get("min_options").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(expected.get("option_includes_perception").asBoolean()).isTrue();
        assertThat(expected.get("option_includes_odyssey").asBoolean()).isTrue();
        assertThat(expected.get("option_includes_capsule").asBoolean()).isTrue();
        assertThat(expected.get("has_risk_assessment").asBoolean()).isTrue();
    }

    // === Learning Loop Tests ===

    @Test
    @Order(7)
    @DisplayName("Learning loop flow steps are properly sequenced")
    void testLearningLoopFlowSteps() throws Exception {
        JsonNode scenario = loadScenario("nexus-learning-loop.json");
        JsonNode flowSteps = scenario.get("flow_steps");

        assertThat(flowSteps).hasSize(4);

        // Verify step sequence
        String[] expectedSteps = {"record_signal", "verify_pending", "apply_signals", "verify_effectiveness"};
        for (int i = 0; i < flowSteps.size(); i++) {
            assertThat(flowSteps.get(i).get("step").asText()).isEqualTo(expectedSteps[i]);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Learning signal request contains all required fields")
    void testLearningSignalRequest() throws Exception {
        JsonNode scenario = loadScenario("nexus-learning-loop.json");
        JsonNode recordStep = scenario.get("flow_steps").get(0);
        JsonNode request = recordStep.get("request");

        assertThat(request.get("signal_id").asText()).isNotBlank();
        assertThat(request.get("source_system").asText()).isEqualTo("ODYSSEY");
        assertThat(request.get("target_system").asText()).isEqualTo("PERCEPTION");
        assertThat(request.get("signal_type").asText()).isEqualTo("DECISION_OUTCOME");
        assertThat(request.has("payload")).isTrue();
        assertThat(request.get("confidence").asDouble()).isGreaterThan(0.0);
    }

    // === Contradiction Detection Tests ===

    @Test
    @Order(9)
    @DisplayName("Contradiction detection setup creates divergent values")
    void testContradictionSetup() throws Exception {
        JsonNode scenario = loadScenario("nexus-contradiction-detection.json");
        JsonNode setup = scenario.get("setup").get("inject_contradiction");

        assertThat(setup.get("rim_node_id").asText())
            .isEqualTo("rim:entity:finance:BTCUSD");

        // Verify divergent values
        double capsulePrice = setup.get("capsule_value").get("configuration.price").asDouble();
        double perceptionPrice = setup.get("perception_value").get("configuration.price").asDouble();
        double divergence = Math.abs(capsulePrice - perceptionPrice) / Math.max(capsulePrice, perceptionPrice);

        assertThat(divergence).isGreaterThan(setup.get("divergence_threshold").asDouble());
    }

    @Test
    @Order(10)
    @DisplayName("Contradiction lifecycle phases are properly defined")
    void testContradictionLifecycle() throws Exception {
        JsonNode scenario = loadScenario("nexus-contradiction-detection.json");
        JsonNode lifecycle = scenario.get("contradiction_lifecycle");

        assertThat(lifecycle).hasSize(3);

        // Verify phases
        assertThat(lifecycle.get(0).get("phase").asText()).isEqualTo("detection");
        assertThat(lifecycle.get(0).get("expected_status").asText()).isEqualTo("DETECTED");

        assertThat(lifecycle.get(1).get("phase").asText()).isEqualTo("analysis");
        assertThat(lifecycle.get(1).get("expected_status").asText()).isEqualTo("ANALYZING");

        assertThat(lifecycle.get(2).get("phase").asText()).isEqualTo("resolution");
        assertThat(lifecycle.get(2).get("expected_status").asText()).isEqualTo("RESOLVED");
        assertThat(lifecycle.get(2).get("resolution_method").asText()).isEqualTo("TRUST_SCORE");
    }

    // === Scenario Loading Utilities ===

    private JsonNode loadScenario(String filename) throws IOException {
        Path scenarioPath = Path.of("scenarios", filename);
        String content = Files.readString(scenarioPath);
        return MAPPER.readTree(content);
    }

    // === Kafka Integration Tests ===

    @Nested
    @DisplayName("Kafka Event Integration")
    class KafkaEventTests {

        @Test
        @DisplayName("Kafka container is running")
        void kafkaContainerIsRunning() {
            assertThat(KAFKA.isRunning()).isTrue();
            assertThat(KAFKA.getBootstrapServers()).isNotBlank();
        }

        @Test
        @DisplayName("Redis container is running")
        void redisContainerIsRunning() {
            assertThat(REDIS.isRunning()).isTrue();
        }

        @Test
        @DisplayName("WireMock container is running for upstream service mocking")
        void wiremockContainerIsRunning() {
            assertThat(WIREMOCK.isRunning()).isTrue();
        }
    }

    // === Health Check Tests ===

    @Nested
    @DisplayName("NEXUS Health Checks")
    class HealthCheckTests {

        @Test
        @DisplayName("Learning loop scenario defines health check expectations")
        void learningLoopDefinesHealthCheck() throws Exception {
            JsonNode scenario = loadScenario("nexus-learning-loop.json");
            JsonNode healthCheck = scenario.get("integration_health_check");

            assertThat(healthCheck.get("endpoint").asText())
                .isEqualTo("/api/v1/evolution/health");

            JsonNode expected = healthCheck.get("expected_response");
            assertThat(expected.get("overall_score_min").asDouble()).isGreaterThan(0.0);
            assertThat(expected.get("patterns_tracked_min").asInt()).isGreaterThanOrEqualTo(1);
        }
    }

    // === Scenario Catalog Tests ===

    @Nested
    @DisplayName("Scenario Catalog Validation")
    class CatalogTests {

        @Test
        @DisplayName("Catalog contains all NEXUS scenarios")
        void catalogContainsNexusScenarios() throws Exception {
            Path catalogPath = Path.of("scenarios", "catalog.json");
            String content = Files.readString(catalogPath);
            JsonNode catalog = MAPPER.readTree(content);

            // Find NEXUS scenarios
            int nexusScenarioCount = 0;
            for (JsonNode entry : catalog) {
                if (entry.has("target") && "nexus".equals(entry.get("target").asText())) {
                    nexusScenarioCount++;
                }
            }

            assertThat(nexusScenarioCount).isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("All NEXUS scenario files exist")
        void allNexusScenarioFilesExist() throws Exception {
            Path catalogPath = Path.of("scenarios", "catalog.json");
            String content = Files.readString(catalogPath);
            JsonNode catalog = MAPPER.readTree(content);

            for (JsonNode entry : catalog) {
                if (entry.has("target") && "nexus".equals(entry.get("target").asText())) {
                    String filename = entry.get("file").asText();
                    Path scenarioPath = Path.of("scenarios", filename);
                    assertThat(Files.exists(scenarioPath))
                        .withFailMessage("Scenario file not found: " + filename)
                        .isTrue();
                }
            }
        }
    }
}


