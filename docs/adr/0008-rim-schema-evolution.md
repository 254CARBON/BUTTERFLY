# ADR-0008: RIM Schema Evolution Strategy

## Status

Proposed

## Date

2025-12-04

## Context

The Reality Integration Mesh (RIM) is PERCEPTION's world model layer, maintaining canonical identities (RimNodeId), node states, relationships, and projections to CAPSULE. Multiple downstream systems depend on RIM's identity and schema contracts:

- **CAPSULE**: Stores immutable snapshots using `scope_id` = RimNodeId
- **ODYSSEY**: Consumes RIM node states for world model construction
- **NEXUS**: Orchestrates temporal queries using RimNodeId as correlation key
- **PLATO**: Evaluates governance rules referencing RIM entities

As the platform evolves, RIM schemas may need changes:

1. **New node types**: Adding categories like `metric`, `signal`, or domain-specific types
2. **New relationship types**: Supporting additional semantic connections
3. **Schema field additions**: Extending node/state payloads with new attributes
4. **Namespace expansions**: Adding new domain namespaces

Without a clear evolution strategy:
- Breaking changes could cascade across all consuming services
- Historical CAPSULE data could become orphaned or incompatible
- Cross-service identity resolution could fail silently
- Migration paths would be ad-hoc and error-prone

## Decision

We adopt a **controlled schema evolution strategy** for RIM with the following principles:

### 1. RimNodeId Format is Frozen

The canonical identity format is **frozen** and cannot be modified without a major version bump and full migration:

```
rim:{nodeType}:{namespace}:{localId}
rim-scope:{namespace}:{scopeName}
```

**Frozen aspects:**
- The `rim:` and `rim-scope:` prefixes
- The four-part structure for RimNodeId
- The three-part structure for rim-scope
- Character constraints (lowercase nodeType/namespace, alphanumeric localId)

### 2. Node Type Changes Require ADR Approval

Adding or deprecating node types requires:

1. **Proposal**: Create an RFC in `docs/rfcs/` documenting the need
2. **Impact Analysis**: Document affected services and migration requirements
3. **ADR Approval**: Tech lead approval and ADR update
4. **Consumer Notification**: All consuming services notified 2 sprints before rollout
5. **Contract Test Update**: `RimCapsuleIdentityContractTest` updated with new types

**Current approved node types** (from `ScopeValidator.APPROVED_NODE_TYPES`):
- `entity`, `region`, `market`, `actor`, `system`
- `event`, `scope`, `relation`, `metric`, `signal`

### 3. Schema Changes Follow butterfly-common Versioning

RIM-related schemas in `butterfly-common` follow the existing versioning policy:

| Change Type | Compatibility | Action Required |
|-------------|---------------|-----------------|
| Add optional field with default | Backward compatible | Minor version bump |
| Add new enum value (end of list) | Backward compatible | Minor version bump |
| Add documentation | Compatible | Patch version bump |
| Remove field | Breaking | Major version + migration plan |
| Rename field | Breaking | Major version + migration plan |
| Change field type | Breaking | Major version + migration plan |
| Remove enum value | Breaking | Major version + migration plan |

### 4. Backward Compatibility Enforced via JSON Schema Validation

All RIM node and state payloads are validated against JSON schemas:

- `perception-rim/src/main/resources/schemas/rim-node-schema.json`
- `perception-rim/src/main/resources/schemas/rim-node-state-schema.json`
- `perception-rim/src/main/resources/schemas/rim-capsule-envelope-schema.json`

Schema changes must:
1. Pass JSON Schema Draft-07 validation
2. Maintain backward compatibility with existing documents
3. Include `schemaVersion` field for version tracking

### 5. Cross-System Compatibility Tested in butterfly-e2e

All schema changes must pass integration tests before merge:

1. **Contract Tests**: `RimCapsuleIdentityContractTest` validates identity alignment
2. **E2E Scenarios**: `rim-capsule-identity-contract.json` exercises full flow
3. **Golden Path**: Existing perception-capsule golden path must pass
4. **CI Enforcement**: `contracts-guardrails.yml` workflow blocks breaking changes

### 6. Migration Process for Breaking Changes

When breaking changes are unavoidable:

```
Phase 1: Dual Support (2-4 sprints)
├── Add support for new format/schema
├── Continue supporting old format
├── Log deprecation warnings
└── Update documentation

Phase 2: Migration (2 sprints)
├── Run migration scripts for historical data
├── Update all consumers to new format
├── Monitor for migration failures
└── DLQ replay for failed migrations

Phase 3: Cleanup (1 sprint)
├── Remove old format support
├── Archive migration scripts
├── Update documentation
└── Close migration tracking issue
```

### 7. Relationship Type Extensions

New relationship types can be added to `RimRelationshipType` enum with:
- Documentation of semantic meaning
- Category classification (CAUSAL, STRUCTURAL, ASSOCIATIVE, BEHAVIORAL)
- Symmetric flag where applicable
- Consumer notification (less strict than node types)

## Consequences

### Positive

- **Stability**: Downstream consumers have guaranteed contract stability
- **Predictability**: Clear process for any schema changes
- **Traceability**: All changes documented via ADRs and RFCs
- **Safety**: CI enforcement prevents accidental breaking changes
- **Clarity**: Single source of truth for approved types and formats

### Negative

- **Velocity**: Schema changes require more process overhead
- **Coordination**: Cross-team notification adds communication burden
- **Rigidity**: Emergency changes still require ADR (expedited review)

### Neutral

- Node type additions are expected to be rare (1-2 per year)
- Relationship type additions are more common but lower risk
- Most feature work doesn't require schema changes

## Alternatives Considered

### Alternative 1: Fully Dynamic Schemas

Allow arbitrary node types and relationship types without validation.

**Rejected because:**
- No contract guarantees for consumers
- Debugging cross-service issues becomes difficult
- Historical data could have inconsistent formats
- CAPSULE scope_id alignment would be unreliable

### Alternative 2: Strict Immutability (No Changes Ever)

Freeze all schemas permanently, never allow changes.

**Rejected because:**
- Platform would become unable to evolve
- New use cases couldn't be supported
- Workarounds would create technical debt

### Alternative 3: Per-Service Schema Ownership

Each service defines its own RIM-related schemas.

**Rejected because:**
- Violates ADR-0002 (shared contract library)
- Creates mapping complexity
- Increases risk of drift and incompatibility

## References

- [BUTTERFLY_IDENTITY.md](../../PERCEPTION/BUTTERFLY_IDENTITY.md) - Canonical identity specification
- [ADR-0002: Shared Contract Library](0002-shared-contract-library.md) - butterfly-common as single source
- [ADR-0003: Canonical Identity Model](0003-canonical-identity-model.md) - RimNodeId format decision
- [Schema Versioning Policy](../contracts/SCHEMA_VERSIONING.md) - Avro schema versioning
- [Contract Freeze Policy](../contracts/FREEZE_POLICY.md) - Freeze period governance
- [RIM-CAPSULE Validation](../../PERCEPTION/docs/RIM_CAPSULE_VALIDATION.md) - Integration testing guide
