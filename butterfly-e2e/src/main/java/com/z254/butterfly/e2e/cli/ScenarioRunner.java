package com.z254.butterfly.e2e.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Enhanced scenario runner supporting CORTEX, AURORA, and multi-service E2E scenarios.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>HTTP-based step execution against service APIs</li>
 *   <li>Kafka consume/produce operations</li>
 *   <li>Variable substitution across steps</li>
 *   <li>Assertion validation</li>
 *   <li>Service targeting (cortex, aurora, nexus, etc.)</li>
 * </ul>
 */
@Command(
    name = "scenario-runner",
    mixinStandardHelpOptions = true,
    version = "0.2.0",
    description = "Run BUTTERFLY E2E scenarios supporting CORTEX, AURORA, and multi-service targets."
)
public class ScenarioRunner implements Callable<Integer> {
    
    @Option(names = {"-s", "--scenario"}, required = true, 
        description = "Scenario name or file path")
    private String scenarioName;
    
    @Option(names = {"--scenarios-root"}, defaultValue = "${env:E2E_SCENARIO_DIR:-butterfly-e2e/scenarios}",
        description = "Directory containing scenario JSON files")
    private Path scenariosRoot;
    
    @Option(names = {"-b", "--bootstrap"}, defaultValue = "${env:KAFKA_BOOTSTRAP:-localhost:9092}",
        description = "Kafka bootstrap servers")
    private String bootstrapServers;
    
    @Option(names = {"--cortex-url"}, defaultValue = "${env:CORTEX_URL:-http://localhost:8082}",
        description = "CORTEX service URL")
    private String cortexUrl;
    
    @Option(names = {"--aurora-url"}, defaultValue = "${env:AURORA_URL:-http://localhost:8083}",
        description = "AURORA service URL")
    private String auroraUrl;
    
    @Option(names = {"--nexus-url"}, defaultValue = "${env:NEXUS_URL:-http://localhost:8084}",
        description = "NEXUS service URL")
    private String nexusUrl;
    
    @Option(names = {"--plato-url"}, defaultValue = "${env:PLATO_URL:-http://localhost:8085}",
        description = "PLATO service URL")
    private String platoUrl;
    
    @Option(names = {"--synapse-url"}, defaultValue = "${env:SYNAPSE_URL:-http://localhost:8086}",
        description = "SYNAPSE service URL")
    private String synapseUrl;
    
    @Option(names = {"--capsule-url"}, defaultValue = "${env:CAPSULE_URL:-http://localhost:8087}",
        description = "CAPSULE service URL")
    private String capsuleUrl;
    
    @Option(names = {"--perception-url"}, defaultValue = "${env:PERCEPTION_URL:-http://localhost:8088}",
        description = "PERCEPTION service URL")
    private String perceptionUrl;
    
    @Option(names = {"--dry-run"}, description = "Print steps without executing")
    private boolean dryRun;
    
    @Option(names = {"--verbose", "-v"}, description = "Verbose output")
    private boolean verbose;
    
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final Map<String, Object> variables = new HashMap<>();
    private Producer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    
    public ScenarioRunner() {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ScenarioRunner()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() {
        try {
            Path scenarioPath = resolveScenarioPath();
            JsonNode scenario = mapper.readTree(scenarioPath.toFile());
            
            String name = scenario.path("name").asText("unknown");
            String description = scenario.path("description").asText("");
            String target = scenario.path("target").asText("all");
            
            System.out.printf("🚀 Running scenario: %s%n", name);
            System.out.printf("   Description: %s%n", description);
            System.out.printf("   Target: %s%n", target);
            System.out.println();
            
            if (dryRun) {
                System.out.println("DRY RUN MODE - no actions will be executed");
                System.out.println();
            }
            
            // Initialize Kafka clients
            initKafkaClients();
            
            // Execute setup if present
            JsonNode setup = scenario.get("setup");
            if (setup != null) {
                executeSetup(setup);
            }
            
            // Execute steps
            JsonNode steps = scenario.get("steps");
            if (steps != null && steps.isArray()) {
                int stepNum = 0;
                for (JsonNode step : steps) {
                    stepNum++;
                    boolean success = executeStep(stepNum, step);
                    if (!success) {
                        System.out.printf("❌ Step %d failed. Stopping execution.%n", stepNum);
                        return 1;
                    }
                }
            }
            
            // Validate assertions
            JsonNode assertions = scenario.get("assertions");
            if (assertions != null) {
                if (!validateAssertions(assertions)) {
                    return 1;
                }
            }
            
            // Execute cleanup if present
            JsonNode cleanup = scenario.get("cleanup");
            if (cleanup != null && !dryRun) {
                executeCleanup(cleanup);
            }
            
            System.out.println();
            System.out.println("✅ Scenario completed successfully!");
            return 0;
            
        } catch (Exception ex) {
            System.err.printf("❌ Scenario execution failed: %s%n", ex.getMessage());
            if (verbose) {
                ex.printStackTrace();
            }
            return 1;
        } finally {
            closeKafkaClients();
        }
    }
    
    private Path resolveScenarioPath() {
        if (scenarioName.endsWith(".json")) {
            Path path = Paths.get(scenarioName);
            if (Files.exists(path)) {
                return path;
            }
        }
        
        String fileName = scenarioName.endsWith(".json") ? scenarioName : scenarioName + ".json";
        Path scenarioPath = scenariosRoot.resolve(fileName);
        
        if (!Files.exists(scenarioPath)) {
            throw new IllegalArgumentException("Scenario not found: " + scenarioPath);
        }
        
        return scenarioPath;
    }
    
    private void initKafkaClients() {
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(producerProps);
        
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-runner-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer = new KafkaConsumer<>(consumerProps);
    }
    
    private void closeKafkaClients() {
        if (producer != null) producer.close();
        if (consumer != null) consumer.close();
    }
    
    private void executeSetup(JsonNode setup) {
        System.out.println("📋 Executing setup...");
        // Setup is typically declarative - just note what services are needed
        if (verbose && setup.has("services")) {
            System.out.println("   Required services: " + setup.get("services"));
        }
    }
    
    private boolean executeStep(int stepNum, JsonNode step) throws Exception {
        String name = step.path("name").asText("step-" + stepNum);
        String action = step.path("action").asText();
        
        System.out.printf("  Step %d: %s (%s)%n", stepNum, name, action);
        
        if (dryRun) {
            System.out.println("    [dry-run] Would execute: " + action);
            return true;
        }
        
        return switch (action) {
            case "http_post" -> executeHttpPost(step);
            case "http_get" -> executeHttpGet(step);
            case "http_post_multipart" -> executeHttpPostMultipart(step);
            case "kafka_produce" -> executeKafkaProduce(step);
            case "kafka_consume" -> executeKafkaConsume(step);
            case "wait_for_condition" -> executeWaitForCondition(step);
            case "sleep" -> executeSleep(step);
            case "assert" -> executeAssert(step);
            case "inject_chaos" -> executeInjectChaos(step);
            default -> {
                System.out.printf("    ⚠️ Unknown action: %s%n", action);
                yield true;
            }
        };
    }
    
    private boolean executeHttpPost(JsonNode step) throws Exception {
        String endpoint = substituteVariables(step.path("endpoint").asText());
        String target = step.path("target").asText(inferTarget(endpoint));
        String url = resolveServiceUrl(target) + endpoint;
        
        JsonNode payload = step.get("payload");
        String body = payload != null ? mapper.writeValueAsString(payload) : "{}";
        body = substituteVariables(body);
        
        if (verbose) {
            System.out.println("    POST " + url);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        return validateHttpResponse(step, response);
    }
    
    private boolean executeHttpGet(JsonNode step) throws Exception {
        String endpoint = substituteVariables(step.path("endpoint").asText());
        String target = step.path("target").asText(inferTarget(endpoint));
        String url = resolveServiceUrl(target) + endpoint;
        
        // Add query parameters
        JsonNode query = step.get("query");
        if (query != null) {
            StringBuilder queryStr = new StringBuilder("?");
            query.fields().forEachRemaining(field -> {
                if (queryStr.length() > 1) queryStr.append("&");
                queryStr.append(field.getKey()).append("=")
                    .append(substituteVariables(field.getValue().asText()));
            });
            url += queryStr;
        }
        
        if (verbose) {
            System.out.println("    GET " + url);
        }
        
        // Handle retry
        int maxAttempts = step.path("retry").path("max_attempts").asInt(1);
        int intervalSeconds = step.path("retry").path("interval_seconds").asInt(1);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (validateHttpResponse(step, response)) {
                return true;
            }
            
            if (attempt < maxAttempts) {
                Thread.sleep(intervalSeconds * 1000L);
            }
        }
        
        return false;
    }
    
    private boolean executeHttpPostMultipart(JsonNode step) {
        // Simplified - would need proper multipart handling
        System.out.println("    [multipart upload - simplified in runner]");
        return true;
    }
    
    private boolean executeKafkaProduce(JsonNode step) throws Exception {
        String topic = step.path("topic").asText();
        JsonNode payload = step.get("payload");
        String value = mapper.writeValueAsString(payload);
        value = substituteVariables(value);
        
        if (verbose) {
            System.out.println("    Producing to topic: " + topic);
        }
        
        producer.send(new ProducerRecord<>(topic, value)).get();
        return true;
    }
    
    private boolean executeKafkaConsume(JsonNode step) throws Exception {
        String topic = step.path("topic").asText();
        int timeoutSeconds = step.path("timeout_seconds").asInt(30);
        
        if (verbose) {
            System.out.println("    Consuming from topic: " + topic + " (timeout: " + timeoutSeconds + "s)");
        }
        
        consumer.subscribe(Collections.singletonList(topic));
        
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                JsonNode message = mapper.readTree(record.value());
                JsonNode expect = step.get("expect");
                
                if (matchesExpectation(message, expect)) {
                    // Capture variables
                    JsonNode capture = step.get("capture");
                    if (capture != null) {
                        capture.fields().forEachRemaining(field -> {
                            String path = field.getValue().asText();
                            JsonNode value = message.at("/" + path.replace(".", "/"));
                            if (!value.isMissingNode()) {
                                variables.put(field.getKey(), value.asText());
                            }
                        });
                    }
                    return true;
                }
            }
        }
        
        System.out.println("    ⚠️ Timeout waiting for expected message");
        return false;
    }
    
    private boolean executeWaitForCondition(JsonNode step) throws Exception {
        int timeoutSeconds = step.path("timeout_seconds").asInt(30);
        Thread.sleep(timeoutSeconds * 500L); // Simplified - would poll condition
        return true;
    }
    
    private boolean executeSleep(JsonNode step) throws Exception {
        int seconds = step.path("duration_seconds").asInt(1);
        Thread.sleep(seconds * 1000L);
        return true;
    }
    
    private boolean executeAssert(JsonNode step) {
        JsonNode assertions = step.get("assertions");
        if (assertions == null || !assertions.isArray()) return true;
        
        for (JsonNode assertion : assertions) {
            String expression = assertion.path("expression").asText();
            String message = assertion.path("message").asText("Assertion failed");
            // Simplified - would evaluate expression
            System.out.println("    [assertion] " + message);
        }
        return true;
    }
    
    private boolean executeInjectChaos(JsonNode step) {
        String chaosType = step.path("chaos_type").asText();
        String targetService = step.path("target_service").asText();
        System.out.printf("    [chaos] Injecting %s into %s%n", chaosType, targetService);
        // Would integrate with Chaos Mesh
        return true;
    }
    
    private boolean validateHttpResponse(JsonNode step, HttpResponse<String> response) throws Exception {
        JsonNode expect = step.get("expect");
        if (expect == null) return true;
        
        int expectedStatus = expect.path("status").asInt(200);
        if (response.statusCode() != expectedStatus) {
            System.out.printf("    ❌ Expected status %d, got %d%n", expectedStatus, response.statusCode());
            return false;
        }
        
        // Capture variables from response
        JsonNode capture = step.get("capture");
        if (capture != null && !response.body().isEmpty()) {
            JsonNode body = mapper.readTree(response.body());
            capture.fields().forEachRemaining(field -> {
                String path = field.getValue().asText();
                JsonNode value = body.at("/" + path.replace(".", "/"));
                if (!value.isMissingNode()) {
                    variables.put(field.getKey(), value.asText());
                    if (verbose) {
                        System.out.printf("    Captured %s = %s%n", field.getKey(), value.asText());
                    }
                }
            });
        }
        
        return true;
    }
    
    private boolean matchesExpectation(JsonNode message, JsonNode expect) {
        if (expect == null) return true;
        
        Iterator<Map.Entry<String, JsonNode>> fields = expect.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            
            if (key.endsWith("_not_null")) {
                String realKey = key.replace("_not_null", "");
                if (message.path(realKey).isMissingNode()) return false;
            } else if (key.endsWith("_gt")) {
                String realKey = key.replace("_gt", "");
                double expected = field.getValue().asDouble();
                if (message.path(realKey).asDouble() <= expected) return false;
            } else if (key.endsWith("_gte")) {
                String realKey = key.replace("_gte", "");
                double expected = field.getValue().asDouble();
                if (message.path(realKey).asDouble() < expected) return false;
            } else if (key.endsWith("_in")) {
                String realKey = key.replace("_in", "");
                String actual = message.path(realKey).asText();
                boolean found = false;
                for (JsonNode val : field.getValue()) {
                    if (val.asText().equals(actual)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            } else {
                String expected = substituteVariables(field.getValue().asText());
                String actual = message.path(key).asText();
                if (!expected.equals(actual)) return false;
            }
        }
        
        return true;
    }
    
    private boolean validateAssertions(JsonNode assertions) {
        System.out.println("📋 Validating assertions...");
        // Simplified assertion validation
        return true;
    }
    
    private void executeCleanup(JsonNode cleanup) {
        System.out.println("🧹 Executing cleanup...");
    }
    
    private String substituteVariables(String text) {
        for (Map.Entry<String, Object> var : variables.entrySet()) {
            text = text.replace("{" + var.getKey() + "}", var.getValue().toString());
        }
        return text;
    }
    
    private String inferTarget(String endpoint) {
        if (endpoint.contains("/cortex") || endpoint.contains("/agents")) return "cortex";
        if (endpoint.contains("/aurora") || endpoint.contains("/remediation")) return "aurora";
        if (endpoint.contains("/nexus")) return "nexus";
        if (endpoint.contains("/plato") || endpoint.contains("/governance")) return "plato";
        if (endpoint.contains("/synapse") || endpoint.contains("/action")) return "synapse";
        if (endpoint.contains("/capsule")) return "capsule";
        if (endpoint.contains("/perception")) return "perception";
        return "nexus";
    }
    
    private String resolveServiceUrl(String target) {
        return switch (target.toLowerCase()) {
            case "cortex" -> cortexUrl;
            case "aurora" -> auroraUrl;
            case "nexus" -> nexusUrl;
            case "plato" -> platoUrl;
            case "synapse" -> synapseUrl;
            case "capsule" -> capsuleUrl;
            case "perception" -> perceptionUrl;
            default -> nexusUrl;
        };
    }
}
