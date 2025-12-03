# ADR-0002: Shared Contract Library (butterfly-common)

## Status

Accepted

## Date

2024-11-15

## Context

The BUTTERFLY ecosystem consists of multiple services that need to:

1. Share domain models for consistent data representation
2. Use the same event contracts (Avro schemas) for Kafka messaging
3. Apply consistent identity validation rules
4. Generate deterministic idempotency keys for deduplication
5. Handle failures consistently with shared DLQ infrastructure

Without centralization, each service would maintain its own copies of these contracts, leading to:
- Schema drift between services
- Inconsistent validation rules
- Duplicated code and maintenance burden
- Integration bugs from contract mismatches

## Decision

We will create a **shared library called `butterfly-common`** that serves as the single source of truth for:

1. **Avro Schemas**: All event contracts live in `src/main/avro/`
2. **Identity Primitives**: `RimNodeId` validation and parsing
3. **Idempotency**: `IdempotencyKeyGenerator` for deterministic hashing
4. **Kafka Helpers**: `RimKafkaContracts` for producer/consumer configuration
5. **DLQ Infrastructure**: `DlqPublisher`, `DlqRecord`, `DlqRetryService`

### Library Characteristics

- **Plain JAR**: No Spring Boot dependency (can be used anywhere)
- **Java 17**: Minimum JDK version across the ecosystem
- **Semantic versioning**: Breaking changes bump major version
- **Local install**: Services install via `mvn install` or CI artifact

### Usage Pattern

```xml
<dependency>
    <groupId>com.studioz254.carbon</groupId>
    <artifactId>butterfly-common</artifactId>
    <version>0.2.1</version>
</dependency>
```

## Consequences

### Positive

- **Single source of truth**: One place to update contracts
- **Type safety**: Generated Avro classes shared across services
- **Consistent validation**: Same `RimNodeId` rules everywhere
- **Reduced duplication**: DLQ handling code is reusable
- **Versioned contracts**: Clear upgrade path with semantic versioning

### Negative

- **Coordination overhead**: Changes require downstream updates
- **Build dependency**: Services must install butterfly-common first
- **Version management**: Teams must stay reasonably in sync
- **Release process**: Library releases gate service updates

### Neutral

- CI must build butterfly-common before dependent services
- Breaking changes require coordinated rollout

## Alternatives Considered

### Alternative 1: Copy-paste contracts per service

Each service maintains its own copy. Rejected because:
- High duplication
- Schema drift risk
- No single source of truth

### Alternative 2: Git submodules

Share code via Git submodules. Rejected because:
- Complex version management
- Poor IDE support
- Awkward CI integration

### Alternative 3: Monorepo

All services in one repository. Rejected because:
- Different teams, different release cycles
- Blast radius for changes too large
- Not all services are equally mature

## References

- [butterfly-common README.md](../../butterfly-common/README.md)
- [butterfly-common ARCHITECTURE.md](../../butterfly-common/ARCHITECTURE.md)
- [IDENTITY_AND_CONTRACTS.md](../../butterfly-common/IDENTITY_AND_CONTRACTS.md)

