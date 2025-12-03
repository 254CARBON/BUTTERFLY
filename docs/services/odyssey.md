# ODYSSEY Service

> Strategic Cognition Engine for the BUTTERFLY Ecosystem

**Last Updated**: 2025-12-03  
**Service Port**: 8082  
**Full Documentation**: [ODYSSEY README](../../ODYSSEY/README.md)

---

## Overview

ODYSSEY is the strategic cognition layer of BUTTERFLY. It maintains a live, coherent model of the world, projects plausible futures, models how key actors will move, learns from historical outcomes, and synthesizes posture and options across horizons.

## Purpose

> "ODYSSEY thinks strategically about the world—understanding what exists, what might happen, and who is acting."

---

## Conceptual Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ODYSSEY World-Story State                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Six Interconnected Fields                        │   │
│  │                                                                       │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │   │
│  │  │   World Field   │  │   Path Field    │  │   Player Field      │  │   │
│  │  │                 │  │                 │  │                     │  │   │
│  │  │ The live state  │  │ Plausible       │  │ Actor models:       │  │   │
│  │  │ of entities,    │  │ narrative       │  │ objectives,         │  │   │
│  │  │ relationships,  │  │ futures with    │  │ constraints,        │  │   │
│  │  │ and regimes     │  │ probabilities   │  │ strategies          │  │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │   │
│  │  │Experience Field │  │Ignorance Surface│  │ Commitment Ledger   │  │   │
│  │  │                 │  │                 │  │                     │  │   │
│  │  │ Past episodes,  │  │ What we don't   │  │ Explicit and        │  │   │
│  │  │ post-mortems,   │  │ know: low-data  │  │ implicit            │  │   │
│  │  │ learned biases  │  │ regions, weak   │  │ commitments         │  │   │
│  │  │                 │  │ models          │  │                     │  │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │   │
│  │                                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Technology: Java 17 │ Spring Boot │ JanusGraph │ Kafka │ Cassandra        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Capabilities

### 1. World Field - Current State

Maintains a coherent view of:
- **Entities**: Assets, organizations, regions, concepts
- **Relations**: Dependencies, influences, hierarchies
- **Regimes**: Market conditions, political states, operational modes
- **Stress/Buffer**: System fragility indicators

### 2. Path Field - Future Projection

Projects plausible futures:
- **Narrative Branches**: Alternative storylines
- **Branch Points**: Key decision moments
- **Path Weights**: Probability assignments
- **Horizon Alignment**: Short/medium/long-term views

### 3. Player Field - Actor Modeling

Understands key actors:
- **Identity**: Who are the relevant players
- **Objectives**: What they want
- **Constraints**: What limits them
- **Strategies**: How they typically act

### 4. Experience Field - Learning

Learns from history:
- **Episodes**: Past significant events
- **Post-mortems**: What went wrong/right
- **Bias Patterns**: Systematic errors
- **Analogies**: Historical parallels

### 5. Ignorance Surface - Uncertainty

Tracks knowledge gaps:
- **Low-Data Regions**: Where information is sparse
- **Weak Models**: Where predictions are unreliable
- **Update Priorities**: Where to focus attention

### 6. Commitment Ledger - Obligations

Records commitments:
- **Explicit**: Stated promises and contracts
- **Implicit**: Behavioral expectations
- **Credibility**: Track record of fulfillment

---

## Functional Engines

| Engine | Responsibility |
|--------|----------------|
| **State Core** | Assimilate updates from PERCEPTION/CAPSULE |
| **Path Engine** | Generate and weight narrative futures |
| **Actor Engine** | Model player behavior |
| **Experiment Engine** | Strategic probing and hypothesis testing |

---

## API Surface

### Key Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/state/{nodeId}` | Get entity state |
| GET | `/api/v1/paths/{nodeId}` | Get path projections |
| GET | `/api/v1/actors/{actorId}` | Get actor model |
| POST | `/api/v1/scenarios` | Create scenario |
| GET | `/api/v1/ignorance` | Get ignorance surface |
| GET | `/actuator/health` | Service health |

### GraphQL Endpoint

```graphql
query {
  entity(id: "rim:entity:finance:EURUSD") {
    id
    worldState {
      attributes
      regime
      stress
    }
    paths {
      scenario
      probability
      horizon
    }
  }
}
```

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `odyssey.paths` | Path weight updates |
| `odyssey.actors` | Actor model updates |
| `rim.fast-path` | Real-time signal consumption |

---

## Integration Points

### HORIZONBOX Integration

Probabilistic foresight external service:
- Scenario generation
- Probability calibration
- Horizon-specific projections

### QUANTUMROOM Integration

Game-theoretic analysis:
- Multi-player dynamics
- Nash equilibrium finding
- Strategic option scoring

---

## Configuration

### Key Properties

```yaml
odyssey:
  world:
    entity-cache-size: 10000
    regime-update-interval-seconds: 60
  paths:
    max-horizon-days: 365
    min-probability-threshold: 0.01
  actors:
    model-refresh-hours: 24
  janusgraph:
    storage-backend: cassandra
    storage-hostname: localhost
```

### Infrastructure Dependencies

| Component | Purpose |
|-----------|---------|
| JanusGraph | Graph storage |
| Apache Cassandra | Backend for JanusGraph |
| Elasticsearch | Graph indexing |
| Apache Kafka | Event streaming |

---

## Behavioral Modes

| Mode | Description |
|------|-------------|
| **Reactive** | Respond to incoming events |
| **Deliberative** | Deep analysis on demand |
| **Anticipatory** | Proactive scanning for changes |

---

## Quick Start

```bash
# Start infrastructure
cd ODYSSEY
docker compose up -d

# Build
mvn clean install

# Run
cd odyssey-core
mvn spring-boot:run
```

---

## Health and Monitoring

### Health Check

```bash
curl http://localhost:8082/actuator/health
```

### Key Metrics

- World state freshness
- Path projection latency
- Actor model coverage
- Ignorance surface size

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Full README](../../ODYSSEY/README.md) | Complete documentation |
| [API Reference](../../ODYSSEY/docs/api/) | API documentation |
| [Operations Guide](../../ODYSSEY/docs/operations/) | Operational guides |
| [Data Flow](../architecture/data-flow.md) | How data flows to ODYSSEY |

