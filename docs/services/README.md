# BUTTERFLY Services Overview

> Individual service documentation and summaries

**Last Updated**: 2025-12-03  
**Target Audience**: Developers, architects, operators

---

## Overview

The BUTTERFLY ecosystem consists of six primary services, each with a specific responsibility in the cognitive intelligence platform.

## Service Catalog

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        BUTTERFLY Services                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                          NEXUS                                      │    │
│  │                   Integration Cortex                                │    │
│  │          Temporal Intelligence │ Reasoning │ Synthesis              │    │
│  │                         Port: 8084                                  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                  │                                          │
│       ┌──────────┬───────────────┼───────────────┬──────────┐              │
│       │          │               │               │          │              │
│       ▼          ▼               ▼               ▼          ▼              │
│  ┌────────┐ ┌────────┐    ┌──────────┐    ┌────────┐ ┌────────────────┐  │
│  │PERCEP- │ │CAPSULE │    │ ODYSSEY  │    │ PLATO  │ │butterfly-common│  │
│  │  TION  │ │        │    │          │    │        │ │                │  │
│  │        │ │   4D   │    │Strategic │    │Govern- │ │Shared Library  │  │
│  │ Sense  │ │ Memory │    │Cognition │    │  ance  │ │                │  │
│  │        │ │        │    │          │    │        │ │                │  │
│  │  8080  │ │  8081  │    │   8082   │    │  8083  │ │  (library)     │  │
│  └────────┘ └────────┘    └──────────┘    └────────┘ └────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Service Summary Table

| Service | Role | Primary Tech | Port | Documentation |
|---------|------|--------------|------|---------------|
| [**PERCEPTION**](perception.md) | Sensory and interpretation layer | Java 17, Kafka, PostgreSQL | 8080 | [Full Docs](../../PERCEPTION/README.md) |
| [**CAPSULE**](capsule.md) | 4D atomic history storage | Java 17, Cassandra, Redis | 8081 | [Full Docs](../../CAPSULE/README.md) |
| [**ODYSSEY**](odyssey.md) | Strategic cognition engine | Java 17, JanusGraph | 8082 | [Full Docs](../../ODYSSEY/README.md) |
| [**PLATO**](plato.md) | Governance and intelligence | Java 21, WebFlux, Cassandra | 8083 | [Full Docs](../../PLATO/README.md) |
| [**NEXUS**](nexus.md) | Integration cortex layer | Java 17, WebFlux | 8084 | [Full Docs](../../butterfly-nexus/README.md) |
| [**butterfly-common**](butterfly-common.md) | Shared library | Java 17, Avro | N/A | [Full Docs](../../butterfly-common/README.md) |

---

## Service Responsibilities

### PERCEPTION - "Sense the World"

**Mission**: Ingest, normalize, and interpret external information into trusted, structured signals.

**Key Capabilities**:
- Content acquisition (web crawling, APIs, feeds)
- Trust scoring and source validation
- Event detection and scenario generation
- Reality Integration Mesh (RIM) maintenance
- Weak signal detection

**Outputs**: Events, scored content, RIM updates, signals

### CAPSULE - "Remember History"

**Mission**: Store and retrieve 4D atomic units of history with full provenance and lineage.

**Key Capabilities**:
- 4D CAPSULE storage (Configuration, Dynamics, Agency, Counterfactual, Meta)
- Temporal queries and history retrieval
- Lineage and provenance tracking
- Multi-vantage perspective support
- Idempotent storage with deduplication

**Outputs**: Historical state, temporal slices, lineage chains

### ODYSSEY - "Understand Futures"

**Mission**: Model the world, project plausible futures, and understand actors.

**Key Capabilities**:
- World state modeling (World Field)
- Narrative future projection (Path Field)
- Actor/player modeling (Player Field)
- Ignorance surface tracking
- Experience learning from past episodes

**Outputs**: World state, path projections, actor models, strategic options

### PLATO - "Govern and Reason"

**Mission**: Enforce policies, coordinate AI engines, and provide governance.

**Key Capabilities**:
- Six AI engines (SynthPlane, FeatureForge, LawMiner, SpecMiner, ResearchOS, EvidencePlanner)
- Core primitives (Specs, Artifacts, Proofs, Plans)
- Policy enforcement and evaluation
- Research orchestration
- Evidence planning and proof generation

**Outputs**: Specs, artifacts, proofs, plans, governance decisions

### NEXUS - "Integrate All"

**Mission**: Unify all services with emergent cross-system capabilities.

**Key Capabilities**:
- Temporal Intelligence Fabric (unified queries)
- Autonomous Reasoning Engine (cross-system inference)
- Predictive Synthesis Core (strategic options)
- Evolution Controller (self-optimization)

**Outputs**: Temporal slices, integrated insights, strategic recommendations

### butterfly-common - "Shared Foundation"

**Mission**: Provide shared domain models, identity primitives, and event contracts.

**Key Capabilities**:
- RimNodeId canonical identity
- Avro schemas for events
- Kafka configuration helpers
- Idempotency key generation
- Shared domain models

**Outputs**: Libraries, schemas, configuration helpers

---

## Service Communication

### Event Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         Service Communication                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  PERCEPTION ─────────────────────────────────────────────────────────────▶│
│       │                                                                    │
│       │ perception.events                                                  │
│       │ perception.signals                                                 │
│       │ rim.fast-path                                                      │
│       │                                                                    │
│       └───────┬────────────────────┬────────────────────┬─────────────────│
│               │                    │                    │                  │
│               ▼                    ▼                    ▼                  │
│           CAPSULE              ODYSSEY               PLATO                │
│               │                    │                    │                  │
│               │capsule.snapshots   │odyssey.paths       │plato.governance │
│               │capsule.history     │odyssey.actors      │plato.plans      │
│               │                    │                    │                  │
│               └─────────┬──────────┴──────────┬─────────┘                  │
│                         │                     │                            │
│                         ▼                     ▼                            │
│                             NEXUS (aggregates all)                         │
│                                                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

### REST API Dependencies

| Service | Calls | Purpose |
|---------|-------|---------|
| NEXUS | CAPSULE, PERCEPTION, ODYSSEY, PLATO | Temporal fusion |
| ODYSSEY | CAPSULE | Historical context |
| PLATO | ODYSSEY | Strategic context |

---

## Quick Links

### Service Documentation

| Service | README | API Docs | Operations |
|---------|--------|----------|------------|
| PERCEPTION | [README](../../PERCEPTION/README.md) | [API](../../PERCEPTION/docs/api/) | [Ops](../../PERCEPTION/docs/operations/) |
| CAPSULE | [README](../../CAPSULE/README.md) | [API](../../CAPSULE/docs/api/) | [Ops](../../CAPSULE/docs/deployment/) |
| ODYSSEY | [README](../../ODYSSEY/README.md) | [API](../../ODYSSEY/docs/api/) | [Ops](../../ODYSSEY/docs/operations/) |
| PLATO | [README](../../PLATO/README.md) | [API](../../PLATO/docs/api/) | [Ops](../../PLATO/docs/operations/) |
| NEXUS | [README](../../butterfly-nexus/README.md) | [API](../../butterfly-nexus/README.md#api-surface) | - |

### Source Code

| Service | Location |
|---------|----------|
| PERCEPTION | `/PERCEPTION/` |
| CAPSULE | `/CAPSULE/` |
| ODYSSEY | `/ODYSSEY/` |
| PLATO | `/PLATO/` |
| NEXUS | `/butterfly-nexus/` |
| Common | `/butterfly-common/` |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Documentation Index](../index.md) | Main documentation portal |
| [Architecture Overview](../architecture/ecosystem-overview.md) | System architecture |
| [Data Flow](../architecture/data-flow.md) | Cross-service data flows |
| [API Catalog](../api/api-catalog.md) | Unified API reference |

