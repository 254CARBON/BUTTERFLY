# BUTTERFLY Data Flow

> Cross-service data flow documentation

**Last Updated**: 2025-12-03  
**Target Audience**: Architects, integration developers

---

## Overview

This document describes how data flows through the BUTTERFLY ecosystem, from initial ingestion through processing, storage, and consumption.

---

## Primary Data Flows

### Flow 1: Signal Ingestion Pipeline

External data enters BUTTERFLY through PERCEPTION and flows to downstream services.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Signal Ingestion Pipeline                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  External Sources                                                            │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         PERCEPTION                                    │   │
│  │                                                                       │   │
│  │  1. Acquisition        2. Integrity          3. Intelligence         │   │
│  │  ┌─────────────┐      ┌─────────────┐      ┌─────────────────────┐  │   │
│  │  │ Ingest      │─────▶│ Trust Score │─────▶│ Event Detection     │  │   │
│  │  │ Normalize   │      │ Dedup       │      │ Scenario Generation │  │   │
│  │  │ Enrich      │      │ Cluster     │      │ Impact Estimation   │  │   │
│  │  └─────────────┘      └─────────────┘      └─────────────────────┘  │   │
│  │                                                                       │   │
│  └───────────────────────────────┬───────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│                    ┌──────────────────────────────┐                         │
│                    │     Kafka: perception.events │                         │
│                    └──────────────────────────────┘                         │
│                                  │                                           │
│         ┌────────────────────────┼────────────────────────┐                 │
│         │                        │                        │                 │
│         ▼                        ▼                        ▼                 │
│  ┌────────────┐          ┌────────────┐          ┌────────────┐            │
│  │  CAPSULE   │          │  ODYSSEY   │          │   PLATO    │            │
│  │            │          │            │          │            │            │
│  │ Store      │          │ Update     │          │ Evaluate   │            │
│  │ CAPSULE    │          │ world      │          │ policies   │            │
│  │ history    │          │ model      │          │            │            │
│  └────────────┘          └────────────┘          └────────────┘            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Data Transformations:**

| Stage | Input | Output | Format |
|-------|-------|--------|--------|
| Acquisition | Raw content (HTML, JSON, feeds) | Normalized Content | Internal model |
| Integrity | Normalized Content | Scored Content + Storylines | Internal model |
| Intelligence | Scored Content | Events + Scenarios | Avro (perception.events) |

### Flow 2: High-Frequency Signal Path

Real-time, low-latency signal flow for time-critical data.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      High-Frequency Signal Path                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Real-Time Sources (Market Data, Sensors, etc.)                             │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       PERCEPTION (Fast Path)                          │   │
│  │                                                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │  RIM Fast-Path Producer                                       │    │   │
│  │  │  • Minimal processing latency (<10ms)                        │    │   │
│  │  │  • Direct to Kafka                                            │    │   │
│  │  │  • Schema: RimFastEvent (Avro)                               │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                  │                                           │
│                                  ▼                                           │
│                    ┌──────────────────────────────┐                         │
│                    │     Kafka: rim.fast-path     │                         │
│                    │     (12 partitions)          │                         │
│                    └──────────────────────────────┘                         │
│                                  │                                           │
│                                  ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        ODYSSEY (Reflex Loop)                          │   │
│  │                                                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │  OdysseyReflexService                                         │    │   │
│  │  │  • In-memory rule evaluation                                  │    │   │
│  │  │  • Predictive CAPSULE generation                             │    │   │
│  │  │  • Accuracy metric tracking                                   │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Target Latency: End-to-end < 50ms                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**RimFastEvent Schema (Avro):**

```json
{
  "namespace": "com.z254.butterfly.hft",
  "type": "record",
  "name": "RimFastEvent",
  "fields": [
    {"name": "rim_node_id", "type": "string"},
    {"name": "event_id", "type": ["null", "string"], "default": null},
    {"name": "timestamp", "type": "long"},
    {"name": "critical_metrics", "type": {"type": "map", "values": "double"}},
    {"name": "price", "type": ["null", "double"], "default": null},
    {"name": "volume", "type": ["null", "double"], "default": null},
    {"name": "stress", "type": ["null", "double"], "default": null},
    {"name": "status", "type": "string"},
    {"name": "source_topic", "type": ["null", "string"], "default": null}
  ]
}
```

### Flow 3: CAPSULE Creation and Retrieval

Historical data storage and retrieval through CAPSULE.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      CAPSULE Creation and Retrieval                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Creation Path                                  │   │
│  │                                                                       │   │
│  │   Source Service          CAPSULE API             Storage            │   │
│  │   (PERCEPTION/ODYSSEY)                                               │   │
│  │         │                      │                      │              │   │
│  │         │  POST /capsules      │                      │              │   │
│  │         │─────────────────────▶│                      │              │   │
│  │         │                      │                      │              │   │
│  │         │                      │  Validate            │              │   │
│  │         │                      │  Generate idempotency key           │   │
│  │         │                      │  Check dedup         │              │   │
│  │         │                      │                      │              │   │
│  │         │                      │  Store CAPSULE       │              │   │
│  │         │                      │─────────────────────▶│              │   │
│  │         │                      │                      │ Cassandra    │   │
│  │         │                      │                      │              │   │
│  │         │                      │  Publish event       │              │   │
│  │         │                      │─────────────────────▶│ Kafka       │   │
│  │         │                      │                      │              │   │
│  │         │◀─────────────────────│                      │              │   │
│  │         │  201 Created         │                      │              │   │
│  │                                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Retrieval Path                                 │   │
│  │                                                                       │   │
│  │   Consumer Service        CAPSULE API             Storage            │   │
│  │   (ODYSSEY/NEXUS)                                                    │   │
│  │         │                      │                      │              │   │
│  │         │  GET /history?       │                      │              │   │
│  │         │  scopeId=...&from=...│                      │              │   │
│  │         │─────────────────────▶│                      │              │   │
│  │         │                      │                      │              │   │
│  │         │                      │  Check cache         │              │   │
│  │         │                      │─────────────────────▶│ Redis       │   │
│  │         │                      │                      │              │   │
│  │         │                      │  Query (if miss)     │              │   │
│  │         │                      │─────────────────────▶│ Cassandra   │   │
│  │         │                      │                      │              │   │
│  │         │◀─────────────────────│                      │              │   │
│  │         │  200 OK + CAPSULEs   │                      │              │   │
│  │                                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Idempotency Key: SHA256(rimNodeId + timestamp + resolution + vantageMode) │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Flow 4: Governance and Policy Evaluation

Policy-driven processing through PLATO.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Governance Flow (PLATO)                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Trigger                     PLATO                      Outcome            │
│   (Event or Request)                                                        │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────┐                                                          │
│  │ Policy       │                                                          │
│  │ Evaluation   │                                                          │
│  │ Request      │                                                          │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                        PLATO Control Plane                           │  │
│  │                                                                       │  │
│  │  1. Load Spec          2. Evaluate           3. Generate Proof      │  │
│  │  ┌─────────────┐      ┌─────────────┐      ┌─────────────────────┐ │  │
│  │  │ Fetch from  │─────▶│ Apply rules │─────▶│ Create evidence     │ │  │
│  │  │ Registry    │      │ to context  │      │ chain               │ │  │
│  │  └─────────────┘      └─────────────┘      └─────────────────────┘ │  │
│  │                                                                       │  │
│  │  4. Execute Plan (if applicable)                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │ Orchestrate steps │ Call engines │ Stream progress via WS   │   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  │                                                                       │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                       │
│                                  ▼                                       │
│                    ┌──────────────────────────────┐                     │
│                    │      Evaluation Result       │                     │
│                    │  • Decision (PASS/FAIL/...)  │                     │
│                    │  • Confidence score          │                     │
│                    │  • Proof artifacts           │                     │
│                    │  • Recommended actions       │                     │
│                    └──────────────────────────────┘                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Flow 5: Cross-System Integration (NEXUS)

Unified temporal and reasoning capabilities through NEXUS.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      NEXUS Integration Flow                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Consumer (API or Internal)                                                │
│         │                                                                   │
│         │  GET /api/v1/temporal/slice/{nodeId}                             │
│         ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                        NEXUS Orchestration                           │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                  Parallel Data Fetching                       │   │  │
│  │  │                                                               │   │  │
│  │  │  ┌──────────┐    ┌──────────┐    ┌──────────┐              │   │  │
│  │  │  │ CAPSULE  │    │PERCEPTION│    │ ODYSSEY  │              │   │  │
│  │  │  │ Client   │    │  Client  │    │  Client  │              │   │  │
│  │  │  │          │    │          │    │          │              │   │  │
│  │  │  │ Past     │    │ Present  │    │ Future   │              │   │  │
│  │  │  │ history  │    │ RIM state│    │ paths    │              │   │  │
│  │  │  └────┬─────┘    └────┬─────┘    └────┬─────┘              │   │  │
│  │  │       │               │               │                     │   │  │
│  │  │       └───────────────┼───────────────┘                     │   │  │
│  │  │                       ▼                                      │   │  │
│  │  │              ┌────────────────┐                             │   │  │
│  │  │              │ Temporal Fusion│                             │   │  │
│  │  │              │ & Coherence    │                             │   │  │
│  │  │              │ Analysis       │                             │   │  │
│  │  │              └────────────────┘                             │   │  │
│  │  │                       │                                      │   │  │
│  │  └───────────────────────┼─────────────────────────────────────┘   │  │
│  │                          ▼                                          │  │
│  │               ┌─────────────────────┐                              │  │
│  │               │   TemporalSlice     │                              │  │
│  │               │   • past: []        │                              │  │
│  │               │   • present: {}     │                              │  │
│  │               │   • futures: []     │                              │  │
│  │               │   • confidence: {}  │                              │  │
│  │               └─────────────────────┘                              │  │
│  │                                                                       │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  Target Latency: <100ms for unified temporal queries                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Kafka Topic Topology

### Topic Categories

| Category | Naming Pattern | Partitions | Retention |
|----------|----------------|------------|-----------|
| Fast path | `rim.fast-path` | 12 | 7 days |
| Domain events | `{service}.events` | 6 | 30 days |
| Commands | `{service}.commands` | 3 | 7 days |
| Dead letters | `{topic}.dlq` | 3 | 90 days |

### Topic Catalog

| Topic | Producer | Consumers | Purpose |
|-------|----------|-----------|---------|
| `rim.fast-path` | PERCEPTION | ODYSSEY | Real-time signals |
| `perception.events` | PERCEPTION | CAPSULE, ODYSSEY, PLATO | Detected events |
| `perception.signals` | PERCEPTION | ODYSSEY | Weak signals |
| `capsule.snapshots` | CAPSULE | ODYSSEY, NEXUS | State changes |
| `odyssey.paths` | ODYSSEY | NEXUS | Path weight updates |
| `odyssey.actors` | ODYSSEY | NEXUS | Actor model updates |
| `plato.governance` | PLATO | Audit | Policy evaluations |
| `plato.plans` | PLATO | Audit | Plan executions |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Communication Patterns](communication-patterns.md) | Integration patterns |
| [Kafka Contracts](../integration/kafka-contracts.md) | Detailed Kafka schemas |
| [Identity Model](identity-model.md) | RimNodeId specification |

