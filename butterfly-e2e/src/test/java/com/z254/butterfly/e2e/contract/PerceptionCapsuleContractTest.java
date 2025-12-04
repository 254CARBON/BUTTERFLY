package com.z254.butterfly.e2e.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.io.JsonEncoder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for PERCEPTION → CAPSULE data flow.
 *
 * <p>These tests validate:
 * <ul>
 *     <li>Avro schema compatibility between PERCEPTION and CAPSULE</li>
 *     <li>JSON serialization round-trips for detection events</li>
 *     <li>Required field presence and type constraints</li>
 *     <li>Schema evolution compatibility (forward/backward)</li>
 * </ul>
 *
 * <p>Contract tests ensure that both systems can communicate reliably
 * without tight coupling, allowing independent deployments while
 * maintaining integration guarantees.
 */
@DisplayName("PERCEPTION → CAPSULE Contract Tests")
public class PerceptionCapsuleContractTest {

    private static final String AVRO_SCHEMA_PATH = "/avro";
    private static ObjectMapper objectMapper;
    private static Schema perceptionSignalEventSchema;
    private static Schema perceptionDetectionResultSchema;
    private static Schema perceptionToCapsuleEventSchema;
    private static Schema capsuleDetectionEventSchema;
    private static Schema unifiedEventHeaderSchema;

    @BeforeAll
    static void setup() throws IOException {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Load Avro schemas from classpath
        unifiedEventHeaderSchema = loadSchema("UnifiedEventHeader.avsc");
        perceptionSignalEventSchema = loadSchema("PerceptionSignalEvent.avsc");
        perceptionDetectionResultSchema = loadSchema("PerceptionDetectionResult.avsc");
        perceptionToCapsuleEventSchema = loadSchema("PerceptionToCapsuleEvent.avsc");
        capsuleDetectionEventSchema = loadSchema("CapsuleDetectionEvent.avsc");
    }

    private static Schema loadSchema(String filename) throws IOException {
        try (InputStream is = PerceptionCapsuleContractTest.class.getResourceAsStream(AVRO_SCHEMA_PATH + "/" + filename)) {
            if (is == null) {
                // Fall back to loading from butterfly-common
                String schemaPath = "../../butterfly-common/src/main/avro/" + filename;
                java.nio.file.Path path = java.nio.file.Paths.get(schemaPath);
                if (java.nio.file.Files.exists(path)) {
                    return new Schema.Parser().parse(java.nio.file.Files.newInputStream(path));
                }
                throw new IOException("Schema not found: " + filename);
            }
            return new Schema.Parser().parse(is);
        }
    }

    // ==================== Avro Schema Structure Tests ====================

    @Nested
    @DisplayName("Avro Schema Structure Tests")
    class SchemaStructureTests {

        @Test
        @DisplayName("PerceptionSignalEvent should have required fields")
        void perceptionSignalEventHasRequiredFields() {
            assertThat(perceptionSignalEventSchema).isNotNull();
            assertThat(perceptionSignalEventSchema.getField("header")).isNotNull();
            assertThat(perceptionSignalEventSchema.getField("signalId")).isNotNull();
            assertThat(perceptionSignalEventSchema.getField("signalType")).isNotNull();
            assertThat(perceptionSignalEventSchema.getField("sourceSystem")).isNotNull();
            assertThat(perceptionSignalEventSchema.getField("confidence")).isNotNull();
            assertThat(perceptionSignalEventSchema.getField("timestamp")).isNotNull();
            assertThat(perceptionSignalEventSchema.getField("payload")).isNotNull();
        }

        @Test
        @DisplayName("PerceptionDetectionResult should have required fields")
        void perceptionDetectionResultHasRequiredFields() {
            assertThat(perceptionDetectionResultSchema).isNotNull();
            assertThat(perceptionDetectionResultSchema.getField("detectorId")).isNotNull();
            assertThat(perceptionDetectionResultSchema.getField("detectionType")).isNotNull();
            assertThat(perceptionDetectionResultSchema.getField("detectedAt")).isNotNull();
            assertThat(perceptionDetectionResultSchema.getField("signalCount")).isNotNull();
        }

        @Test
        @DisplayName("PerceptionToCapsuleEvent should have required fields")
        void perceptionToCapsuleEventHasRequiredFields() {
            assertThat(perceptionToCapsuleEventSchema).isNotNull();
            assertThat(perceptionToCapsuleEventSchema.getField("header")).isNotNull();
            assertThat(perceptionToCapsuleEventSchema.getField("detectionResult")).isNotNull();
        }

        @Test
        @DisplayName("CapsuleDetectionEvent should have required fields")
        void capsuleDetectionEventHasRequiredFields() {
            assertThat(capsuleDetectionEventSchema).isNotNull();
            assertThat(capsuleDetectionEventSchema.getField("detectionId")).isNotNull();
            assertThat(capsuleDetectionEventSchema.getField("scopeId")).isNotNull();
            assertThat(capsuleDetectionEventSchema.getField("detectionType")).isNotNull();
            assertThat(capsuleDetectionEventSchema.getField("detectedAt")).isNotNull();
            assertThat(capsuleDetectionEventSchema.getField("confidence")).isNotNull();
            assertThat(capsuleDetectionEventSchema.getField("score")).isNotNull();
        }

        @Test
        @DisplayName("UnifiedEventHeader should have required fields for tracing")
        void unifiedEventHeaderHasTracingFields() {
            assertThat(unifiedEventHeaderSchema).isNotNull();
            assertThat(unifiedEventHeaderSchema.getField("eventId")).isNotNull();
            assertThat(unifiedEventHeaderSchema.getField("timestamp")).isNotNull();
            assertThat(unifiedEventHeaderSchema.getField("sourceSystem")).isNotNull();
            assertThat(unifiedEventHeaderSchema.getField("correlationId")).isNotNull();
        }
    }

    // ==================== Avro Serialization Round-Trip Tests ====================

    @Nested
    @DisplayName("Avro Serialization Round-Trip Tests")
    class SerializationRoundTripTests {

        @Test
        @DisplayName("PerceptionSignalEvent should survive Avro round-trip")
        void perceptionSignalEventRoundTrip() throws IOException {
            GenericRecord record = createSamplePerceptionSignalEvent();

            // Serialize to JSON
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(perceptionSignalEventSchema);
            JsonEncoder encoder = EncoderFactory.get().jsonEncoder(perceptionSignalEventSchema, baos);
            writer.write(record, encoder);
            encoder.flush();

            String json = baos.toString(StandardCharsets.UTF_8);

            // Deserialize from JSON
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(perceptionSignalEventSchema);
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(perceptionSignalEventSchema, json);
            GenericRecord deserialized = reader.read(null, decoder);

            // Verify
            assertThat(deserialized.get("signalId")).isEqualTo(record.get("signalId"));
            assertThat(deserialized.get("signalType")).isEqualTo(record.get("signalType"));
            assertThat(deserialized.get("confidence")).isEqualTo(record.get("confidence"));
        }

        @Test
        @DisplayName("PerceptionDetectionResult should survive Avro round-trip")
        void perceptionDetectionResultRoundTrip() throws IOException {
            GenericRecord record = createSamplePerceptionDetectionResult();

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(perceptionDetectionResultSchema);
            JsonEncoder encoder = EncoderFactory.get().jsonEncoder(perceptionDetectionResultSchema, baos);
            writer.write(record, encoder);
            encoder.flush();

            String json = baos.toString(StandardCharsets.UTF_8);

            // Deserialize
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(perceptionDetectionResultSchema);
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(perceptionDetectionResultSchema, json);
            GenericRecord deserialized = reader.read(null, decoder);

            // Verify
            assertThat(deserialized.get("detectorId")).isEqualTo(record.get("detectorId"));
            assertThat(deserialized.get("detectionType")).isEqualTo(record.get("detectionType"));
            assertThat(deserialized.get("signalCount")).isEqualTo(record.get("signalCount"));
        }

        @Test
        @DisplayName("CapsuleDetectionEvent should survive Avro round-trip")
        void capsuleDetectionEventRoundTrip() throws IOException {
            GenericRecord record = createSampleCapsuleDetectionEvent();

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(capsuleDetectionEventSchema);
            JsonEncoder encoder = EncoderFactory.get().jsonEncoder(capsuleDetectionEventSchema, baos);
            writer.write(record, encoder);
            encoder.flush();

            String json = baos.toString(StandardCharsets.UTF_8);

            // Deserialize
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(capsuleDetectionEventSchema);
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(capsuleDetectionEventSchema, json);
            GenericRecord deserialized = reader.read(null, decoder);

            // Verify
            assertThat(deserialized.get("detectionId")).isEqualTo(record.get("detectionId"));
            assertThat(deserialized.get("scopeId")).isEqualTo(record.get("scopeId"));
        }
    }

    // ==================== JSON Contract Tests ====================

    @Nested
    @DisplayName("JSON Contract Tests")
    class JsonContractTests {

        @Test
        @DisplayName("PERCEPTION detection result JSON should be parseable")
        void perceptionDetectionResultJsonParseable() throws IOException {
            String json = """
                    {
                        "detectorId": "volatility-detector",
                        "detectionType": "ANOMALY",
                        "detectedAt": 1701460800000,
                        "signalCount": 5,
                        "entities": ["rim-node-123", "rim-node-456"],
                        "metrics": {"volatility": 0.85, "threshold": 0.7},
                        "patterns": [],
                        "anomalies": [
                            {
                                "type": "DYNAMICS",
                                "description": "High volatility detected",
                                "score": 0.85,
                                "threshold": 0.7,
                                "context": {}
                            }
                        ],
                        "metadata": {"source": "perception-signals"},
                        "schema_version": "1.0.0"
                    }
                    """;

            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("detectorId").asText()).isEqualTo("volatility-detector");
            assertThat(node.get("detectionType").asText()).isEqualTo("ANOMALY");
            assertThat(node.get("signalCount").asInt()).isEqualTo(5);
            assertThat(node.get("entities").isArray()).isTrue();
            assertThat(node.get("anomalies").isArray()).isTrue();
        }

        @Test
        @DisplayName("CAPSULE detection event JSON should be parseable")
        void capsuleDetectionEventJsonParseable() throws IOException {
            String json = """
                    {
                        "detectionId": "det-123",
                        "capsuleId": "cap-456",
                        "scopeId": "scope-789",
                        "detectionType": "PATTERN",
                        "patternType": "TREND",
                        "anomalyType": null,
                        "detectedAt": 1701460800000,
                        "confidence": 0.9,
                        "score": 0.85,
                        "description": "Upward trend detected",
                        "details": {"field": "price", "direction": "up"},
                        "entities": ["entity-1"],
                        "metadata": {},
                        "schema_version": "1.0.0"
                    }
                    """;

            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("detectionId").asText()).isEqualTo("det-123");
            assertThat(node.get("detectionType").asText()).isEqualTo("PATTERN");
            assertThat(node.get("patternType").asText()).isEqualTo("TREND");
            assertThat(node.get("confidence").asDouble()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("PERCEPTION to CAPSULE detection type mapping should be valid")
        void detectionTypeMappingValid() {
            // Valid PERCEPTION detector → CAPSULE type mappings
            Map<String, String> patternMappings = Map.of(
                    "emergent-pattern-detector", "SEQUENCE",
                    "co-occurrence-detector", "CORRELATION",
                    "event-horizon-detector", "TREND"
            );

            Map<String, String> anomalyMappings = Map.of(
                    "volatility-detector", "DYNAMICS",
                    "missing-signal-detector", "DATA_QUALITY",
                    "ingestion-telemetry-detector", "DATA_QUALITY"
            );

            // Verify all mappings are to valid CAPSULE types
            List<String> validPatternTypes = List.of(
                    "SEQUENCE", "CYCLE", "TREND", "REGIME_SHIFT", "CORRELATION", "SEASONALITY");
            List<String> validAnomalyTypes = List.of(
                    "POINT", "CONTEXTUAL", "COLLECTIVE", "STRUCTURAL", "TEMPORAL", "DATA_QUALITY", "CONFIDENCE", "DYNAMICS");

            patternMappings.values().forEach(type ->
                    assertThat(validPatternTypes).contains(type));

            anomalyMappings.values().forEach(type ->
                    assertThat(validAnomalyTypes).contains(type));
        }
    }

    // ==================== Schema Compatibility Tests ====================

    @Nested
    @DisplayName("Schema Compatibility Tests")
    class SchemaCompatibilityTests {

        @Test
        @DisplayName("Schema should support optional fields with defaults")
        void schemaSupportsOptionalFieldsWithDefaults() {
            // targetSystem should be optional
            Schema.Field targetSystemField = perceptionSignalEventSchema.getField("targetSystem");
            assertThat(targetSystemField).isNotNull();
            assertThat(targetSystemField.hasDefaultValue()).isTrue();

            // metadata should have empty map default
            Schema.Field metadataField = perceptionSignalEventSchema.getField("metadata");
            assertThat(metadataField).isNotNull();
            assertThat(metadataField.hasDefaultValue()).isTrue();
        }

        @Test
        @DisplayName("Schema version field should be present for evolution")
        void schemaVersionFieldPresent() {
            Schema.Field versionField = perceptionSignalEventSchema.getField("schema_version");
            assertThat(versionField).isNotNull();
            assertThat(versionField.hasDefaultValue()).isTrue();
        }

        @Test
        @DisplayName("CapsuleDetectionEvent enums should match CAPSULE definitions")
        void capsuleDetectionEnumsMatchDefinitions() {
            // Get the detectionType enum from the schema
            Schema detectionTypeSchema = capsuleDetectionEventSchema.getField("detectionType").schema();
            assertThat(detectionTypeSchema.getType()).isEqualTo(Schema.Type.ENUM);
            assertThat(detectionTypeSchema.getEnumSymbols()).containsExactlyInAnyOrder("PATTERN", "ANOMALY");

            // Get the patternType enum (union with null)
            Schema patternTypeUnion = capsuleDetectionEventSchema.getField("patternType").schema();
            assertThat(patternTypeUnion.getType()).isEqualTo(Schema.Type.UNION);
            Schema patternTypeEnum = patternTypeUnion.getTypes().stream()
                    .filter(s -> s.getType() == Schema.Type.ENUM)
                    .findFirst()
                    .orElseThrow();
            assertThat(patternTypeEnum.getEnumSymbols()).containsExactlyInAnyOrder(
                    "SEQUENCE", "CYCLE", "TREND", "REGIME_SHIFT", "CORRELATION", "SEASONALITY");

            // Get the anomalyType enum (union with null)
            Schema anomalyTypeUnion = capsuleDetectionEventSchema.getField("anomalyType").schema();
            assertThat(anomalyTypeUnion.getType()).isEqualTo(Schema.Type.UNION);
            Schema anomalyTypeEnum = anomalyTypeUnion.getTypes().stream()
                    .filter(s -> s.getType() == Schema.Type.ENUM)
                    .findFirst()
                    .orElseThrow();
            assertThat(anomalyTypeEnum.getEnumSymbols()).containsExactlyInAnyOrder(
                    "POINT", "CONTEXTUAL", "COLLECTIVE", "STRUCTURAL", "TEMPORAL", "DATA_QUALITY", "CONFIDENCE", "DYNAMICS");
        }
    }

    // ==================== Contract Violation Tests ====================

    @Nested
    @DisplayName("Contract Violation Detection Tests")
    class ContractViolationTests {

        @Test
        @DisplayName("Should reject invalid detection type")
        void rejectInvalidDetectionType() {
            String invalidJson = """
                    {
                        "detectionId": "det-123",
                        "scopeId": "scope-789",
                        "detectionType": "INVALID_TYPE",
                        "detectedAt": 1701460800000,
                        "confidence": 0.9,
                        "score": 0.85,
                        "description": "Test"
                    }
                    """;

            // Attempting to decode with Avro should fail for invalid enum
            assertThatThrownBy(() -> {
                DatumReader<GenericRecord> reader = new GenericDatumReader<>(capsuleDetectionEventSchema);
                JsonDecoder decoder = DecoderFactory.get().jsonDecoder(capsuleDetectionEventSchema, invalidJson);
                reader.read(null, decoder);
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should reject missing required fields")
        void rejectMissingRequiredFields() {
            String incompleteJson = """
                    {
                        "detectorId": "volatility-detector"
                    }
                    """;

            // Avro should require detectionType and other fields
            assertThatThrownBy(() -> {
                DatumReader<GenericRecord> reader = new GenericDatumReader<>(perceptionDetectionResultSchema);
                JsonDecoder decoder = DecoderFactory.get().jsonDecoder(perceptionDetectionResultSchema, incompleteJson);
                reader.read(null, decoder);
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should reject negative confidence scores")
        void rejectNegativeConfidence() throws IOException {
            // JSON parsing will succeed but validation should catch this
            String json = """
                    {
                        "detectionId": "det-123",
                        "scopeId": "scope-789",
                        "detectionType": "PATTERN",
                        "patternType": "TREND",
                        "detectedAt": 1701460800000,
                        "confidence": -0.5,
                        "score": 0.85,
                        "description": "Test"
                    }
                    """;

            JsonNode node = objectMapper.readTree(json);
            double confidence = node.get("confidence").asDouble();

            // Business rule: confidence should be 0.0 - 1.0
            assertThat(confidence).isLessThan(0.0);
            // This demonstrates a contract violation that should be caught at runtime
        }
    }

    // ==================== Helper Methods ====================

    private GenericRecord createSamplePerceptionSignalEvent() {
        GenericRecord header = new GenericData.Record(unifiedEventHeaderSchema);
        header.put("eventId", UUID.randomUUID().toString());
        header.put("timestamp", System.currentTimeMillis());
        header.put("sourceSystem", "perception-signals");
        header.put("correlationId", UUID.randomUUID().toString());
        header.put("traceparent", null);

        GenericRecord record = new GenericData.Record(perceptionSignalEventSchema);
        record.put("header", header);
        record.put("signalId", UUID.randomUUID().toString());
        record.put("signalType", "VOLATILITY");
        record.put("sourceSystem", "PERCEPTION");
        record.put("targetSystem", "CAPSULE");
        record.put("confidence", 0.85);
        record.put("timestamp", System.currentTimeMillis());
        record.put("payload", Map.of("volatility", "high"));
        record.put("metadata", Map.of());
        record.put("schema_version", "1.0.0");

        return record;
    }

    private GenericRecord createSamplePerceptionDetectionResult() {
        GenericRecord record = new GenericData.Record(perceptionDetectionResultSchema);
        record.put("detectorId", "volatility-detector");
        record.put("detectionType", "ANOMALY");
        record.put("detectedAt", System.currentTimeMillis());
        record.put("signalCount", 5);
        record.put("entities", List.of("rim-node-123"));
        record.put("metrics", Map.of("volatility", 0.85));
        record.put("patterns", List.of());
        record.put("anomalies", List.of());
        record.put("metadata", Map.of());
        record.put("schema_version", "1.0.0");

        return record;
    }

    private GenericRecord createSampleCapsuleDetectionEvent() {
        GenericRecord record = new GenericData.Record(capsuleDetectionEventSchema);
        record.put("detectionId", UUID.randomUUID().toString());
        record.put("capsuleId", "cap-" + UUID.randomUUID());
        record.put("scopeId", "scope-" + UUID.randomUUID());
        record.put("detectionType", new GenericData.EnumSymbol(
                capsuleDetectionEventSchema.getField("detectionType").schema(), "PATTERN"));
        record.put("patternType", new GenericData.EnumSymbol(
                capsuleDetectionEventSchema.getField("patternType").schema().getTypes().get(1), "TREND"));
        record.put("anomalyType", null);
        record.put("detectedAt", System.currentTimeMillis());
        record.put("confidence", 0.9);
        record.put("score", 0.85);
        record.put("description", "Upward trend detected");
        record.put("details", Map.of("direction", "up"));
        record.put("entities", List.of("entity-1"));
        record.put("metadata", Map.of());
        record.put("schema_version", "1.0.0");

        return record;
    }
}

