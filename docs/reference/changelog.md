# BUTTERFLY Changelog

> Version history and release notes

**Last Updated**: 2025-12-03

All notable changes to the BUTTERFLY ecosystem are documented here.

This project follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Added
- Unified documentation portal
- Comprehensive API documentation
- Operations runbooks
- Security hardening guide

### Changed
- Improved RimFastEvent schema with optional fields
- Enhanced circuit breaker configuration

### Fixed
- Consumer lag monitoring accuracy
- JWT token refresh edge cases

---

## [1.2.0] - 2024-11-15

### Added

#### CAPSULE
- Time-travel query optimization for large time ranges
- Batch capsule creation API
- Configurable TTL per entity type

#### ODYSSEY
- Actor influence scoring algorithm
- Path finding with weighted edges
- Temporal relationship tracking

#### PERCEPTION
- Multi-tenant data isolation
- Enhanced signal deduplication
- ClickHouse integration for analytics

#### PLATO
- ResearchOS workflow engine
- EvidencePlanner with proof generation
- Async plan execution with callbacks

#### NEXUS
- Request aggregation for batch operations
- Enhanced circuit breaker metrics
- WebSocket support for real-time updates

### Changed
- Standardized on Java 17 across services
- Spring Boot 3.2.x migration
- Improved Kafka consumer performance

### Fixed
- Memory leak in ODYSSEY graph traversal
- PERCEPTION event timestamp parsing
- PLATO engine queue deadlock

### Security
- CVE-2024-XXXXX: Updated jackson-databind
- Added rate limiting to all public endpoints
- Enhanced JWT validation

---

## [1.1.0] - 2024-08-20

### Added

#### CAPSULE
- Omniscient vantage mode
- Capsule comparison API
- Export to Parquet format

#### ODYSSEY
- Community detection algorithm
- Graph visualization endpoint
- Bulk entity import

#### PERCEPTION
- Apache Iceberg integration
- Project Nessie versioning
- Enhanced NLP pipeline

#### PLATO
- SynthPlane synthetic data engine
- FeatureForge transformations
- Governance policy validation

#### NEXUS
- Request tracing with correlation IDs
- Health aggregation endpoint
- Client SDK generation

### Changed
- Migrated from Kafka Avro to Confluent Schema Registry
- Improved startup time for all services
- Enhanced error messages in API responses

### Deprecated
- Legacy authentication endpoint (`/auth/login`)
- V0 API endpoints (removal in 2.0)

### Fixed
- Race condition in CAPSULE concurrent writes
- ODYSSEY index corruption on restart
- PERCEPTION duplicate event detection

---

## [1.0.0] - 2024-05-01

### Added

Initial release of BUTTERFLY ecosystem:

#### Core Services
- **CAPSULE**: 4D Atomic History Service
- **ODYSSEY**: Strategic Cognition Engine
- **PERCEPTION**: Sensory and Interpretation Layer
- **PLATO**: Governance and Intelligence Service
- **NEXUS**: Unified Integration Layer

#### Shared Components
- **butterfly-common**: Shared domain models and Avro schemas
- **butterfly-e2e**: End-to-end testing framework

#### Features
- RimNodeId canonical identity model
- Event-driven architecture with Kafka
- JWT and API key authentication
- Multi-tenant support
- OpenAPI documentation
- Prometheus metrics
- Distributed tracing

#### Infrastructure
- Docker Compose development setup
- Kubernetes deployment manifests
- Cassandra, PostgreSQL, Redis, JanusGraph support

---

## Migration Guides

### 1.1.x to 1.2.x

1. **Standardize on Java 17**
   ```bash
   # Update JAVA_HOME to a Java 17 LTS installation
   export JAVA_HOME=/path/to/jdk-17
   ```

2. **Spring Boot 3.2 Migration**
   - Update property: `spring.datasource.url` → `spring.datasource.jdbc-url`
   - Review deprecated API usage

3. **Schema Registry Changes**
   - Register new schemas before deployment
   - Use BACKWARD compatibility mode

### 1.0.x to 1.1.x

1. **Kafka Topic Changes**
   - New topic: `perception.signals`
   - Increased partitions on `rim.fast-path` (6 → 12)

2. **API Changes**
   - New header required: `X-Tenant-Id`
   - Pagination format changed

---

## Release Schedule

| Version | Release Date | Support Until |
|---------|--------------|---------------|
| 1.2.x | 2024-11-15 | 2025-11-15 |
| 1.1.x | 2024-08-20 | 2025-02-20 |
| 1.0.x | 2024-05-01 | 2024-11-01 (EOL) |

---

## Version Support Policy

- **Active**: Current major version receives features, fixes, security patches
- **Maintenance**: Previous major version receives fixes and security patches only
- **EOL**: No longer supported, upgrade required

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Reference Index](README.md) | Reference overview |
| [Migration Guide](../getting-started/installation.md) | Installation |
| [API Versioning](../api/versioning.md) | API versions |
