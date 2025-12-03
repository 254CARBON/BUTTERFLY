# BUTTERFLY Ecosystem Overview

> Complete technical architecture of the BUTTERFLY platform

**Last Updated**: 2025-12-03  
**Target Audience**: Architects, senior developers, technical leads

---

## Executive Summary

BUTTERFLY is an enterprise cognitive intelligence platform consisting of six integrated services that work together to sense, remember, think, govern, and integrate information at scale. This document provides a comprehensive technical overview of the entire ecosystem.

---

## System Architecture

### High-Level View

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              BUTTERFLY PLATFORM                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ┌────────────────────────────────────────────────────────────────────────┐   │
│   │                           API Gateway Layer                              │   │
│   │   Load Balancer │ Rate Limiting │ Auth │ TLS Termination               │   │
│   └────────────────────────────────────────────────────────────────────────┘   │
│                                      │                                          │
│   ┌──────────────────────────────────┼──────────────────────────────────────┐  │
│   │                                  ▼                                       │  │
│   │   ┌─────────────────────────────────────────────────────────────────┐  │  │
│   │   │                         NEXUS                                    │  │  │
│   │   │              Unified Integration Layer                           │  │  │
│   │   │  ┌───────────────┬───────────────┬────────────────────────────┐│  │  │
│   │   │  │   Temporal    │   Reasoning   │      Synthesis             ││  │  │
│   │   │  │  Intelligence │    Engine     │        Core                ││  │  │
│   │   │  └───────────────┴───────────────┴────────────────────────────┘│  │  │
│   │   └─────────────────────────────────────────────────────────────────┘  │  │
│   │                                  │                                       │  │
│   │   ┌──────────────────────────────┼──────────────────────────────────┐  │  │
│   │   │                              ▼                                   │  │  │
│   │   │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐│  │  │
│   │   │  │ PERCEPTION │  │  CAPSULE   │  │  ODYSSEY   │  │   PLATO    ││  │  │
│   │   │  │            │  │            │  │            │  │            ││  │  │
│   │   │  │  Sensory   │  │   Memory   │  │ Cognition  │  │ Governance ││  │  │
│   │   │  │   Layer    │  │   Layer    │  │   Layer    │  │   Layer    ││  │  │
│   │   │  │            │  │            │  │            │  │            ││  │  │
│   │   │  │ :8080      │  │ :8081      │  │ :8082      │  │ :8083      ││  │  │
│   │   │  └────────────┘  └────────────┘  └────────────┘  └────────────┘│  │  │
│   │   │        │                │                │                │     │  │  │
│   │   └────────┼────────────────┼────────────────┼────────────────┼─────┘  │  │
│   │            │                │                │                │        │  │
│   │   ┌────────▼────────────────▼────────────────▼────────────────▼─────┐  │  │
│   │   │                    Apache Kafka                                  │  │  │
│   │   │   rim.fast-path │ perception.* │ capsule.* │ odyssey.* │ plato.*│  │  │
│   │   └─────────────────────────────────────────────────────────────────┘  │  │
│   │                                  │                                       │  │
│   │   ┌──────────────────────────────┼──────────────────────────────────┐  │  │
│   │   │                              ▼                                   │  │  │
│   │   │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐│  │  │
│   │   │  │ PostgreSQL │  │ Cassandra  │  │ JanusGraph │  │   Redis    ││  │  │
│   │   │  │            │  │            │  │            │  │            ││  │  │
│   │   │  │ PERCEPTION │  │  CAPSULE   │  │  ODYSSEY   │  │   Cache    ││  │  │
│   │   │  │   state    │  │  history   │  │   graph    │  │            ││  │  │
│   │   │  └────────────┘  └────────────┘  └────────────┘  └────────────┘│  │  │
│   │   │                                                                  │  │  │
│   │   │  ┌────────────┐  ┌────────────┐  ┌────────────────────────────┐│  │  │
│   │   │  │  Iceberg   │  │  Ignite    │  │     Schema Registry        ││  │  │
│   │   │  │ + Nessie   │  │   Cache    │  │                            ││  │  │
│   │   │  └────────────┘  └────────────┘  └────────────────────────────┘│  │  │
│   │   └─────────────────────────────────────────────────────────────────┘  │  │
│   │                             Data Layer                                   │  │
│   └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                             Observability Layer                                  │
│   ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────────────────┐  │
│   │ Prometheus │  │  Grafana   │  │  ClickHouse│  │    Distributed Tracing │  │
│   └────────────┘  └────────────┘  └────────────┘  └────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## UI Architecture

The BUTTERFLY ecosystem implements a deliberate separation between backend services and user interfaces:

### Service Categories

| Category | Services | UI Strategy |
|----------|----------|-------------|
| **Full-Stack** | PERCEPTION, CAPSULE | Dedicated native UI |
| **Background** | NEXUS, ODYSSEY, PLATO | Headless; reuse CAPSULE UI |

### UI Distribution

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        BUTTERFLY UI Architecture                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         User Interface Layer                          │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────┐  ┌─────────────────────────────┐   │  │
│  │  │       PERCEPTION UI         │  │        CAPSULE UI           │   │  │
│  │  │  (Dedicated Multiservice)   │  │   (Shared Management Portal)│   │  │
│  │  │                             │  │                             │   │  │
│  │  │  • Acquisition management   │  │  • History browsing         │   │  │
│  │  │  • Trust score dashboards   │  │  • Temporal queries         │   │  │
│  │  │  • Event monitoring         │  │  • Service configuration    │   │  │
│  │  │  • Signal visualization     │  │  • Governance dashboards    │   │  │
│  │  │  • RIM graph explorer       │  │  • World state views        │   │  │
│  │  │  • Source configuration     │  │  • Path projections         │   │  │
│  │  │                             │  │  • Plan execution monitor   │   │  │
│  │  └─────────────────────────────┘  └─────────────────────────────┘   │  │
│  │             │                                    │                   │  │
│  └─────────────┼────────────────────────────────────┼───────────────────┘  │
│                │                                    │                       │
│                ▼                                    ▼                       │
│  ┌─────────────────────┐      ┌────────────────────────────────────────┐  │
│  │     PERCEPTION      │      │         Backend Services               │  │
│  │   (Full-Stack)      │      │                                        │  │
│  │                     │      │  ┌──────────┐  ┌──────────┐           │  │
│  │   • Own UI          │      │  │ CAPSULE  │  │  NEXUS   │           │  │
│  │   • Own API         │      │  │ (native) │  │(headless)│           │  │
│  └─────────────────────┘      │  └──────────┘  └──────────┘           │  │
│                               │  ┌──────────┐  ┌──────────┐           │  │
│                               │  │ ODYSSEY  │  │  PLATO   │           │  │
│                               │  │(headless)│  │(headless)│           │  │
│                               │  └──────────┘  └──────────┘           │  │
│                               │                                        │  │
│                               │  All headless services managed via     │  │
│                               │  the shared CAPSULE UI portal          │  │
│                               └────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Design Rationale

- **PERCEPTION UI**: Dedicated multiservice interface for data acquisition workflows, trust scoring, event detection, and RIM management—operations unique to the sensory layer
- **CAPSULE UI**: Shared management portal providing unified access to history, temporal queries, and management interfaces for CAPSULE, NEXUS, ODYSSEY, and PLATO
- **Background Services (NEXUS, ODYSSEY, PLATO)**: Operate headlessly with API-only interfaces; all user-facing functionality consolidated in CAPSULE UI for a unified experience

---

## Service Deep Dives

### PERCEPTION - Sensory Layer

**Purpose**: Ingest, normalize, and interpret external information into trusted, structured signals.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PERCEPTION                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         Ingestion Tier                                │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │   │
│  │  │ Web Crawler  │  │  API Ingest  │  │     Camel Routes          │   │   │
│  │  │  (Nutch)     │  │              │  │  (Feeds, Files, Queues)   │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                   │                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       Processing Tier                                 │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │   │
│  │  │  Integrity   │  │ Intelligence │  │      Signals              │   │   │
│  │  │ Trust Score  │  │   Events     │  │   Weak Signal Detection   │   │   │
│  │  │ Dedup/Cluster│  │   Scenarios  │  │   Volatility/Horizon      │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                   │                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Reality Integration Mesh (RIM)                     │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │   │
│  │  │ World Model  │  │  CAPSULE     │  │   Ignorance Surface       │   │   │
│  │  │   Graph      │  │   Sync       │  │   Visibility Tracking     │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Storage: PostgreSQL (state) │ Iceberg/Nessie (lake) │ Ignite (cache)      │
│  Messaging: Kafka (perception.events, perception.signals)                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Modules**:
- `perception-acquisition` - Content ingestion
- `perception-integrity` - Trust scoring and deduplication
- `perception-intelligence` - Event detection and scenarios
- `perception-signals` - Weak signal detection
- `perception-rim` - Reality Integration Mesh

### CAPSULE - Memory Layer

**Purpose**: Store and retrieve 4D atomic units of history with full provenance.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                               CAPSULE                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        CAPSULE Structure                              │   │
│  │                                                                       │   │
│  │   ┌────────────────────────────────────────────────────────────┐    │   │
│  │   │                                                              │    │   │
│  │   │   ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  │    │   │
│  │   │   │ Configuration │  │   Dynamics    │  │    Agency     │  │    │   │
│  │   │   │      (C)      │  │      (D)      │  │      (A)      │  │    │   │
│  │   │   │               │  │               │  │               │  │    │   │
│  │   │   │ What exists   │  │ Rate of       │  │ Actor         │  │    │   │
│  │   │   │ and how       │  │ change        │  │ intentions    │  │    │   │
│  │   │   │ arranged      │  │ locally       │  │ and goals     │  │    │   │
│  │   │   └───────────────┘  └───────────────┘  └───────────────┘  │    │   │
│  │   │                                                              │    │   │
│  │   │   ┌───────────────┐  ┌────────────────────────────────────┐│    │   │
│  │   │   │Counterfactual │  │              Meta                  ││    │   │
│  │   │   │      (X)      │  │              (M)                   ││    │   │
│  │   │   │               │  │                                    ││    │   │
│  │   │   │ Alternative   │  │ Confidence, provenance, quality   ││    │   │
│  │   │   │ futures       │  │                                    ││    │   │
│  │   │   └───────────────┘  └────────────────────────────────────┘│    │   │
│  │   │                                                              │    │   │
│  │   └────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Key Concepts:                                                               │
│  • Immutable: CAPSULEs never change; corrections create new CAPSULEs       │
│  • Markov-at-resolution: Each CAPSULE sufficient for next-step reasoning   │
│  • Multi-perspective: Omniscient, observer-specific, system-specific       │
│  • Idempotent: Unique key = (rimNodeId + timestamp + resolution + vantage) │
│                                                                              │
│  Storage: Apache Cassandra (primary) │ Redis (cache)                        │
│  Messaging: Kafka (capsule.snapshots, capsule.history)                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### ODYSSEY - Cognition Layer

**Purpose**: Strategic understanding and future projection through world modeling.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                               ODYSSEY                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    World-Story State (Unified)                        │   │
│  │                                                                       │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │   │
│  │  │   World Field   │  │   Path Field    │  │   Player Field      │  │   │
│  │  │                 │  │                 │  │                     │  │   │
│  │  │ • Entities      │  │ • Narrative     │  │ • Actor models      │  │   │
│  │  │ • Relations     │  │   futures       │  │ • Objectives        │  │   │
│  │  │ • Regimes       │  │ • Branch points │  │ • Constraints       │  │   │
│  │  │ • Stress/Buffer │  │ • Probabilities │  │ • Strategies        │  │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │   │
│  │  │Experience Field │  │Ignorance Surface│  │Commitment Ledger    │  │   │
│  │  │                 │  │                 │  │                     │  │   │
│  │  │ • Past episodes │  │ • Low-data      │  │ • Explicit commits  │  │   │
│  │  │ • Post-mortems  │  │   regions       │  │ • Implicit commits  │  │   │
│  │  │ • Bias patterns │  │ • Weak models   │  │ • Credibility       │  │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Functional Engines                             │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────────┐│   │
│  │  │ State Core │ │ Path Engine│ │Actor Engine│ │ Experiment Engine  ││   │
│  │  │ Assimilate │ │ Narrative  │ │ Player     │ │ Strategic probes   ││   │
│  │  │ updates    │ │ generation │ │ modeling   │ │ hypothesis testing ││   │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────────────┘│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Integrations: HORIZONBOX (probabilistic foresight)                         │
│                QUANTUMROOM (game-theoretic analysis)                        │
│                                                                              │
│  Storage: JanusGraph (graph) │ Cassandra (episodes) │ Redis (cache)        │
│  Messaging: Kafka (odyssey.paths, odyssey.actors, rim.fast-path)           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### PLATO - Governance Layer

**Purpose**: Unified governance, AI coordination, and intelligent automation.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                PLATO                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     Core Primitives Registry                          │   │
│  │                                                                       │   │
│  │   ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐    │   │
│  │   │   Specs    │  │ Artifacts  │  │   Proofs   │  │   Plans    │    │   │
│  │   │            │  │            │  │            │  │            │    │   │
│  │   │ Versioned  │  │ Concrete   │  │ Machine-   │  │ Executable │    │   │
│  │   │ intents/   │  │ outputs:   │  │ checkable  │  │ workflows  │    │   │
│  │   │ contracts  │  │ transforms,│  │ evidence   │  │ producing  │    │   │
│  │   │            │  │ features   │  │            │  │ outputs    │    │   │
│  │   └────────────┘  └────────────┘  └────────────┘  └────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Six AI Engines                                 │   │
│  │                                                                       │   │
│  │   ┌────────────┐  ┌────────────┐  ┌────────────────────────────┐    │   │
│  │   │ SynthPlane │  │FeatureForge│  │        LawMiner            │    │   │
│  │   │            │  │            │  │                            │    │   │
│  │   │ Program    │  │ Feature    │  │ Equation discovery         │    │   │
│  │   │ synthesis  │  │ engineering│  │ Invariant mining           │    │   │
│  │   └────────────┘  └────────────┘  └────────────────────────────┘    │   │
│  │                                                                       │   │
│  │   ┌────────────┐  ┌────────────┐  ┌────────────────────────────┐    │   │
│  │   │ SpecMiner  │  │ ResearchOS │  │    EvidencePlanner         │    │   │
│  │   │            │  │            │  │                            │    │   │
│  │   │ Spec mining│  │ Research & │  │ Optimal evidence           │    │   │
│  │   │ & hardening│  │ hypothesis │  │ planning                   │    │   │
│  │   └────────────┘  └────────────┘  └────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Control Plane                                  │   │
│  │   Policy Enforcement │ Meta-Spec Governance │ RBAC │ Audit          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Storage: Cassandra/JanusGraph (registry) │ In-memory (fast path)          │
│  Messaging: Kafka (plato.governance, plato.plans)                           │
│  Real-time: WebSocket (/ws/plans/{planId})                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### NEXUS - Integration Layer

**Purpose**: Unified integration with emergent cross-system capabilities.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                NEXUS                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Four Integration Capabilities                      │   │
│  │                                                                       │   │
│  │  ┌────────────────────────────┐  ┌────────────────────────────────┐  │   │
│  │  │  Temporal Intelligence     │  │    Autonomous Reasoning        │  │   │
│  │  │         Fabric             │  │          Engine                 │  │   │
│  │  │                            │  │                                 │  │   │
│  │  │ • Unified past/present/    │  │ • Cross-system inference       │  │   │
│  │  │   future queries           │  │ • Contradiction detection      │  │   │
│  │  │ • Causal chain discovery   │  │ • Autonomous hypotheses        │  │   │
│  │  │ • Temporal anomaly detect  │  │ • Belief reconciliation        │  │   │
│  │  │                            │  │                                 │  │   │
│  │  │ Target: <100ms latency     │  │ Target: <500ms inference       │  │   │
│  │  └────────────────────────────┘  └────────────────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌────────────────────────────┐  ┌────────────────────────────────┐  │   │
│  │  │  Predictive Synthesis      │  │    Evolution Controller        │  │   │
│  │  │         Core               │  │                                 │  │   │
│  │  │                            │  │                                 │  │   │
│  │  │ • Strategic option         │  │ • Self-optimizing patterns     │  │   │
│  │  │   generation               │  │ • Closed-loop learning         │  │   │
│  │  │ • Cross-system ignorance   │  │ • Performance tracking         │  │   │
│  │  │   mapping                  │  │ • Auto-tuning                  │  │   │
│  │  │ • Governance-aware scoring │  │                                 │  │   │
│  │  │                            │  │ Target: 10% monthly improvement│  │   │
│  │  │ Target: <5s option gen     │  │                                 │  │   │
│  │  └────────────────────────────┘  └────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Clients: CapsuleClient, PerceptionClient, OdysseyClient, PlatoClient      │
│  Resilience: Circuit breakers, retries with backoff                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Infrastructure Components

### Apache Kafka

Central event backbone for async communication:

| Topic Category | Examples | Purpose |
|----------------|----------|---------|
| High-frequency | `rim.fast-path` | Real-time signals, HFT data |
| Domain events | `perception.events`, `capsule.snapshots` | Business events |
| System events | `*.dlq`, `*.notifications` | Operational events |

### Databases

| Database | Service | Use Case |
|----------|---------|----------|
| PostgreSQL | PERCEPTION | Operational state, source configs |
| Cassandra | CAPSULE, PLATO | Time-series history, registry |
| JanusGraph | ODYSSEY | Entity graph, relationships |
| Redis | All | Caching, session state |
| Apache Ignite | PERCEPTION | Distributed cache, feature store |
| Apache Iceberg | PERCEPTION | Data lakehouse, versioned data |

---

## Non-Functional Architecture

### Scalability

| Dimension | Approach |
|-----------|----------|
| Horizontal | Stateless services, Kafka partitioning |
| Vertical | JVM tuning, connection pooling |
| Data | Sharding (Cassandra), partitioning (Kafka) |
| Compute | Kubernetes HPA, pod autoscaling |

### Resilience

| Pattern | Implementation |
|---------|----------------|
| Circuit Breaker | Resilience4j per upstream |
| Retry | Exponential backoff with jitter |
| Timeout | Per-operation timeouts |
| Bulkhead | Thread pool isolation |
| Dead Letter | Per-topic DLQs with replay |

### Observability

| Category | Tools |
|----------|-------|
| Metrics | Micrometer → Prometheus → Grafana |
| Logging | SLF4J → JSON → aggregation |
| Tracing | OpenTelemetry (planned) |
| Alerting | Prometheus Alertmanager |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Data Flow](data-flow.md) | Cross-service data flows |
| [Communication Patterns](communication-patterns.md) | Integration patterns |
| [Identity Model](identity-model.md) | RimNodeId specification |
| [Security Architecture](security-architecture.md) | Security design |
| [Technology Stack](technology-stack.md) | Technology decisions |

