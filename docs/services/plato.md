# PLATO Service

> Governance and Intelligence Service for the BUTTERFLY Ecosystem

**Last Updated**: 2025-12-03  
**Service Port**: 8083  
**Full Documentation**: [PLATO README](../../PLATO/README.md)

---

## Overview

PLATO (Platform for Learning, Analysis, and Transformation Operations) is the governance and intelligence layer of BUTTERFLY. It provides policy enforcement, AI engine coordination, and intelligent automation through six specialized engines.

## Purpose

> "PLATO governs the ecosystem—enforcing policies, coordinating AI, and generating machine-verifiable proofs."

---

## Core Primitives

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PLATO Core Primitives                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────┐│
│  │     Specs      │  │   Artifacts    │  │    Proofs      │  │   Plans    ││
│  │                │  │                │  │                │  │            ││
│  │ Typed,         │  │ Concrete       │  │ Machine-       │  │ Executable ││
│  │ versioned      │  │ outputs:       │  │ checkable      │  │ workflows  ││
│  │ intents and    │  │ transforms,    │  │ evidence that  │  │ producing  ││
│  │ contracts      │  │ features,      │  │ properties     │  │ Artifacts  ││
│  │                │  │ equations,     │  │ hold           │  │ and Proofs ││
│  │ Examples:      │  │ reports        │  │                │  │            ││
│  │ • RuleSpec     │  │                │  │ Examples:      │  │ Steps:     ││
│  │ • TransformSpec│  │ Examples:      │  │ • TestProof    │  │ • Evaluate ││
│  │ • FeatureSpec  │  │ • SQLArtifact  │  │ • FormalProof  │  │ • Generate ││
│  │ • ResearchSpec │  │ • FeatureSet   │  │ • AuditProof   │  │ • Validate ││
│  │                │  │ • Report       │  │                │  │ • Publish  ││
│  └────────────────┘  └────────────────┘  └────────────────┘  └────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Six AI Engines

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PLATO Engines                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │    │
│  │  │   SynthPlane    │  │  FeatureForge   │  │      LawMiner       │ │    │
│  │  │                 │  │                 │  │                     │ │    │
│  │  │ Program         │  │ Automated       │  │ Equation discovery  │ │    │
│  │  │ synthesis:      │  │ feature         │  │ from data:          │ │    │
│  │  │ SQL, transforms │  │ engineering     │  │ invariants,         │ │    │
│  │  │ from specs      │  │ from raw data   │  │ relationships       │ │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘ │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │    │
│  │  │   SpecMiner     │  │   ResearchOS    │  │  EvidencePlanner    │ │    │
│  │  │                 │  │                 │  │                     │ │    │
│  │  │ Specification   │  │ Research        │  │ Optimal evidence    │ │    │
│  │  │ mining and      │  │ orchestration,  │  │ planning: what      │ │    │
│  │  │ hardening       │  │ hypothesis      │  │ to verify and how   │ │    │
│  │  │                 │  │ testing         │  │                     │ │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘ │    │
│  │                                                                      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Technology: Java 21 │ Spring Boot WebFlux │ Cassandra │ JanusGraph        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Engine Descriptions

| Engine | Capability |
|--------|------------|
| **SynthPlane** | Program synthesis—generates SQL, transforms from high-level specs |
| **FeatureForge** | Automated feature engineering from raw data |
| **LawMiner** | Equation and invariant discovery from data |
| **SpecMiner** | Mines and hardens specifications from existing code/data |
| **ResearchOS** | Orchestrates research workflows and hypothesis testing |
| **EvidencePlanner** | Plans optimal evidence gathering strategies |

---

## API Surface

### Key Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/specs` | Create specification |
| GET | `/api/v1/specs/{id}` | Get specification |
| PUT | `/api/v1/specs/{id}` | Update specification |
| DELETE | `/api/v1/specs/{id}` | Delete specification |
| POST | `/api/v1/artifacts` | Create artifact |
| GET | `/api/v1/artifacts/{id}` | Get artifact |
| POST | `/api/v1/proofs` | Create proof |
| GET | `/api/v1/proofs/{id}` | Get proof |
| POST | `/api/v1/plans` | Create plan |
| POST | `/api/v1/plans/{id}/execute` | Execute plan |
| GET | `/api/v1/plans/{id}/status` | Get execution status |
| POST | `/api/v1/governance/evaluate` | Evaluate policy |
| GET | `/actuator/health` | Service health |

### WebSocket Endpoints

Real-time plan execution streaming:

```
ws://localhost:8083/ws/plans/{planId}
```

Message types:
- `STEP_STARTED` - Step execution started
- `STEP_PROGRESS` - Progress update
- `STEP_COMPLETED` - Step finished
- `PLAN_COMPLETED` - Plan finished

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `plato.governance` | Policy evaluations |
| `plato.plans` | Plan execution events |

---

## Control Plane

The Control Plane enforces governance across all primitives:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Control Plane                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────────┐   │
│  │ Policy Enforcement│  │  Meta-Spec        │  │  RBAC & Audit         │   │
│  │                   │  │  Governance       │  │                       │   │
│  │ • Rule evaluation │  │ • Spec validation │  │ • Permission checks   │   │
│  │ • Threshold checks│  │ • Version control │  │ • Action logging      │   │
│  │ • Compliance      │  │ • Compatibility   │  │ • Access tracking     │   │
│  └───────────────────┘  └───────────────────┘  └───────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Example: Create and Execute a Plan

### 1. Create a Spec

```bash
curl -X POST http://localhost:8083/api/v1/specs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "market-volatility-alert",
    "type": "RULE",
    "version": "1.0.0",
    "definition": {
      "condition": "volatility > 0.15",
      "action": "trigger_alert"
    }
  }'
```

### 2. Create a Plan

```bash
curl -X POST http://localhost:8083/api/v1/plans \
  -H "Content-Type: application/json" \
  -d '{
    "name": "volatility-response",
    "steps": [
      {"name": "evaluate", "type": "EVALUATE", "specId": "spec_123"},
      {"name": "report", "type": "SYNTHESIZE", "engine": "ResearchOS"}
    ]
  }'
```

### 3. Execute the Plan

```bash
curl -X POST http://localhost:8083/api/v1/plans/plan_456/execute
```

---

## Configuration

### Key Properties

```yaml
plato:
  persistence:
    type: cassandra  # or inmemory
  engines:
    synthplane:
      enabled: true
      timeout-seconds: 300
    featureforge:
      enabled: true
    lawminer:
      enabled: true
    specminer:
      enabled: true
    researchos:
      enabled: true
    evidenceplanner:
      enabled: true
  governance:
    strict-mode: true
    audit-enabled: true
```

### Infrastructure Dependencies

| Component | Purpose |
|-----------|---------|
| Apache Cassandra | Persistent storage |
| JanusGraph | Graph relationships |
| Apache Kafka | Event streaming |

---

## Quick Start

```bash
# Start infrastructure
cd PLATO
./scripts/dev-up.sh

# Build
mvn clean install

# Run
mvn spring-boot:run
```

---

## Health and Monitoring

### Health Check

```bash
curl http://localhost:8083/actuator/health
```

### Key Metrics

- Spec count by type
- Plan execution rate
- Engine utilization
- Policy evaluation latency

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Full README](../../PLATO/README.md) | Complete documentation |
| [API Reference](../../PLATO/docs/api/) | API documentation |
| [Operations Guide](../../PLATO/docs/operations/) | Operational guides |
| [Engine Details](../../PLATO/docs/engines/) | Individual engine docs |

