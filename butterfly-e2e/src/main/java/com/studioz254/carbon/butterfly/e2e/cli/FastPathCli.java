package com.studioz254.carbon.butterfly.e2e.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.z254.butterfly.common.hft.NodeStatus;
import com.z254.butterfly.common.hft.RimEventFactory;
import com.z254.butterfly.common.hft.RimFastEvent;
import com.z254.butterfly.common.hft.VectorTimestamp;
import com.z254.butterfly.common.identity.RimNodeId;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Thin CLI for publishing RimFastEvent payloads to Kafka or generating minimal test events.
 */
@Command(
        name = "fastpath-cli",
        mixinStandardHelpOptions = true,
        version = "0.0.1",
        description = "Publish RimFastEvent payloads to rim.fast-path, validate scenarios, or generate minimal test events.",
        subcommands = {
                FastPathCli.PublishCommand.class,
                FastPathCli.GenerateCommand.class,
                FastPathCli.ListScenariosCommand.class
        }
)
public class FastPathCli implements Runnable {

    public static void main(String[] args) {
        int exit = new CommandLine(new FastPathCli()).execute(args);
        System.exit(exit);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    static Producer<String, String> buildProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 20000);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new KafkaProducer<>(props);
    }

    static Path resolveScenariosRoot(Path provided) {
        if (provided != null && !provided.toString().trim().isEmpty()) {
            return provided.toAbsolutePath().normalize();
        }
        String envOverride = System.getenv("FASTPATH_SCENARIO_DIR");
        if (envOverride != null && !envOverride.isBlank()) {
            return Paths.get(envOverride).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().resolve("butterfly-e2e").resolve("scenarios");
    }

    static Instant parseInstant(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if ("now".equalsIgnoreCase(raw.trim())) {
            return Instant.now();
        }
        try {
            long epochMillis = Long.parseLong(raw.trim());
            return Instant.ofEpochMilli(epochMillis);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Unable to parse timestamp: " + raw, ex);
        }
    }

    static String prettyPrint(ObjectMapper mapper, String payload, boolean pretty) throws JsonProcessingException {
        if (!pretty) {
            return payload;
        }
        JsonNode node = mapper.readTree(payload);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    static String normalizeAndValidate(ObjectMapper mapper, String payload) {
        try {
            RimFastEvent event = mapper.readValue(payload, RimFastEvent.class);
            validateEvent(event);
            return mapper.writeValueAsString(event);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to parse RimFastEvent payload", ex);
        }
    }

    static void validateEvent(RimFastEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Payload did not resolve to RimFastEvent");
        }
        if (event.getRimNodeId() == null || event.getRimNodeId().toString().isBlank()) {
            throw new IllegalArgumentException("rim_node_id is required");
        }
        RimNodeId.parse(event.getRimNodeId().toString());

        VectorTimestamp ts = event.getVectorTimestamp();
        if (ts == null) {
            throw new IllegalArgumentException("vector_timestamp is required");
        }
        if (ts.getEventTsMs() <= 0 || ts.getIngestTsMs() <= 0) {
            throw new IllegalArgumentException("vector_timestamp.event_ts_ms and ingest_ts_ms must be positive");
        }
        if (event.getCriticalMetrics() == null) {
            throw new IllegalArgumentException("critical_metrics cannot be null (use an empty map when needed)");
        }
    }

    @Command(name = "publish", description = "Publish a RimFastEvent payload from JSON or a named scenario.")
    static class PublishCommand implements Callable<Integer> {

        @Option(names = {"-b", "--bootstrap"}, defaultValue = "${env:FASTPATH_BOOTSTRAP:-localhost:9092}", description = "Kafka bootstrap servers")
        private String bootstrapServers;

        @Option(names = {"-t", "--topic"}, defaultValue = "${env:FASTPATH_TOPIC:-rim.fast-path}", description = "Kafka topic")
        private String topic;

        @Option(names = {"-k", "--key"}, description = "Kafka record key")
        private String key;

        @Option(names = {"-f", "--file"}, description = "Path to RimFastEvent JSON payload")
        private Path payloadFile;

        @Option(names = {"-s", "--scenario"}, description = "Scenario file name (under scenarios root); '.json' optional")
        private String scenarioName;

        @Option(names = {"--scenarios-root"}, defaultValue = "${env:FASTPATH_SCENARIO_DIR:-}", description = "Directory containing scenario JSON files")
        private Path scenariosRoot;

        @Option(names = {"--raw"}, description = "Send the payload as-is without RimFastEvent validation")
        private boolean raw;

        @Option(names = {"--pretty"}, description = "Pretty-print JSON before sending")
        private boolean pretty;

        @Option(names = {"--dry-run"}, description = "Validate and print the payload without sending to Kafka")
        private boolean dryRun;

        @Option(names = {"--timeout-sec"}, defaultValue = "12", description = "Send timeout in seconds")
        private long timeoutSeconds;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = FastPathCli.mapper();
            Path resolved = resolvePayloadPath();
            String rawPayload = Files.readString(resolved, StandardCharsets.UTF_8);
            String normalized = raw ? rawPayload : normalizeAndValidate(mapper, rawPayload);
            String toSend = prettyPrint(mapper, normalized, pretty);

            if (dryRun) {
                System.out.println(toSend);
                return 0;
            }

            try (Producer<String, String> producer = buildProducer(bootstrapServers)) {
                RecordMetadata meta = producer.send(new ProducerRecord<>(topic, key, toSend))
                        .get(timeoutSeconds, TimeUnit.SECONDS);
                System.out.printf("Published to %s-%d @ offset %d%n", meta.topic(), meta.partition(), meta.offset());
                return 0;
            } catch (Exception ex) {
                System.err.printf("Failed to publish to %s (%s): %s%n", topic, bootstrapServers, ex.getMessage());
                return 1;
            }
        }

        private Path resolvePayloadPath() {
            if (payloadFile != null) {
                return payloadFile.toAbsolutePath().normalize();
            }
            if (scenarioName == null || scenarioName.isBlank()) {
                throw new CommandLine.ParameterException(
                        new CommandLine(this),
                        "Provide --file or --scenario to publish");
            }
            Path root = resolveScenariosRoot(scenariosRoot);
            String candidate = scenarioName.endsWith(".json") ? scenarioName : scenarioName + ".json";
            Path scenarioPath = root.resolve(candidate);
            if (!Files.exists(scenarioPath)) {
                throw new CommandLine.ParameterException(
                        new CommandLine(this),
                        "Scenario not found: " + scenarioPath);
            }
            return scenarioPath;
        }
    }

    @Command(name = "generate", description = "Generate a minimal RimFastEvent payload and optionally publish it.")
    static class GenerateCommand implements Callable<Integer> {

        @Option(names = {"-b", "--bootstrap"}, defaultValue = "${env:FASTPATH_BOOTSTRAP:-localhost:9092}", description = "Kafka bootstrap servers")
        private String bootstrapServers;

        @Option(names = {"-t", "--topic"}, defaultValue = "${env:FASTPATH_TOPIC:-rim.fast-path}", description = "Kafka topic")
        private String topic;

        @Option(names = {"-k", "--key"}, description = "Kafka record key")
        private String key;

        @Option(names = {"-n", "--node"}, required = true, description = "Canonical rim_node_id (e.g., rim:entity:finance:EURUSD)")
        private String rimNodeId;

        @Option(names = {"--event-ts"}, description = "Event timestamp (ISO-8601 or epoch millis). Defaults to now.")
        private String eventTimestamp;

        @Option(names = {"--ingest-ts"}, description = "Ingest timestamp (ISO-8601 or epoch millis). Defaults to now.")
        private String ingestTimestamp;

        @Option(names = {"--logical-clock"}, description = "Logical clock for vector timestamp")
        private Long logicalClock;

        @Option(names = {"--event-id"}, description = "Optional event identifier")
        private String eventId;

        @Option(names = {"--instrument"}, description = "Optional instrument identifier")
        private String instrumentId;

        @Option(names = {"--price"}, description = "Optional price field")
        private Double price;

        @Option(names = {"--volume"}, description = "Optional volume field")
        private Double volume;

        @Option(names = {"--stress"}, description = "Optional stress metric (also injected into critical_metrics)")
        private Double stress;

        @Option(names = {"--status"}, defaultValue = "ALERT", description = "NodeStatus enum value (OK, WARN, ALERT, UNKNOWN, OFFLINE)")
        private String status;

        @Option(names = {"--metric"}, description = "Additional metric key=value entries (repeatable)", mapFallbackValue = "")
        private Map<String, Double> metrics = new HashMap<>();

        @Option(names = {"--metadata"}, description = "Metadata key=value entries (repeatable)", mapFallbackValue = "")
        private Map<String, String> metadata = new HashMap<>();

        @Option(names = {"--source-topic"}, description = "Optional source_topic field")
        private String sourceTopic;

        @Option(names = {"--send"}, description = "Publish to Kafka instead of printing only")
        private boolean send;

        @Option(names = {"--pretty"}, description = "Pretty-print JSON before sending/printing")
        private boolean pretty;

        @Option(names = {"--timeout-sec"}, defaultValue = "12", description = "Send timeout in seconds when --send is provided")
        private long timeoutSeconds;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = FastPathCli.mapper();
            RimFastEvent event = buildEvent();
            FastPathCli.validateEvent(event);
            String payload = mapper.writeValueAsString(event);
            String json = prettyPrint(mapper, payload, pretty);

            if (!send) {
                System.out.println(json);
                return 0;
            }

            try (Producer<String, String> producer = buildProducer(bootstrapServers)) {
                RecordMetadata meta = producer.send(new ProducerRecord<>(topic, key, json))
                        .get(timeoutSeconds, TimeUnit.SECONDS);
                System.out.printf("Generated and published to %s-%d @ offset %d%n", meta.topic(), meta.partition(), meta.offset());
                return 0;
            } catch (Exception ex) {
                System.err.printf("Failed to publish generated payload to %s (%s): %s%n", topic, bootstrapServers, ex.getMessage());
                return 1;
            }
        }

        private RimFastEvent buildEvent() {
            RimNodeId nodeId = RimNodeId.parse(rimNodeId);
            Instant eventTs = parseInstant(eventTimestamp, Instant.now());
            Instant ingestTs = parseInstant(ingestTimestamp, Instant.now());

            Map<String, Double> metricCopy = new HashMap<>(Optional.ofNullable(metrics).orElseGet(HashMap::new));
            if (stress != null) {
                metricCopy.putIfAbsent("stress", stress);
            }
            Double stressField = stress != null ? stress : metricCopy.getOrDefault("stress", null);

            NodeStatus nodeStatus = NodeStatus.valueOf(status.toUpperCase());
            Map<String, String> metadataCopy = new HashMap<>(Optional.ofNullable(metadata).orElseGet(HashMap::new));

            return RimEventFactory.create(
                    nodeId,
                    eventTs,
                    ingestTs,
                    metricCopy,
                    nodeStatus,
                    logicalClock,
                    eventId,
                    instrumentId,
                    price,
                    volume,
                    stressField,
                    metadataCopy,
                    sourceTopic
            );
        }
    }

    @Command(name = "scenarios", description = "List known scenarios under the scenarios directory.")
    static class ListScenariosCommand implements Callable<Integer> {

        @Option(names = {"--scenarios-root"}, defaultValue = "${env:FASTPATH_SCENARIO_DIR:-}", description = "Directory containing scenario JSON files")
        private Path scenariosRoot;

        @Override
        public Integer call() throws Exception {
            Path root = resolveScenariosRoot(scenariosRoot);
            if (!Files.exists(root)) {
                System.err.printf("Scenario directory not found: %s%n", root);
                return 1;
            }

            Path catalog = root.resolve("catalog.json");
            if (Files.exists(catalog)) {
                printCatalog(catalog);
                return 0;
            }

            Files.list(root)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> System.out.println(path.getFileName().toString().replace(".json", "")));
            return 0;
        }

        private void printCatalog(Path catalog) throws IOException {
            ObjectMapper mapper = FastPathCli.mapper();
            JsonNode root = mapper.readTree(catalog.toFile());
            if (!root.isArray()) {
                System.out.printf("Catalog at %s is not an array; falling back to raw list.%n", catalog);
                return;
            }
            for (JsonNode entry : root) {
                String name = entry.path("name").asText("unknown");
                String description = entry.path("description").asText("no description");
                String mode = entry.path("mode").asText("unspecified");
                System.out.printf("- %s [%s]: %s%n", name, mode, description);
            }
        }
    }
}
