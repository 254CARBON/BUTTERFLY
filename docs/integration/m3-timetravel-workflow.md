# M3: Time-Travel Analyst Workflow

> **Milestone 3**: End-to-end workflows: event → RIM node updates → CAPSULE snapshot → analyst view with time-travel.

This document describes the complete M3 integration that enables analysts to query the RIM mesh as it existed at any point in time, leveraging CAPSULE's historical snapshot storage for accurate reconstruction.

## Overview

The M3 Time-Travel workflow connects four core BUTTERFLY components:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   EVENT     │────▶│     RIM     │────▶│   CAPSULE   │────▶│  ANALYST    │
│  Detection  │     │  Node State │     │  Snapshot   │     │    VIEW     │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                                                                   │
                                              ┌────────────────────┘
                                              │ Time-Travel Query
                                              ▼
                                        ┌─────────────┐
                                        │  CAPSULE    │
                                        │   History   │
                                        └─────────────┘
```

### Key Capabilities

1. **Temporal Mesh Reconstruction**: Query the RIM mesh as it existed at any historical timestamp
2. **CAPSULE Lineage**: Track the revision chain of snapshots for any entity
3. **Multi-Vantage Support**: Filter historical views by vantage perspective (omniscient, observer, system, public)
4. **Degraded Responses**: Graceful handling when historical data is unavailable

## Data Flow

### 1. Event Detection to RIM

When PERCEPTION detects an event:

```
PERCEPTION Event → rim.fast-path (Kafka) → RIM Node State Update
```

The event triggers a RIM node state update with:
- Canonical `rimNodeId` (e.g., `rim:entity:finance:EURUSD`)
- `effectiveAt` timestamp
- State metrics (trust, coverage, etc.)
- Metadata including source provenance

### 2. RIM to CAPSULE Projection

RIM automatically projects state changes to CAPSULE:

```
RIM Node State → RimCapsuleAdapter → CAPSULE /api/v1/capsules
```

Each projection includes:
- `scopeId` = canonical `rimNodeId`
- `snapshotTime` = state `effectiveAt`
- `resolutionSeconds` = state resolution
- `vantageMode` = observation perspective
- `idempotency_key` = SHA-256(rimNodeId + snapshotTime + resolution + vantage)

### 3. Analyst Time-Travel Query

Analysts query historical mesh state:

```
Analyst Request (asOf=T0) → PERCEPTION Time-Travel API → CAPSULE History → Mesh Reconstruction
```

The API:
1. Validates the `rimNodeId` format
2. Queries CAPSULE history around the `asOf` timestamp
3. Finds the closest snapshot to the requested time
4. Reconstructs the mesh view from historical data
5. Returns the mesh with CAPSULE lineage references

## API Reference

### REST Endpoints

#### GET `/api/v1/rim/mesh/{nodeId}/timeline`

Query the RIM mesh at a specific point in time.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `nodeId` | string | Yes | - | Canonical RIM node ID |
| `asOf` | ISO-8601 DateTime | Yes | - | Point-in-time to query |
| `depth` | integer | No | 2 | Mesh traversal depth (1-5) |
| `vantageMode` | string | No | omniscient | Vantage perspective |
| `includeGovernance` | boolean | No | true | Include governance signals |

**Example:**

```bash
curl "http://localhost:8080/api/v1/rim/mesh/rim:entity:finance:EURUSD/timeline?asOf=2025-01-15T10:30:00Z&depth=2&vantageMode=omniscient"
```

**Response:**

```json
{
  "success": true,
  "data": {
    "rimNodeId": "rim:entity:finance:EURUSD",
    "requestedAsOf": "2025-01-15T10:30:00Z",
    "actualSnapshotTime": "2025-01-15T10:30:00Z",
    "mesh": {
      "eventNodeId": "rim:entity:finance:EURUSD",
      "connectedNodes": [
        {
          "nodeId": "rim:entity:finance:EURUSD",
          "type": "ENTITY",
          "label": "EUR/USD",
          "stateScore": 0.92,
          "status": "ACTIVE",
          "distance": 0
        },
        {
          "nodeId": "rim:region:geo:europe",
          "type": "REGION",
          "label": "Europe",
          "stateScore": 0.85,
          "status": "ACTIVE",
          "distance": 1
        }
      ],
      "relationships": [
        {
          "sourceNodeId": "rim:entity:finance:EURUSD",
          "targetNodeId": "rim:region:geo:europe",
          "relationshipType": "LOCATED_IN",
          "strength": 1.0
        }
      ],
      "nodeStateScores": {
        "rim:entity:finance:EURUSD": 0.92,
        "rim:region:geo:europe": 0.85
      },
      "governanceSignals": [],
      "meshDepth": 2,
      "totalNodes": 2,
      "generatedAt": "2025-01-15T10:30:00Z"
    },
    "capsuleReferences": [
      {
        "capsuleId": "cap-abc123",
        "scopeId": "rim:entity:finance:EURUSD",
        "snapshotTime": "2025-01-15T10:30:00Z",
        "resolutionSeconds": 60,
        "vantageMode": "omniscient",
        "revision": 42
      }
    ],
    "temporal": {
      "from": "2025-01-15T10:00:00Z",
      "to": "2025-01-15T10:30:00Z",
      "capsuleCount": 3,
      "coverageRatio": 0.95,
      "temporalGap": "PT0S",
      "interpolated": false
    }
  }
}
```

#### POST `/api/v1/rim/mesh/timeline`

Advanced time-travel query with full request body control.

**Request Body:**

```json
{
  "rimNodeId": "rim:entity:finance:EURUSD",
  "asOf": "2025-01-15T10:30:00Z",
  "depth": 3,
  "vantageMode": "omniscient",
  "includeGovernance": true
}
```

### GraphQL Queries

#### `rimMeshAtTime`

```graphql
query TimeTravelMesh($nodeId: ID!, $asOf: DateTime!, $depth: Int, $vantageMode: VantageMode) {
  rimMeshAtTime(
    nodeId: $nodeId
    asOf: $asOf
    depth: $depth
    vantageMode: $vantageMode
    includeGovernance: true
  ) {
    rimNodeId
    requestedAsOf
    actualSnapshotTime
    hasData
    mesh {
      eventNodeId
      connectedNodes {
        nodeId
        type
        label
        stateScore
        status
        distance
      }
      relationships {
        sourceNodeId
        targetNodeId
        relationshipType
        strength
      }
      totalNodes
      generatedAt
    }
    capsuleReferences {
      capsuleId
      scopeId
      snapshotTime
      revision
    }
    temporal {
      capsuleCount
      coverageRatio
      interpolated
    }
    degradationReasons
  }
}
```

**Variables:**

```json
{
  "nodeId": "rim:entity:finance:EURUSD",
  "asOf": "2025-01-15T10:30:00Z",
  "depth": 2,
  "vantageMode": "OMNISCIENT"
}
```

#### `rimTimeline`

Query a series of snapshots over a time range:

```graphql
query RimTimeline($nodeId: ID!, $from: DateTime!, $to: DateTime!) {
  rimTimeline(nodeId: $nodeId, from: $from, to: $to, limit: 10) {
    nodeId
    from
    to
    snapshots {
      nodeId
      snapshotTime
      stateScore
      trustConfidence
      coverageRatio
      capsuleReference {
        capsuleId
        revision
      }
    }
    inflectionPoints {
      timestamp
      type
      description
      magnitude
    }
    totalSnapshotsAvailable
  }
}
```

## Response Models

### TimeTravelMeshResponse

| Field | Type | Description |
|-------|------|-------------|
| `rimNodeId` | string | The queried canonical RIM node ID |
| `requestedAsOf` | DateTime | The requested point-in-time |
| `actualSnapshotTime` | DateTime? | The actual closest snapshot time |
| `mesh` | RimMeshView? | The reconstructed mesh view |
| `capsuleReferences` | CapsuleReference[] | CAPSULE snapshots used |
| `temporal` | TemporalMetadata | Query coverage metadata |
| `degradationReasons` | string[]? | Reasons for incomplete data |

### TemporalMetadata

| Field | Type | Description |
|-------|------|-------------|
| `from` | DateTime? | Earliest snapshot in reconstruction |
| `to` | DateTime? | Latest snapshot in reconstruction |
| `capsuleCount` | integer | Number of CAPSULEs used |
| `coverageRatio` | double | Data completeness (0.0-1.0) |
| `temporalGap` | string | Gap from requested time (ISO-8601 duration) |
| `interpolated` | boolean | Whether data was interpolated |

### CapsuleReference

| Field | Type | Description |
|-------|------|-------------|
| `capsuleId` | string? | Unique CAPSULE identifier |
| `scopeId` | string | Scope ID (= rimNodeId) |
| `snapshotTime` | DateTime | When snapshot was captured |
| `resolutionSeconds` | integer? | Resolution of the snapshot |
| `vantageMode` | string? | Vantage perspective |
| `revision` | integer? | Revision number in lineage |

## Identity Contracts

All time-travel operations adhere to the identity contracts defined in [BUTTERFLY_IDENTITY.md](../../BUTTERFLY_IDENTITY.md):

1. **RimNodeId Format**: `rim:{nodeType}:{namespace}:{localId}`
   - Validated on all API inputs
   - Preserved through CAPSULE projections
   - Used as the primary key for time-travel queries

2. **CAPSULE Scope Alignment**: `capsule.scopeId == rimNodeId`
   - Ensures consistent identity across services
   - Enables efficient CAPSULE history lookups

3. **Idempotency**: `SHA-256(rimNodeId + snapshotTime + resolutionSeconds + vantageMode)`
   - Deterministic key generation
   - Prevents duplicate CAPSULE snapshots
   - Enables safe replay/recovery

4. **Approved Node Types**: entity, region, market, actor, system, event, scope, relation, metric, signal
   - Validated in RimNodeId parsing
   - New types require ADR approval

5. **Approved Vantage Modes**: omniscient, observer, system, public
   - Filter historical data appropriately
   - Default to omniscient for complete view

## Error Handling

### Degraded Responses

When historical data is unavailable or incomplete, the API returns a degraded response:

```json
{
  "success": true,
  "data": {
    "rimNodeId": "rim:entity:finance:EURUSD",
    "requestedAsOf": "2020-01-01T00:00:00Z",
    "actualSnapshotTime": null,
    "mesh": null,
    "capsuleReferences": [],
    "temporal": {
      "capsuleCount": 0,
      "coverageRatio": 0.0,
      "interpolated": false
    },
    "degradationReasons": [
      "No historical data available at requested timestamp"
    ]
  }
}
```

### Error Codes

| Status | Code | Description |
|--------|------|-------------|
| 400 | INVALID_REQUEST | Invalid rimNodeId format |
| 400 | INVALID_TIMESTAMP | Invalid asOf timestamp |
| 400 | INVALID_DEPTH | Depth out of range (1-5) |
| 400 | INVALID_VANTAGE | Unknown vantage mode |
| 404 | NOT_FOUND | Node not found in RIM |
| 503 | CAPSULE_UNAVAILABLE | CAPSULE service unavailable |

## Metrics

The time-travel feature exposes the following Micrometer metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `perception.rim.mesh.timetravel` | Counter | Total time-travel queries |
| `perception.rim.mesh.timetravel.time` | Timer | Query latency distribution |
| `perception.rim.capsule.timetravel{status}` | Counter | CAPSULE history query results |
| `perception.rim.capsule.timetravel.errors` | Counter | CAPSULE history query failures |

## SLOs

| Metric | Target | Description |
|--------|--------|-------------|
| Time-travel query latency (p95) | < 1000ms | End-to-end query time |
| CAPSULE history retrieval (p95) | < 500ms | CAPSULE API call time |
| Mesh reconstruction (p95) | < 800ms | Local mesh building time |
| Temporal gap tolerance | < 5 minutes | Max gap between requested and actual snapshot |
| Coverage ratio (production) | > 0.8 | Data completeness target |

## Testing

### E2E Scenario

Run the automated M3 validation:

```bash
# Run the complete scenario
./scripts/run-e2e.sh --scenario=m3-rim-capsule-analyst-timetravel

# Or using the Java harness
java -jar butterfly-e2e/target/butterfly-e2e.jar \
  --scenario=m3-rim-capsule-analyst-timetravel.json \
  --env=local
```

### Contract Tests

```bash
# Run the M3 contract tests
./mvnw test -pl butterfly-e2e \
  -Dtest="RimCapsuleIdentityContractTest\$EndToEndTimeTravelTests"
```

### Manual Validation

See [RIM_CAPSULE_VALIDATION.md](../../PERCEPTION/docs/RIM_CAPSULE_VALIDATION.md#scenario-3--time-travel-analyst-view-m3) for step-by-step manual validation instructions.

## Configuration

### Application Properties

```yaml
perception:
  rim:
    timetravel:
      enabled: true
      default-depth: 2
      max-depth: 5
      default-history-window: PT1H
      max-history-window: P7D
      coverage-threshold: 0.5
      temporal-gap-tolerance-minutes: 5
    capsule:
      enabled: true
      base-url: http://capsule:8085
      history-path: /api/v1/history
```

### GraphQL Configuration

The GraphQL schema extensions are automatically loaded from:
```
classpath:graphql/rim-timetravel.graphqls
```

Ensure the GraphQL library is configured to scan this path.

## Sequence Diagram

```
┌────────┐    ┌─────────────┐    ┌───────────────────┐    ┌─────────┐
│ Analyst│    │  PERCEPTION │    │ RimMeshViewBuilder│    │ CAPSULE │
└───┬────┘    └──────┬──────┘    └─────────┬─────────┘    └────┬────┘
    │                │                     │                   │
    │ GET /mesh/{id}/timeline?asOf=T0      │                   │
    │───────────────────────────────────>  │                   │
    │                │                     │                   │
    │                │ buildHistoricalMeshView(id, T0, depth)  │
    │                │────────────────────>│                   │
    │                │                     │                   │
    │                │                     │ getHistoricalSnapshots(id, T0)
    │                │                     │──────────────────>│
    │                │                     │                   │
    │                │                     │  List<CapsuleDoc> │
    │                │                     │<──────────────────│
    │                │                     │                   │
    │                │                     │ findClosestSnapshot()
    │                │                     │ buildMeshFromCapsule()
    │                │                     │                   │
    │                │  TimeTravelMeshResponse                 │
    │                │<────────────────────│                   │
    │                │                     │                   │
    │ 200 OK + Response                    │                   │
    │<───────────────────────────────────  │                   │
    │                │                     │                   │
```

## Related Documentation

- [BUTTERFLY_IDENTITY.md](../../BUTTERFLY_IDENTITY.md) - Identity contracts
- [RIM_CAPSULE_VALIDATION.md](../../PERCEPTION/docs/RIM_CAPSULE_VALIDATION.md) - Validation scenarios
- [CAPSULE Service](../services/capsule.md) - CAPSULE service overview
- [RIM Service](../services/perception.md#rim) - RIM module documentation
- [ADR-0008: RIM Schema Evolution](../adr/0008-rim-schema-evolution.md) - Schema governance
