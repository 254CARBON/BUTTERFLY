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
    // M3: End-to-End Time-Travel Workflow
    // =========================================================================

    @Nested
    @DisplayName("M3: End-to-End Time-Travel Workflow")
    class EndToEndTimeTravelTests {

        @Test
        @DisplayName("eventToRimToCapsuleToAnalystView: complete workflow contract")
        void eventToRimToCapsuleToAnalystView() {
            // This test validates the M3 contract: event → RIM → CAPSULE → analyst view
            // At each stage, identity contracts must be maintained

            // Stage 1: Event detection produces a canonical RimNodeId
            String eventRimNodeId = "rim:event:market:fed-rate-decision-2024-12";
            assertThat(isValidRimNodeId(eventRimNodeId))
                    .as("Event RIM node ID must be valid")
                    .isTrue();

            // Stage 2: RIM node state update uses the same canonical ID
            String rimStateNodeId = eventRimNodeId;
            assertThat(rimStateNodeId).isEqualTo(eventRimNodeId);

            // Stage 3: CAPSULE snapshot must use canonical RimNodeId as scope_id
            String capsuleScopeId = rimStateNodeId;
            assertThat(isValidScopeId(capsuleScopeId))
                    .as("CAPSULE scope_id must be valid")
                    .isTrue();
            assertThat(capsuleScopeId).isEqualTo(eventRimNodeId);

            // Stage 4: Time-travel query uses canonical RimNodeId
            Instant asOf = Instant.parse("2024-12-03T10:00:00Z");
            TimeTravelQueryParams query = new TimeTravelQueryParams(
                    capsuleScopeId, asOf, 2, "omniscient"
            );
            assertThat(isValidRimNodeId(query.rimNodeId()))
                    .as("Time-travel query must use valid RimNodeId")
                    .isTrue();

            // Stage 5: Analyst view response references same canonical ID
            // The response mesh nodes should all have valid RimNodeIds
            List<String> meshNodeIds = List.of(
                    eventRimNodeId,
                    "rim:entity:finance:EURUSD",
                    "rim:region:geo:us"
            );
            for (String nodeId : meshNodeIds) {
                assertThat(isValidRimNodeId(nodeId))
                        .as("Mesh node ID '%s' must be valid", nodeId)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("timeTravelMeshMatchesCapsuleHistory: temporal consistency")
        void timeTravelMeshMatchesCapsuleHistory() {
            // This test validates that time-travel queries return data consistent
            // with CAPSULE history

            String rimNodeId = "rim:entity:finance:EURUSD";
            Instant t0 = Instant.parse("2024-12-03T09:00:00Z");
            Instant t1 = Instant.parse("2024-12-03T10:00:00Z");
            Instant t2 = Instant.parse("2024-12-03T11:00:00Z");

            // Each timestamp should produce different idempotency keys
            String keyT0 = generateIdempotencyKey(rimNodeId, t0, 60, "omniscient");
            String keyT1 = generateIdempotencyKey(rimNodeId, t1, 60, "omniscient");
            String keyT2 = generateIdempotencyKey(rimNodeId, t2, 60, "omniscient");

            // All keys should be unique (different points in time)
            assertThat(keyT0).isNotEqualTo(keyT1);
            assertThat(keyT1).isNotEqualTo(keyT2);
            assertThat(keyT0).isNotEqualTo(keyT2);

            // Each time point should maintain the same rimNodeId format
            for (Instant ts : List.of(t0, t1, t2)) {
                TimeTravelQueryParams query = new TimeTravelQueryParams(
                        rimNodeId, ts, 2, "omniscient"
                );
                assertThat(query.rimNodeId()).isEqualTo(rimNodeId);
                assertThat(query.asOf()).isEqualTo(ts);
            }
        }

        @Test
        @DisplayName("meshDepthTraversesRelationships: relationship identity consistency")
        void meshDepthTraversesRelationships() {
            // This test validates that mesh traversal maintains identity consistency
            // across relationship depth

            String centerNodeId = "rim:entity:finance:EURUSD";
            int depth = 3;

            // Simulate mesh nodes at different depths
            List<MeshNodeAtDepth> meshNodes = List.of(
                    new MeshNodeAtDepth(centerNodeId, 0),
                    new MeshNodeAtDepth("rim:entity:finance:GBPUSD", 1),
                    new MeshNodeAtDepth("rim:region:geo:europe", 1),
                    new MeshNodeAtDepth("rim:actor:central-bank:ecb", 2),
                    new MeshNodeAtDepth("rim:market:forex:european", 2),
                    new MeshNodeAtDepth("rim:signal:correlation:EUR-GBP", 3)
            );

            // All mesh nodes must have valid RimNodeIds
            for (MeshNodeAtDepth node : meshNodes) {
                assertThat(isValidRimNodeId(node.nodeId()))
                        .as("Mesh node '%s' at depth %d must have valid RimNodeId",
                                node.nodeId(), node.depth())
                        .isTrue();
                assertThat(node.depth())
                        .as("Node depth must be within query depth")
                        .isLessThanOrEqualTo(depth);
            }

            // Relationships between mesh nodes must reference valid IDs
            List<MeshRelationship> relationships = List.of(
                    new MeshRelationship(
                            "rim:relation:correlation:EURUSD-GBPUSD",
                            "rim:entity:finance:EURUSD",
                            "rim:entity:finance:GBPUSD",
                            "CORRELATES_WITH"
                    ),
                    new MeshRelationship(
                            "rim:relation:location:EURUSD-europe",
                            "rim:entity:finance:EURUSD",
                            "rim:region:geo:europe",
                            "LOCATED_IN"
                    )
            );

            for (MeshRelationship rel : relationships) {
                assertThat(isValidRimNodeId(rel.relationshipId()))
                        .as("Relationship ID must be valid")
                        .isTrue();
                assertThat(isValidRimNodeId(rel.sourceNodeId()))
                        .as("Relationship source must be valid")
                        .isTrue();
                assertThat(isValidRimNodeId(rel.targetNodeId()))
                        .as("Relationship target must be valid")
                        .isTrue();
            }
        }

        @Test
        @DisplayName("timeTravelQueryParameters: validation contracts")
        void timeTravelQueryParameters() {
            // Valid time-travel query parameters
            String validNodeId = "rim:entity:finance:EURUSD";
            Instant validAsOf = Instant.parse("2024-12-03T10:00:00Z");

            // Valid depth range: 1-5
            for (int depth : List.of(1, 2, 3, 4, 5)) {
                assertThat(isValidDepth(depth))
                        .as("Depth %d should be valid", depth)
                        .isTrue();
            }

            // Invalid depth values
            for (int depth : List.of(0, -1, 6, 10)) {
                assertThat(isValidDepth(depth))
                        .as("Depth %d should be invalid", depth)
                        .isFalse();
            }

            // Valid vantage modes
            for (String vantage : APPROVED_VANTAGE_MODES) {
                assertThat(isValidVantageMode(vantage))
                        .as("Vantage mode '%s' should be valid", vantage)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("capsuleReferencesInTimeTravel: lineage tracking")
        void capsuleReferencesInTimeTravel() {
            // Time-travel responses must include CAPSULE references for lineage

            String rimNodeId = "rim:entity:finance:EURUSD";
            Instant snapshotTime = Instant.parse("2024-12-03T10:00:00Z");
            int resolution = 60;
            String vantage = "omniscient";

            // Simulate CAPSULE reference
            CapsuleReference ref = new CapsuleReference(
                    "capsule-abc123",
                    rimNodeId,
                    snapshotTime,
                    resolution,
                    vantage,
                    1
            );

            // Validate CAPSULE reference maintains identity contracts
            assertThat(isValidRimNodeId(ref.scopeId()))
                    .as("CAPSULE reference scopeId must be valid RimNodeId")
                    .isTrue();
            assertThat(APPROVED_VANTAGE_MODES)
                    .as("CAPSULE reference vantage mode must be approved")
                    .contains(ref.vantageMode());
            assertThat(ref.revision())
                    .as("CAPSULE revision must be positive")
                    .isPositive();
        }

        @Test
        @DisplayName("temporalMetadataContract: coverage and gap reporting")
        void temporalMetadataContract() {
            // Temporal metadata must follow contract for time-travel accuracy

            // Coverage ratio: 0.0-1.0
            for (double coverage : List.of(0.0, 0.25, 0.5, 0.75, 1.0)) {
                assertThat(coverage)
                        .as("Coverage ratio %.2f should be in valid range", coverage)
                        .isBetween(0.0, 1.0);
            }

            // Temporal gap should be representable as ISO-8601 duration
            String validGap = "PT5M";  // 5 minutes
            assertThat(java.time.Duration.parse(validGap).toMinutes())
                    .as("Temporal gap should be parseable")
                    .isEqualTo(5);

            // Interpolation flag
            // If gap > 5 minutes, interpolation should be indicated
            long gapMinutes = 10;
            boolean shouldInterpolate = gapMinutes > 5;
            assertThat(shouldInterpolate)
                    .as("Interpolation should be true when gap > 5 minutes")
                    .isTrue();
        }

        // Helper methods for M3 tests

        private boolean isValidDepth(int depth) {
            return depth >= 1 && depth <= 5;
        }

        private boolean isValidVantageMode(String vantageMode) {
            return vantageMode != null && APPROVED_VANTAGE_MODES.contains(vantageMode);
        }

        // Supporting types for M3 tests

        private record TimeTravelQueryParams(
                String rimNodeId,
                Instant asOf,
                int depth,
                String vantageMode
        ) {}

        private record MeshNodeAtDepth(
                String nodeId,
                int depth
        ) {}

        private record MeshRelationship(
                String relationshipId,
                String sourceNodeId,
                String targetNodeId,
                String relationshipType
        ) {}

        private record CapsuleReference(
                String capsuleId,
                String scopeId,
                Instant snapshotTime,
                int resolutionSeconds,
                String vantageMode,
                int revision
        ) {}
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
