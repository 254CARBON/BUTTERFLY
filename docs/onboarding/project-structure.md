# BUTTERFLY Project Structure

> Understanding the monorepo layout and service architecture

**Last Updated**: 2025-12-03

---

## Monorepo Overview

The BUTTERFLY ecosystem is organized as a monorepo containing multiple services, shared libraries, and tooling:

```
BUTTERFLY/apps/
├── butterfly-common/        # Shared library (contracts, utilities)
├── butterfly-e2e/           # End-to-end test harness
├── butterfly-nexus/         # Knowledge graph service
├── CAPSULE/                 # 4D historical storage service
├── ODYSSEY/                 # Strategic cognition service
├── PERCEPTION/              # Reality integration mesh
├── PLATO/                   # Governance and intelligence service
├── docs/                    # Cross-cutting documentation
│   ├── adr/                 # Architecture Decision Records
│   └── onboarding/          # This guide
├── scripts/                 # Development scripts
├── .github/workflows/       # CI/CD pipelines
├── pom.xml                  # Parent POM with shared config
├── commitlint.config.js     # Commit message enforcement
└── package.json             # Node.js tooling (Husky, etc.)
```

---

## Service Architecture

### PLATO - Governance and Intelligence

PLATO is the governance backbone, managing Specs, Artifacts, Proofs, and Plans.

```
PLATO/
├── src/main/java/com/z254/butterfly/plato/
│   ├── api/                 # REST controllers and WebSocket handlers
│   │   ├── v1/              # Versioned API endpoints
│   │   ├── websocket/       # Real-time event streaming
│   │   └── exception/       # Exception handlers
│   ├── cache/               # Caching infrastructure
│   ├── config/              # Spring configuration
│   ├── domain/              # Domain models and repositories
│   │   ├── model/           # Spec, Artifact, Proof, Plan entities
│   │   └── repository/      # Data access layer
│   ├── engine/              # Six PLATO engines
│   │   ├── synthplane/      # Program synthesis
│   │   ├── featureforge/    # Feature engineering
│   │   ├── lawminer/        # Equation discovery
│   │   ├── specminer/       # Specification mining
│   │   ├── researchos/      # Research orchestration
│   │   └── evidenceplanner/ # Evidence planning
│   ├── event/               # Event publishing and broadcasting
│   ├── execution/           # Plan execution framework
│   ├── governance/          # Control plane and policies
│   ├── identity/            # ID types (PlatoObjectId hierarchy)
│   ├── infrastructure/      # Cassandra, Kafka, JanusGraph
│   ├── integration/         # External service clients
│   ├── observability/       # Metrics, tracing, logging
│   ├── security/            # Auth, RBAC, API keys
│   └── service/             # Business logic services
├── docs/                    # PLATO documentation
├── openapi/                 # OpenAPI specifications
└── schema/                  # Database schemas
```

### PERCEPTION - Reality Integration Mesh

PERCEPTION handles data acquisition, signal detection, and intelligence.

```
PERCEPTION/
├── perception-acquisition/  # Data ingestion (Camel routes)
├── perception-api/          # REST API gateway
├── perception-briefing/     # Briefing generation
├── perception-common/       # Shared utilities
├── perception-drift/        # Drift detection
├── perception-evidence/     # Evidence management
├── perception-features/     # Feature extraction
├── perception-feedback/     # Feedback loops
├── perception-integrity/    # Trust scoring
├── perception-intelligence/ # RIM and signal fusion
├── perception-knowledge/    # Knowledge enrichment
├── perception-models/       # Domain models
├── perception-monitoring/   # Pipeline monitoring
├── perception-narrative/    # Narrative generation
├── perception-pipeline/     # Processing pipelines
├── perception-portal/       # Web UI (Next.js)
├── perception-predictive/   # Predictive awareness
├── perception-provenance/   # Provenance tracking
├── perception-reasoning/    # Reasoning engine
├── perception-rim/          # Reality Integration Mesh
├── perception-scenarios/    # Scenario composition
├── perception-signals/      # Weak signal detection
├── perception-temporal/     # Temporal analysis
├── perception-visualization/# Visualization components
├── clients/                 # SDK clients (Python, TypeScript)
├── docs/                    # PERCEPTION documentation
└── observability/           # Grafana dashboards, alerts
```

### CAPSULE - 4D Historical Storage

CAPSULE provides temporal data storage with point-in-time queries.

```
CAPSULE/
├── src/main/java/           # Core service
├── capsule-sdk-java/        # Java SDK
├── capsule-sdk-python/      # Python SDK
├── capsule-sdk-typescript/  # TypeScript SDK
├── capsule-ui/              # Admin UI
├── docs/                    # Documentation
├── infra/                   # Infrastructure configs
└── k8s/                     # Kubernetes manifests
```

### ODYSSEY - Strategic Cognition

ODYSSEY provides strategic planning and futures analysis.

```
ODYSSEY/
├── odyssey-core/            # Core service
├── odyssey-common/          # Shared utilities
├── horizonbox/              # Scenario planning
├── quantumroom/             # Decision simulation
├── openapi/                 # API specifications
└── docs/                    # Documentation
```

---

## Shared Components

### butterfly-common

Shared library used by all services:

```
butterfly-common/
├── src/main/java/com/z254/butterfly/common/
│   ├── contract/            # Shared contracts (events, DTOs)
│   ├── identity/            # Canonical identity model
│   ├── kafka/               # Kafka utilities
│   ├── observability/       # Shared metrics/tracing
│   └── util/                # Common utilities
```

**Key contracts:**
- `FastPathEvent` - High-frequency event format
- `ButterflyIdentity` - Canonical ID format (`rim:{type}:{domain}:{localId}`)

### butterfly-e2e

End-to-end test harness:

```
butterfly-e2e/
├── scenarios/               # Test scenario definitions
│   ├── catalog.json         # Scenario catalog
│   ├── fastpath-*.json      # Fast path scenarios
│   └── nexus-*.json         # Nexus scenarios
├── src/test/java/           # Integration tests
├── docker-compose.yml       # Test infrastructure
└── run-scenarios.sh         # Test runner script
```

---

## Module Dependencies

```
                    ┌─────────────────┐
                    │ butterfly-common│
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
   ┌─────────┐         ┌──────────┐         ┌─────────┐
   │  PLATO  │◄───────►│PERCEPTION│◄───────►│ CAPSULE │
   └────┬────┘         └────┬─────┘         └────┬────┘
        │                   │                    │
        │                   │                    │
        └───────────────────┼────────────────────┘
                            │
                            ▼
                      ┌──────────┐
                      │ ODYSSEY  │
                      └──────────┘
```

**Integration patterns:**
- PLATO ↔ PERCEPTION: Specs attach to RIM nodes, evidence bundles become Proofs
- PLATO ↔ CAPSULE: Historical data for research studies, temporal queries
- PLATO ↔ ODYSSEY: Strategic context for ResearchOS, experiment registration
- All services → Kafka: Event-driven communication

---

## Build System

### Maven Structure

The parent [pom.xml](../../pom.xml) provides:
- Dependency management (Spring Boot, Testcontainers, etc.)
- Plugin management (compiler, surefire, jacoco, checkstyle)
- Shared properties (Java version, library versions)

Each service has its own `pom.xml` that inherits from parent:
- PLATO inherits from `spring-boot-starter-parent`
- PERCEPTION uses multi-module structure with parent POM

### Build Commands

```bash
# Build entire ecosystem
mvn clean install -DskipTests

# Build specific service
mvn -f PLATO/pom.xml clean package

# Build with tests
mvn -f PLATO/pom.xml verify

# Build with coverage
mvn -f PLATO/pom.xml test jacoco:report
```

---

## Configuration

### Profiles

Each service supports Spring profiles:
- `dev` - Local development with in-memory stores
- `test` - Test execution with Testcontainers
- `prod` - Production settings

### Environment Variables

Common environment variables:

| Variable | Service | Purpose |
|----------|---------|---------|
| `PLATO_PERSISTENCE_TYPE` | PLATO | `inmemory` or `cassandra` |
| `CASSANDRA_HOSTS` | PLATO | Cassandra contact points |
| `KAFKA_BOOTSTRAP_SERVERS` | All | Kafka broker addresses |
| `JWT_SECRET` | All | JWT signing secret |
| `SPRING_PROFILES_ACTIVE` | All | Active profile |

---

## CI/CD Structure

GitHub Actions workflows:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `plato-ci.yml` | PR to PLATO/ | Build, test, coverage |
| `perception-ci.yml` | PR to PERCEPTION/ | Build, test, coverage |
| `butterfly-e2e.yml` | PR to any | E2E integration tests |
| `security-scan.yml` | Nightly | Dependency vulnerability scan |
| `deploy.yml` | Merge to main | Deployment pipeline |

---

## Next Steps

- Explore the [Package Guide](package-guide.md) for deep dives into PLATO's packages
- Set up your environment with [Common Workflows](common-workflows.md)
- Review [Architecture Decision Records](../adr/) for design rationale

