# BUTTERFLY Architecture Overview

> A high-level introduction to the BUTTERFLY ecosystem architecture

**Last Updated**: 2025-12-03  
**Target Audience**: Architects, developers, technical evaluators

---

## Overview

BUTTERFLY is a distributed cognitive intelligence platform built on event-driven microservices architecture. This document provides a high-level overview of the system design, service responsibilities, and integration patterns.

For detailed technical architecture, see [Ecosystem Overview](../architecture/ecosystem-overview.md).

---

## The Cognitive Model

BUTTERFLY implements a cognitive architecture inspired by human cognition:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BUTTERFLY COGNITIVE MODEL                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│    External World                                                            │
│         │                                                                    │
│         ▼                                                                    │
│    ┌─────────────────────────────────────────────────────────────────┐     │
│    │                      PERCEPTION (Sensory)                        │     │
│    │   • Ingest external signals           • Score trust             │     │
│    │   • Normalize and enrich             • Detect events            │     │
│    │   • Build Reality Integration Mesh    • Generate scenarios       │     │
│    └─────────────────────────────────┬───────────────────────────────┘     │
│                                      │                                       │
│                                      ▼                                       │
│    ┌─────────────────────────────────────────────────────────────────┐     │
│    │                       CAPSULE (Memory)                           │     │
│    │   • Store 4D atomic history          • Maintain lineage         │     │
│    │   • Query temporal state             • Track provenance         │     │
│    │   • Provide historical context       • Support replay           │     │
│    └─────────────────────────────────┬───────────────────────────────┘     │
│                                      │                                       │
│                                      ▼                                       │
│    ┌─────────────────────────────────────────────────────────────────┐     │
│    │                      ODYSSEY (Cognition)                         │     │
│    │   • Model the world state            • Project future paths     │     │
│    │   • Understand actors/players        • Learn from outcomes      │     │
│    │   • Synthesize strategic options     • Manage uncertainty       │     │
│    └─────────────────────────────────┬───────────────────────────────┘     │
│                                      │                                       │
│                                      ▼                                       │
│    ┌─────────────────────────────────────────────────────────────────┐     │
│    │                       PLATO (Governance)                         │     │
│    │   • Enforce policies                 • Coordinate AI engines    │     │
│    │   • Generate proofs                  • Manage specifications    │     │
│    │   • Orchestrate research             • Plan evidence gathering  │     │
│    └─────────────────────────────────┬───────────────────────────────┘     │
│                                      │                                       │
│                                      ▼                                       │
│    ┌─────────────────────────────────────────────────────────────────┐     │
│    │                    NEXUS (Integration Cortex)                    │     │
│    │   • Temporal intelligence fusion     • Cross-system reasoning   │     │
│    │   • Strategic option synthesis       • Self-evolution           │     │
│    └─────────────────────────────────────────────────────────────────┘     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Service Architecture

### PERCEPTION - The Sensory Layer

**Purpose**: Ingest, normalize, and interpret external information

```
┌─────────────────────────────────────────────────────────────────────┐
│                          PERCEPTION                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │  Acquisition │  │  Integrity   │  │    Event Intelligence    │  │
│  │              │  │              │  │                          │  │
│  │ • Web crawl  │  │ • Trust score│  │ • Event detection        │  │
│  │ • API ingest │  │ • Dedup      │  │ • Scenario generation    │  │
│  │ • Feeds      │  │ • Storylines │  │ • Impact estimation      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │   Signals    │  │  Monitoring  │  │   Reality Integration    │  │
│  │              │  │              │  │        Mesh (RIM)        │  │
│  │ • Weak sigs  │  │ • Health     │  │                          │  │
│  │ • Volatility │  │ • Self-audit │  │ • World model graph      │  │
│  │ • Horizons   │  │ • Telemetry  │  │ • CAPSULE sync           │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Technologies**: Apache Kafka, Apache Camel, PostgreSQL, Apache Ignite

### CAPSULE - The Memory Layer

**Purpose**: Store and retrieve 4D atomic units of history

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CAPSULE                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  A CAPSULE represents one "chunk" of reality with five components:  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  │  │
│  │  │ Configuration  │  │   Dynamics     │  │    Agency      │  │  │
│  │  │       (C)      │  │      (D)       │  │      (A)       │  │  │
│  │  │                │  │                │  │                │  │  │
│  │  │ What exists    │  │ How it changes │  │ Actor intents  │  │  │
│  │  └────────────────┘  └────────────────┘  └────────────────┘  │  │
│  │                                                                │  │
│  │  ┌────────────────┐  ┌────────────────┐                      │  │
│  │  │ Counterfactual │  │     Meta       │                      │  │
│  │  │       (X)      │  │      (M)       │                      │  │
│  │  │                │  │                │                      │  │
│  │  │ Alternative    │  │ Confidence,    │                      │  │
│  │  │ futures        │  │ provenance     │                      │  │
│  │  └────────────────┘  └────────────────┘                      │  │
│  │                                                                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  Storage: Apache Cassandra + Redis cache                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Technologies**: Apache Cassandra, Redis, Spring Boot

### ODYSSEY - The Cognition Layer

**Purpose**: Strategic understanding and future projection

```
┌─────────────────────────────────────────────────────────────────────┐
│                           ODYSSEY                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  World-Story State (Continuously Updated)                           │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ World Field │  │ Path Field  │  │   Player Field      │   │  │
│  │  │             │  │             │  │                     │   │  │
│  │  │ Entities,   │  │ Narrative   │  │ Actor models,       │   │  │
│  │  │ relations,  │  │ futures,    │  │ objectives,         │   │  │
│  │  │ regimes     │  │ branches    │  │ strategies          │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  │                                                                │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ Experience  │  │ Ignorance   │  │   Commitment        │   │  │
│  │  │   Field     │  │  Surface    │  │     Ledger          │   │  │
│  │  │             │  │             │  │                     │   │  │
│  │  │ Past        │  │ What we     │  │ Explicit/implicit   │   │  │
│  │  │ episodes    │  │ don't know  │  │ commitments         │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  │                                                                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  Integrations: HORIZONBOX (probabilistic foresight)                 │
│                QUANTUMROOM (game-theoretic analysis)                │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Technologies**: JanusGraph, Apache Kafka, Spring Boot

### PLATO - The Governance Layer

**Purpose**: Governance, AI coordination, and intelligent automation

```
┌─────────────────────────────────────────────────────────────────────┐
│                            PLATO                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Six Integrated Engines                                              │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ SynthPlane  │  │FeatureForge│  │     LawMiner        │   │  │
│  │  │             │  │             │  │                     │   │  │
│  │  │ Program     │  │ Feature     │  │ Equation            │   │  │
│  │  │ synthesis   │  │ engineering │  │ discovery           │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  │                                                                │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ SpecMiner   │  │ ResearchOS  │  │  EvidencePlanner    │   │  │
│  │  │             │  │             │  │                     │   │  │
│  │  │ Spec mining │  │ Research &  │  │ Optimal evidence    │   │  │
│  │  │ & hardening │  │ hypothesis  │  │ planning            │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  │                                                                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  Core Primitives: Specs │ Artifacts │ Proofs │ Plans               │
│                                                                      │
│  Control Plane: Policy enforcement, meta-spec governance           │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Technologies**: Spring Boot WebFlux, Cassandra, JanusGraph

### NEXUS - The Integration Cortex

**Purpose**: Unify all services with emergent capabilities

```
┌─────────────────────────────────────────────────────────────────────┐
│                            NEXUS                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Four Integration Capabilities                                       │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  ┌────────────────────────┐  ┌────────────────────────────┐  │  │
│  │  │ Temporal Intelligence  │  │   Autonomous Reasoning     │  │  │
│  │  │        Fabric          │  │        Engine              │  │  │
│  │  │                        │  │                            │  │  │
│  │  │ Past/Present/Future    │  │ Cross-system inference     │  │  │
│  │  │ unified queries        │  │ Contradiction detection    │  │  │
│  │  └────────────────────────┘  └────────────────────────────┘  │  │
│  │                                                                │  │
│  │  ┌────────────────────────┐  ┌────────────────────────────┐  │  │
│  │  │  Predictive Synthesis  │  │    Evolution Controller    │  │  │
│  │  │         Core           │  │                            │  │  │
│  │  │                        │  │                            │  │  │
│  │  │ Strategic option       │  │ Self-optimizing patterns   │  │  │
│  │  │ generation             │  │ Continuous learning        │  │  │
│  │  └────────────────────────┘  └────────────────────────────┘  │  │
│  │                                                                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Technologies**: Spring Boot WebFlux, Resilience4j, Apache Kafka

---

## Communication Architecture

### Event-Driven Backbone

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Apache Kafka Event Backbone                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Topic Categories:                                                   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ High-Frequency Topics (rim.fast-path)                         │  │
│  │ • Real-time signals and metrics                               │  │
│  │ • Sub-second latency requirements                             │  │
│  │ • Avro serialization with Schema Registry                     │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ Domain Event Topics                                           │  │
│  │ • perception.events    - Detected events and scenarios        │  │
│  │ • capsule.snapshots    - Historical state changes             │  │
│  │ • odyssey.paths        - Path weight updates                  │  │
│  │ • plato.governance     - Policy evaluations                   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ Dead Letter Queues (DLQ)                                      │  │
│  │ • Service-specific DLQs for failed message handling           │  │
│  │ • Replay capability for recovery                              │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Synchronous APIs

| Service | REST API | WebSocket | GraphQL |
|---------|----------|-----------|---------|
| PERCEPTION | `/api/v1/*` | - | Planned |
| CAPSULE | `/api/v1/capsules/*` | - | - |
| ODYSSEY | `/api/v1/state/*` | Planned | `/graphql` |
| PLATO | `/api/v1/*` | `/ws/plans/*` | - |
| NEXUS | `/api/v1/*` | Planned | - |

---

## Identity Model

### Canonical RimNodeId

All entities across BUTTERFLY use a canonical identifier format:

```
rim:{nodeType}:{namespace}:{localId}

Examples:
  rim:entity:finance:EURUSD
  rim:actor:regulator:SEC
  rim:event:market:fed-rate-decision-2024
  rim:scope:macro:global-recession-scenario
```

### Identity Resolution

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Identity Resolution                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  PERCEPTION          CAPSULE           ODYSSEY           PLATO      │
│       │                 │                 │                │        │
│       │    rim:entity:finance:EURUSD     │                │        │
│       └─────────────────┼─────────────────┼────────────────┘        │
│                         │                 │                          │
│                         ▼                 ▼                          │
│              ┌─────────────────────────────────────┐                │
│              │      Same canonical identity       │                │
│              │      used across all services       │                │
│              └─────────────────────────────────────┘                │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Example

### Event Processing Pipeline

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Example: Market Event Processing                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. PERCEPTION: Detect Event                                        │
│     └─► Web crawl detects Fed rate decision announcement            │
│     └─► Trust scoring validates source (Reuters, Bloomberg)         │
│     └─► Event published to perception.events topic                  │
│                                                                      │
│  2. CAPSULE: Record History                                         │
│     └─► New CAPSULE created with event context                      │
│     └─► Linked to previous market state CAPSULEs                    │
│     └─► Counterfactual branches recorded                            │
│                                                                      │
│  3. ODYSSEY: Update World Model                                     │
│     └─► World Field updated with new market regime                  │
│     └─► Path weights adjusted based on rate decision                │
│     └─► Actor models updated (Fed, banks, markets)                  │
│                                                                      │
│  4. PLATO: Governance Check                                         │
│     └─► Policy evaluation triggered                                 │
│     └─► Risk limits checked against new scenario                    │
│     └─► Proof generated for compliance                              │
│                                                                      │
│  5. NEXUS: Strategic Synthesis                                      │
│     └─► Temporal slice assembled (past + present + futures)         │
│     └─► Strategic options synthesized                               │
│     └─► Cross-system coherence validated                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Security Architecture

### Defense in Depth

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Security Layers                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Network Layer: TLS 1.2+, mTLS for services, CORS            │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Authentication: JWT tokens, API keys, OAuth2/OIDC           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Authorization: RBAC, scoped permissions, resource-level ACL │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Application: Input validation, rate limiting, audit logging │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Next Steps

| Topic | Document |
|-------|----------|
| Detailed architecture | [Ecosystem Overview](../architecture/ecosystem-overview.md) |
| Data flow patterns | [Data Flow](../architecture/data-flow.md) |
| Communication details | [Communication Patterns](../architecture/communication-patterns.md) |
| Installation | [Installation Guide](installation.md) |
| First API calls | [First Steps](first-steps.md) |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Documentation Index](../index.md) | Main documentation portal |
| [Services Overview](../services/README.md) | Detailed service documentation |
| [Architecture Section](../architecture/README.md) | Technical architecture details |
| [ADR Index](../adr/README.md) | Architecture decisions |

