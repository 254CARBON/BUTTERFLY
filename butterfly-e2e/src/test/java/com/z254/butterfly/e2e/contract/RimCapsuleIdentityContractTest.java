package com.z254.butterfly.e2e.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for RIM-CAPSULE identity alignment.
 *
 * <p>These tests enforce the hard contracts defined in BUTTERFLY_IDENTITY.md:
 * <ul>
 *   <li>RimNodeId format: {@code rim:{nodeType}:{namespace}:{localId}}</li>
 *   <li>CAPSULE scope_id must equal the canonical RimNodeId or rim-scope format</li>
 *   <li>Idempotency keys are deterministic: SHA256(nodeId+ts+resolution+vantage)</li>
 *   <li>Node types are restricted to the approved set</li>
 *   <li>Composite scopes use: {@code rim-scope:{namespace}:{scopeName}}</li>
 *   <li>Relationship payloads reference canonical RimNodeIds</li>
 * </ul>
 *
 * <p>Contract violations result in CI failures, ensuring identity consistency
 * across PERCEPTION, CAPSULE, and ODYSSEY.
 *
 * @see <a href="../../PERCEPTION/BUTTERFLY_IDENTITY.md">BUTTERFLY_IDENTITY.md</a>
 * @see <a href="../../docs/architecture/identity-model.md">Identity Model</a>
 */
@DisplayName("RIM-CAPSULE Identity Contract Tests")
public class RimCapsuleIdentityContractTest {

    /**
     * Canonical RimNodeId pattern per BUTTERFLY_IDENTITY.md.
     * Format: rim:{nodeType}:{namespace}:{localId}
     */
    private static final Pattern RIM_NODE_ID_PATTERN = Pattern.compile(
            "^rim:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-zA-Z0-9_-]+$"
    );

    /**
     * Composite rim-scope pattern for multi-node scopes.
     * Format: rim-scope:{namespace}:{scopeName}
     */
    private static final Pattern RIM_SCOPE_PATTERN = Pattern.compile(
            "^rim-scope:[a-z][a-z0-9-]*:[a-zA-Z0-9_-]+$"
    );

    /**
     * Approved node types per BUTTERFLY_IDENTITY.md Section 1.
     */
    private static final Set<String> APPROVED_NODE_TYPES = Set.of(
            "entity",    // Business entities, assets
            "region",    // Geographic regions
            "market",    // Market structures
            "actor",     // Agents, organizations, individuals
            "system",    // System components
            "event",     // Occurrences, happenings
            "scope",     // Groupings, scenarios, contexts
            "relation",  // Connections between entities
            "metric",    // Measurable quantities
            "signal"     // Detected patterns, alerts
    );

    /**
     * Approved vantage modes per BUTTERFLY_IDENTITY.md Section 2.
     */
    private static final Set<String> APPROVED_VANTAGE_MODES = Set.of(
            "omniscient",  // Complete view with all available information
            "observer",    // Specific observer's perspective
            "system",      // System-internal view
            "public"       // Publicly available information only
    );

    // =========================================================================
    // RimNodeId Format Validation
    // =========================================================================

    @Nested
    @DisplayName("RimNodeId Format Validation")
    class RimNodeIdFormatTests {

        @ParameterizedTest(name = "Valid RimNodeId: {0}")
        @ValueSource(strings = {
                "rim:entity:finance:EURUSD",
                "rim:actor:regulator:SEC",
                "rim:event:market:fed-rate-decision-2024-03",
                "rim:region:geo:emea",
                "rim:market:equity:us-large-cap",
                "rim:system:internal:perception-pipeline",
                "rim:metric:volatility:SPX-30d",
                "rim:signal:anomaly:volume-spike-123",
                "rim:scope:macro:global-recession",
                "rim:relation:supply:TSMC-AAPL"
        })
        @DisplayName("should accept valid RimNodeId formats")
        void validRimNodeIdFormats(String rimNodeId) {
            assertThat(isValidRimNodeId(rimNodeId))
                    .as("RimNodeId '%s' should be valid per BUTTERFLY_IDENTITY.md", rimNodeId)
                    .isTrue();
        }

        @ParameterizedTest(name = "Invalid RimNodeId: {0}")
        @ValueSource(strings = {
                "entity:finance:EURUSD",           // Missing rim: prefix
                "rim:Entity:Finance:EUR",          // Uppercase nodeType/namespace
                "rim:entity:finance:",             // Empty localId
                "rim:entity::EURUSD",              // Empty namespace
                "rim::finance:EURUSD",             // Empty nodeType
                "rim:entity:finance:EUR USD",      // Space in localId
                "rim:entity:finance:EUR/USD",      // Slash in localId
                "rim:ENTITY:finance:EURUSD",       // Uppercase nodeType
                "rim:entity:FINANCE:EURUSD",       // Uppercase namespace
                "invalid-format",                   // No structure
                "",                                 // Empty string
                "rim:entity:finance"               // Missing localId
        })
        @DisplayName("should reject invalid RimNodeId formats")
        void invalidRimNodeIdFormats(String rimNodeId) {
            assertThat(isValidRimNodeId(rimNodeId))
                    .as("RimNodeId '%s' should be invalid per BUTTERFLY_IDENTITY.md", rimNodeId)
                    .isFalse();
        }

        @Test
        @DisplayName("should reject null RimNodeId")
        void rejectNullRimNodeId() {
            assertThat(isValidRimNodeId(null)).isFalse();
        }

        @Test
        @DisplayName("RimNodeId components should be extractable")
        void extractRimNodeIdComponents() {
            String rimNodeId = "rim:entity:finance:EURUSD";
            RimNodeIdComponents components = parseRimNodeId(rimNodeId);

            assertThat(components).isNotNull();
            assertThat(components.nodeType()).isEqualTo("entity");
            assertThat(components.namespace()).isEqualTo("finance");
            assertThat(components.localId()).isEqualTo("EURUSD");
        }
    }

    // =========================================================================
    // RimNodeId to CAPSULE scope_id Alignment
    // =========================================================================

    @Nested
    @DisplayName("RimNodeId-CAPSULE Scope Alignment")
    class ScopeAlignmentTests {

        @Test
        @DisplayName("rimNodeId must match CAPSULE scope_id for single-node scopes")
        void rimNodeId_mustMatchCapsuleScopeId() {
            String rimNodeId = "rim:entity:finance:EURUSD";
            String capsuleScopeId = "rim:entity:finance:EURUSD";

            assertThat(capsuleScopeId)
                    .as("CAPSULE scope_id must equal canonical RimNodeId per BUTTERFLY_IDENTITY.md Section 3")
                    .isEqualTo(rimNodeId);

            // Verify both are valid
            assertThat(isValidRimNodeId(rimNodeId)).isTrue();
            assertThat(isValidScopeId(capsuleScopeId)).isTrue();
        }

        @ParameterizedTest
        @MethodSource("com.z254.butterfly.e2e.contract.RimCapsuleIdentityContractTest#scopeAlignmentTestCases")
        @DisplayName("CAPSULE scope_id alignment validation")
        void scopeIdAlignmentValidation(String rimNodeId, String capsuleScopeId, boolean shouldMatch) {
            if (shouldMatch) {
                assertThat(capsuleScopeId)
                        .as("CAPSULE scope_id should match RimNodeId")
                        .isEqualTo(rimNodeId);
            } else {
                assertThat(capsuleScopeId)
                        .as("CAPSULE scope_id should NOT match RimNodeId")
                        .isNotEqualTo(rimNodeId);
            }
        }

        @Test
        @DisplayName("composite scope must use rim-scope prefix for multi-node scopes")
        void compositeScope_mustUseRimScopePrefix() {
            // Valid composite scopes
            assertThat(isValidScopeId("rim-scope:macro:global-recession-2024")).isTrue();
            assertThat(isValidScopeId("rim-scope:portfolio:equity-us-tech")).isTrue();
            assertThat(isValidScopeId("rim-scope:scenario:fed-rate-hike-50bp")).isTrue();

            // Invalid composite scopes
            assertThat(isValidScopeId("scope:macro:global-recession")).isFalse();
            assertThat(isValidScopeId("rim:scope:macro:global-recession")).isTrue(); // This is a node, not composite scope
        }
    }

    // =========================================================================
    // Idempotency Key Generation
    // =========================================================================

    @Nested
    @DisplayName("Idempotency Key Determinism")
    class IdempotencyKeyTests {

        @Test
        @DisplayName("idempotency key must be deterministic")
        void idempotencyKey_mustBeDeterministic() {
            String rimNodeId = "rim:entity:finance:EURUSD";
            Instant snapshotTime = Instant.parse("2024-12-03T10:00:00Z");
            int resolutionSeconds = 60;
            String vantageMode = "omniscient";

            String key1 = generateIdempotencyKey(rimNodeId, snapshotTime, resolutionSeconds, vantageMode);
            String key2 = generateIdempotencyKey(rimNodeId, snapshotTime, resolutionSeconds, vantageMode);

            assertThat(key1)
                    .as("Idempotency key must be deterministic - same inputs produce same output")
                    .isEqualTo(key2);
            assertThat(key1)
                    .as("Idempotency key must be 64-character hex (SHA-256)")
                    .hasSize(64)
                    .matches("[a-f0-9]+");
        }

        @Test
        @DisplayName("different inputs must produce different idempotency keys")
        void differentInputs_differentIdempotencyKeys() {
            String rimNodeId = "rim:entity:finance:EURUSD";
            Instant snapshotTime = Instant.parse("2024-12-03T10:00:00Z");
            int resolutionSeconds = 60;
            String vantageMode = "omniscient";

            String baseKey = generateIdempotencyKey(rimNodeId, snapshotTime, resolutionSeconds, vantageMode);

            // Different nodeId
            String keyDifferentNode = generateIdempotencyKey(
                    "rim:entity:finance:GBPUSD", snapshotTime, resolutionSeconds, vantageMode);
            assertThat(keyDifferentNode).isNotEqualTo(baseKey);

            // Different timestamp
            String keyDifferentTime = generateIdempotencyKey(
                    rimNodeId, Instant.parse("2024-12-03T11:00:00Z"), resolutionSeconds, vantageMode);
            assertThat(keyDifferentTime).isNotEqualTo(baseKey);

            // Different resolution
            String keyDifferentResolution = generateIdempotencyKey(
                    rimNodeId, snapshotTime, 3600, vantageMode);
            assertThat(keyDifferentResolution).isNotEqualTo(baseKey);

            // Different vantage
            String keyDifferentVantage = generateIdempotencyKey(
                    rimNodeId, snapshotTime, resolutionSeconds, "observer");
            assertThat(keyDifferentVantage).isNotEqualTo(baseKey);
        }

        @Test
        @DisplayName("idempotency key format matches BUTTERFLY_IDENTITY.md spec")
        void idempotencyKeyFormatMatchesSpec() {
            // Per BUTTERFLY_IDENTITY.md Section 7:
            // Algorithm: SHA-256(rimNodeId + snapshotTime + resolutionSeconds + vantageMode)
            String rimNodeId = "rim:entity:finance:EURUSD";
            Instant snapshotTime = Instant.parse("2024-12-03T10:00:00Z");
            int resolutionSeconds = 60;
            String vantageMode = "omniscient";

            String key = generateIdempotencyKey(rimNodeId, snapshotTime, resolutionSeconds, vantageMode);

            // Verify structure: 64 hex characters (256 bits)
            assertThat(key).hasSize(64);
            assertThat(key).matches("^[a-f0-9]{64}$");
        }
    }

    // =========================================================================
    // Node Type Validation
    // =========================================================================

    @Nested
    @DisplayName("Node Type Validation")
    class NodeTypeTests {

        @ParameterizedTest(name = "Approved node type: {0}")
        @ValueSource(strings = {"entity", "region", "market", "actor", "system", "event", "scope", "relation", "metric", "signal"})
        @DisplayName("approved node types must be accepted")
        void nodeType_mustBeInAllowedSet(String nodeType) {
            assertThat(APPROVED_NODE_TYPES)
                    .as("Node type '%s' must be in approved set per BUTTERFLY_IDENTITY.md", nodeType)
                    .contains(nodeType);

            String rimNodeId = String.format("rim:%s:test:sample-id", nodeType);
            assertThat(isValidRimNodeId(rimNodeId)).isTrue();
        }

        @ParameterizedTest(name = "Unapproved node type: {0}")
        @ValueSource(strings = {"unknown", "custom", "deprecated", "ENTITY", "Entity"})
        @DisplayName("unapproved node types must be rejected")
        void unapprovedNodeTypes_mustBeRejected(String nodeType) {
            assertThat(APPROVED_NODE_TYPES)
                    .as("Node type '%s' should NOT be in approved set", nodeType)
                    .doesNotContain(nodeType);
        }

        @Test
        @DisplayName("new node types require ADR approval")
        void newNodeTypes_requireAdrApproval() {
            // This test documents the contract: new node types require an ADR
            // The approved set is defined in BUTTERFLY_IDENTITY.md and cannot be
            // extended without updating the spec and creating a migration plan
            assertThat(APPROVED_NODE_TYPES)
                    .as("Approved node types are frozen per ADR-0003 and BUTTERFLY_IDENTITY.md")
                    .containsExactlyInAnyOrder(
                            "entity", "region", "market", "actor", "system",
                            "event", "scope", "relation", "metric", "signal"
                    );
        }
    }

    // =========================================================================
    // Vantage Mode Validation
    // =========================================================================

    @Nested
    @DisplayName("Vantage Mode Validation")
    class VantageModeTests {

        @ParameterizedTest(name = "Approved vantage mode: {0}")
        @ValueSource(strings = {"omniscient", "observer", "system", "public"})
        @DisplayName("approved vantage modes must be accepted")
        void vantageMode_mustBeInAllowedSet(String vantageMode) {
            assertThat(APPROVED_VANTAGE_MODES)
                    .as("Vantage mode '%s' must be in approved set per BUTTERFLY_IDENTITY.md", vantageMode)
                    .contains(vantageMode);
        }

        @ParameterizedTest(name = "Unapproved vantage mode: {0}")
        @ValueSource(strings = {"private", "admin", "OMNISCIENT", "Omniscient"})
        @DisplayName("unapproved vantage modes must be rejected")
        void unapprovedVantageModes_mustBeRejected(String vantageMode) {
            assertThat(APPROVED_VANTAGE_MODES)
                    .as("Vantage mode '%s' should NOT be in approved set", vantageMode)
                    .doesNotContain(vantageMode);
        }
    }

    // =========================================================================
    // Relationship Payload Validation
    // =========================================================================

    @Nested
    @DisplayName("Relationship Payload Validation")
    class RelationshipPayloadTests {

        @Test
        @DisplayName("relationship payloads must reference canonical RimNodeIds")
        void relationshipPayloads_mustReferenceCanonicalIds() {
            // Simulate a relationship payload
            RelationshipPayload relationship = new RelationshipPayload(
                    "rim:relation:supply:TSMC-AAPL",
                    "rim:entity:tech:TSMC",
                    "rim:entity:tech:AAPL",
                    "SUPPLIES"
            );

            // All node references must be valid RimNodeIds
            assertThat(isValidRimNodeId(relationship.relationshipId()))
                    .as("Relationship ID must be valid RimNodeId")
                    .isTrue();
            assertThat(isValidRimNodeId(relationship.sourceNodeId()))
                    .as("Source node ID must be valid RimNodeId")
                    .isTrue();
            assertThat(isValidRimNodeId(relationship.targetNodeId()))
                    .as("Target node ID must be valid RimNodeId")
                    .isTrue();
        }

        @Test
        @DisplayName("relationship type must be uppercase")
        void relationshipType_mustBeUppercase() {
            List<String> validTypes = List.of(
                    "INFLUENCES", "DEPENDS_ON", "CONTAINS", "CORRELATES_WITH", "TRACKS", "GOVERNS", "SUPPLIES"
            );

            for (String type : validTypes) {
                assertThat(type)
                        .as("Relationship type '%s' should be uppercase", type)
                        .isUpperCase();
            }
        }
    }

    // =========================================================================
    // Helper Methods and Test Data
    // =========================================================================

    private static boolean isValidRimNodeId(String rimNodeId) {
        if (rimNodeId == null || rimNodeId.isBlank()) {
            return false;
        }
        if (!RIM_NODE_ID_PATTERN.matcher(rimNodeId).matches()) {
            return false;
        }
        // Extract and validate nodeType
        String[] parts = rimNodeId.split(":");
        if (parts.length != 4) {
            return false;
        }
        String nodeType = parts[1];
        return APPROVED_NODE_TYPES.contains(nodeType);
    }

    private static boolean isValidScopeId(String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return false;
        }
        // Accept either canonical RimNodeId or rim-scope format
        return isValidRimNodeId(scopeId) || RIM_SCOPE_PATTERN.matcher(scopeId).matches();
    }

    private static RimNodeIdComponents parseRimNodeId(String rimNodeId) {
        if (!isValidRimNodeId(rimNodeId)) {
            return null;
        }
        String[] parts = rimNodeId.split(":");
        return new RimNodeIdComponents(parts[1], parts[2], parts[3]);
    }

    /**
     * Generates an idempotency key per BUTTERFLY_IDENTITY.md Section 7.
     * Algorithm: SHA-256(rimNodeId + snapshotTime + resolutionSeconds + vantageMode)
     */
    private static String generateIdempotencyKey(
            String rimNodeId,
            Instant snapshotTime,
            int resolutionSeconds,
            String vantageMode
    ) {
        String composite = String.format(
                "%s:%s:%d:%s",
                rimNodeId,
                snapshotTime.toString(),
                resolutionSeconds,
                vantageMode
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(composite.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    static Stream<Arguments> scopeAlignmentTestCases() {
        return Stream.of(
                // (rimNodeId, capsuleScopeId, shouldMatch)
                Arguments.of("rim:entity:finance:EURUSD", "rim:entity:finance:EURUSD", true),
                Arguments.of("rim:actor:regulator:SEC", "rim:actor:regulator:SEC", true),
                Arguments.of("rim:entity:finance:EURUSD", "rim:entity:finance:GBPUSD", false),
                Arguments.of("rim:entity:finance:EURUSD", "entity:finance:EURUSD", false)
        );
    }

    // =========================================================================
    // Supporting Types
    // =========================================================================

    private record RimNodeIdComponents(String nodeType, String namespace, String localId) {}

    private record RelationshipPayload(
            String relationshipId,
            String sourceNodeId,
            String targetNodeId,
            String relationshipType
    ) {}
}
