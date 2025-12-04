# BUTTERFLY Avro Schema Versioning Policy

## Overview

This document defines the versioning policy for Avro schemas in the BUTTERFLY ecosystem. All shared schemas live in `butterfly-common/src/main/avro/` and are the **single source of truth** for cross-service communication.

## Schema Ownership

| Location | Owner | Purpose |
|----------|-------|---------|
| `butterfly-common/src/main/avro/` | Platform Team | Shared contracts for all services |

**Rule**: No service may define its own `.avsc` files. All Avro schemas must be in `butterfly-common`.

## v1 Contract Freeze

The following schemas are **frozen at v1** as of Phase 1 completion:

| Schema | Version | Status | Breaking Changes |
|--------|---------|--------|------------------|
| `RimFastEvent.avsc` | 1.0.0 | **FROZEN** | Requires migration plan |
| `UnifiedEventHeader.avsc` | 1.0.0 | **FROZEN** | Requires migration plan |
| `NexusTemporalEvent.avsc` | 1.0.0 | **FROZEN** | Requires migration plan |
| `NexusReasoningEvent.avsc` | 1.0.0 | **FROZEN** | Requires migration plan |
| `NexusSynthesisEvent.avsc` | 1.0.0 | **FROZEN** | Requires migration plan |
| `NexusContext.avsc` | 1.0.0 | **FROZEN** | Requires migration plan |
| `PlatoEvent.avsc` | 1.0.0 | **FROZEN** | Requires migration plan |

## Compatibility Rules

### Backward Compatible Changes (Allowed)

These changes can be made without bumping the major version:

1. **Adding new fields with defaults**
   ```json
   {
     "name": "new_field",
     "type": ["null", "string"],
     "default": null,
     "doc": "New optional field"
   }
   ```

2. **Adding new enum values** (at the end of the symbols list)
   ```json
   {
     "type": "enum",
     "name": "EventType",
     "symbols": ["A", "B", "C", "NEW_VALUE"]
   }
   ```

3. **Widening union types** (adding to union, e.g., `"string"` → `["null", "string"]`)

4. **Adding documentation** (`doc` field changes)

5. **Changing field order** (Avro uses field names, not order)

### Breaking Changes (Require Migration)

These changes **require a major version bump** and migration plan:

1. **Removing fields** (even optional ones)
2. **Renaming fields**
3. **Changing field types** (e.g., `string` → `int`)
4. **Removing enum values**
5. **Changing schema name or namespace**
6. **Adding required fields** (fields without `default`)

## Version Format

Schemas use semantic versioning in the `schema_version` field:

```
MAJOR.MINOR.PATCH
```

- **MAJOR**: Breaking changes requiring consumer updates
- **MINOR**: Backward-compatible additions
- **PATCH**: Documentation or metadata changes

Example:
```json
{
  "name": "schema_version",
  "type": "string",
  "default": "1.2.0",
  "doc": "Schema version for backward compatibility"
}
```

## Migration Process for Breaking Changes

When a breaking change is necessary:

### 1. Create Migration Plan

Document in `docs/contracts/migrations/`:
- Affected services
- Data transformation requirements
- Rollback strategy
- Timeline

### 2. Dual-Write Phase

1. Create new schema version (e.g., `RimFastEventV2.avsc`)
2. Update producers to write to both old and new topics
3. Deploy consumer support for new schema

### 3. Migration Phase

1. Migrate historical data if needed
2. Update all consumers to read new schema
3. Deprecate old schema

### 4. Cleanup Phase

1. Remove old schema from code (after deprecation period)
2. Archive historical data in old format

## CI/CD Enforcement

The `contracts-guardrails.yml` workflow enforces:

1. **Schema Ownership**: All `.avsc` files must be in `butterfly-common`
2. **Syntax Validation**: Schemas must be valid Avro
3. **Backward Compatibility**: PRs are checked for breaking changes
4. **Version Presence**: Warns if `schema_version` field is missing

### Running Locally

```bash
# Validate schemas
cd butterfly-common
mvn validate

# Generate Java classes
mvn generate-sources

# Check compatibility (requires both versions)
./scripts/check-schema-compat.sh old.avsc new.avsc
```

## Schema Registry Configuration

For production with Confluent Schema Registry:

```yaml
# Recommended compatibility mode for BUTTERFLY schemas
compatibility: BACKWARD
```

This ensures:
- New schemas can read data written by old schemas
- Producers can be updated independently of consumers

## Topic-Schema Mapping

| Kafka Topic | Schema | Partitions | Retention |
|-------------|--------|------------|-----------|
| `rim.fast-path` | RimFastEvent | 12 | 7d |
| `rim.state.updates` | RimFastEvent | 6 | 14d |
| `perception.events` | PerceptionToCapsuleEvent | 6 | 7d |
| `perception.signals` | PerceptionSignalEvent | 6 | 14d |
| `perception.detections` | PerceptionDetectionResult | 6 | 14d |
| `capsule.snapshots` | CapsuleSnapshot* | 6 | 30d |
| `capsule.history` | CapsuleHistory* | 6 | 90d |
| `capsule.detections` | CapsuleDetectionEvent | 6 | 30d |
| `odyssey.paths` | NarrativePath* | 6 | 14d |
| `odyssey.actors` | ActorEvent* | 6 | 14d |
| `nexus.temporal.slices` | NexusTemporalEvent | 6 | 7d |
| `nexus.reasoning.*` | NexusReasoningEvent | 6 | 30d |
| `nexus.synthesis.*` | NexusSynthesisEvent | 6 | 7d |
| `plato.specs` | PlatoEvent | 6 | 90d |

*These schemas are defined in `butterfly-common` but may have service-specific extensions.

## PERCEPTION → CAPSULE Integration Schemas

The following schemas enable the PERCEPTION → CAPSULE data spine:

| Schema | Version | Purpose | Producer | Consumers |
|--------|---------|---------|----------|-----------|
| `PerceptionSignalEvent.avsc` | 1.0.0 | Signal detector outputs | PERCEPTION | NEXUS, CAPSULE |
| `PerceptionDetectionResult.avsc` | 1.0.0 | Detection result envelope | PERCEPTION | NEXUS, CAPSULE |
| `PerceptionToCapsuleEvent.avsc` | 1.0.0 | CAPSULE ingestion event | PERCEPTION | CAPSULE |
| `CapsuleDetectionEvent.avsc` | 1.0.0 | High-value detection output | CAPSULE | ODYSSEY, PLATO |

These schemas align with CAPSULE's temporal query capabilities:
- `scope_id` maps to CAPSULE's `scopeId` for entity-based queries
- `timestamp` uses `logicalType: timestamp-millis` for temporal slicing
- `resolution` in seconds for snapshot alignment
- `vantage_mode` for perspective-based querying (OMNISCIENT, OBSERVER_SPECIFIC, SYSTEM_SPECIFIC)

## Best Practices

1. **Always add defaults** for new fields
2. **Use union types** with `null` for optional fields
3. **Document every field** with the `doc` attribute
4. **Increment version** on every change
5. **Test compatibility** before merging
6. **Never remove fields** without a migration plan

## Contact

- **Schema Questions**: #butterfly-platform Slack channel
- **Breaking Change Requests**: Create an RFC in `docs/rfcs/`
- **Emergency Changes**: Contact Platform Team lead

---

*Last updated: Phase 1 Completion*
*Schema freeze effective: v1.0.0*

