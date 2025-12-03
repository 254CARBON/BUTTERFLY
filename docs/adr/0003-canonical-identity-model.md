# ADR-0003: Canonical Identity Model (RimNodeId)

## Status

Accepted

## Date

2024-11-20

## Context

The BUTTERFLY platform processes information about entities from multiple domains:
- Financial instruments (EURUSD, SP500)
- Geographic regions
- Market structures
- Actors and agents
- System components

Each service needs to:
1. Reference the same entities consistently
2. Join data across services by entity ID
3. Validate entity references at ingestion
4. Generate deterministic keys for caching and deduplication

Without a canonical identity model:
- Services might use different ID formats for the same entity
- Joins across CAPSULE history and ODYSSEY world-state would fail
- Invalid entity references could propagate through the system
- Debugging cross-service issues would be difficult

## Decision

We will adopt a **canonical identity format called `RimNodeId`** with the structure:

```
rim:{nodeType}:{namespace}:{localId}
```

### Components

| Component | Constraints | Examples |
|-----------|-------------|----------|
| Prefix | Always `rim:` | `rim:` |
| nodeType | One of: `entity`, `region`, `market`, `actor`, `system` | `entity` |
| namespace | Lowercase alphanumeric + hyphens, 1-64 chars | `finance`, `us-equity` |
| localId | Alphanumeric + hyphens/underscores, 1-128 chars | `EURUSD`, `SP_500` |

### Validation Rules

- Maximum total length: 255 characters
- Namespace must be lowercase
- LocalId is case-sensitive
- No whitespace allowed
- Parsing is strict (throws on invalid input)

### API

```java
// Strict parsing (throws IllegalArgumentException)
RimNodeId nodeId = RimNodeId.parse("rim:entity:finance:EURUSD");

// Safe parsing
Optional<RimNodeId> maybeId = RimNodeId.tryParse(input);

// Validation
boolean valid = RimNodeId.isValid(input);

// Construction
RimNodeId nodeId = RimNodeId.of("entity", "finance", "EURUSD");
```

### Idempotency Key Generation

For deduplication, we generate deterministic keys:

```java
String key = IdempotencyKeyGenerator.forSnapshot(
    nodeId,
    snapshotTime,
    resolutionSeconds,
    vantageMode
);
// Returns SHA-256 of: rimNodeId|snapshotTime|resolutionSeconds|vantageMode
```

## Consequences

### Positive

- **Consistency**: Same entity = same ID everywhere
- **Joinability**: CAPSULE, ODYSSEY, PERCEPTION can correlate by ID
- **Validation**: Invalid IDs caught at ingestion boundary
- **Debuggability**: Human-readable IDs aid troubleshooting
- **Extensibility**: New node types can be added

### Negative

- **Migration**: Existing data may need ID normalization
- **Strictness**: Valid-looking IDs may be rejected
- **Learning curve**: Teams must learn the format

### Neutral

- Node types are fixed; adding new ones requires library update
- IDs are strings, not numeric (trade-off for readability)

## Alternatives Considered

### Alternative 1: UUIDs

Random UUIDs for all entities. Rejected because:
- Not human-readable
- No semantic meaning
- Hard to debug

### Alternative 2: Service-specific IDs

Each service defines its own ID format. Rejected because:
- No cross-service consistency
- Mapping tables required
- Higher integration complexity

### Alternative 3: URNs

Standard URN format (urn:butterfly:...). Rejected because:
- More verbose
- Overkill for internal use
- `rim:` prefix is more concise

## References

- [RimNodeId.java](../../butterfly-common/src/main/java/com/z254/butterfly/common/identity/RimNodeId.java)
- [IdempotencyKeyGenerator.java](../../butterfly-common/src/main/java/com/z254/butterfly/common/identity/IdempotencyKeyGenerator.java)
- [IDENTITY_AND_CONTRACTS.md](../../butterfly-common/IDENTITY_AND_CONTRACTS.md)

