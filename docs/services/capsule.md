# CAPSULE Service

> 4D Atomic History Service for the BUTTERFLY Ecosystem

**Last Updated**: 2025-12-03  
**Service Port**: 8081  
**Full Documentation**: [CAPSULE README](../../CAPSULE/README.md)

---

## Overview

CAPSULE is the memory layer of BUTTERFLY, responsible for storing and retrieving 4D atomic units of history. Each CAPSULE represents a complete snapshot of reality at a specific point in time, resolution, and vantage point.

## Purpose

> "CAPSULE remembers history as 4D atomic units, enabling any query about past state with full provenance."

---

## The CAPSULE Model

A CAPSULE captures five dimensions of reality:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CAPSULE Structure                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │    │
│  │  │  Configuration  │  │    Dynamics     │  │      Agency         │ │    │
│  │  │       (C)       │  │       (D)       │  │        (A)          │ │    │
│  │  │                 │  │                 │  │                     │ │    │
│  │  │ "What exists    │  │ "How things     │  │ "Who is acting      │ │    │
│  │  │  and how it's   │  │  are changing"  │  │  and why"           │ │    │
│  │  │  arranged"      │  │                 │  │                     │ │    │
│  │  │                 │  │ • Velocities    │  │ • Actor identities  │ │    │
│  │  │ • Entity state  │  │ • Trends        │  │ • Intentions        │ │    │
│  │  │ • Attributes    │  │ • Rates of      │  │ • Goals             │ │    │
│  │  │ • Relationships │  │   change        │  │ • Confidence        │ │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘ │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌────────────────────────────────────────┐   │    │
│  │  │ Counterfactual  │  │               Meta                      │   │    │
│  │  │       (X)       │  │               (M)                       │   │    │
│  │  │                 │  │                                         │   │    │
│  │  │ "What else      │  │ "How reliable is this?"                │   │    │
│  │  │  could happen"  │  │                                         │   │    │
│  │  │                 │  │ • Confidence scores                     │   │    │
│  │  │ • Alternative   │  │ • Source provenance                     │   │    │
│  │  │   scenarios     │  │ • Data quality                          │   │    │
│  │  │ • Probabilities │  │ • Timestamp, resolution                 │   │    │
│  │  └─────────────────┘  └────────────────────────────────────────┘   │    │
│  │                                                                      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Properties

### Immutability

CAPSULEs are immutable. Once created, they never change. Corrections create new CAPSULEs that supersede older ones.

### Markov-at-Resolution

Each CAPSULE contains enough information to reason about the next time step at its resolution level. You don't need to look at previous CAPSULEs to understand the current state.

### Multi-Vantage

CAPSULEs can represent different perspectives:
- **Omniscient**: Complete view with all available information
- **Observer**: Specific observer's perspective
- **System**: Internal system view
- **Public**: Publicly available information only

### Idempotency

Each CAPSULE has a unique idempotency key:

```
IdempotencyKey = SHA256(rimNodeId + timestamp + resolution + vantageMode)
```

---

## API Surface

### Key Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/capsules` | Create a CAPSULE |
| GET | `/api/v1/capsules/{id}` | Get CAPSULE by ID |
| GET | `/api/v1/history` | Query history for an entity |
| GET | `/api/v1/history/{scopeId}` | Get entity history |
| DELETE | `/api/v1/capsules/{id}` | Delete CAPSULE |
| GET | `/actuator/health` | Service health |

### Example: Create CAPSULE

```bash
curl -X POST http://localhost:8081/api/v1/capsules \
  -H "Content-Type: application/json" \
  -d '{
    "scopeId": "rim:entity:finance:EURUSD",
    "resolution": 60,
    "vantageMode": "omniscient",
    "timestamp": "2024-12-03T10:00:00Z",
    "configuration": {
      "type": "currency_pair",
      "base": "EUR",
      "quote": "USD"
    },
    "dynamics": {
      "priceChange": 0.0012,
      "volatility": 0.08
    },
    "agency": {
      "actors": []
    },
    "counterfactual": {
      "alternativePaths": []
    },
    "meta": {
      "confidence": 0.92,
      "sources": ["reuters"]
    }
  }'
```

### Example: Query History

```bash
curl "http://localhost:8081/api/v1/history?scopeId=rim:entity:finance:EURUSD&from=2024-12-01T00:00:00Z&to=2024-12-03T23:59:59Z"
```

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `capsule.snapshots` | New CAPSULE notifications |
| `capsule.history` | History query results |

---

## Storage Architecture

### Primary Storage: Apache Cassandra

```cql
CREATE TABLE capsules (
    scope_id text,
    timestamp timestamp,
    resolution int,
    vantage_mode text,
    idempotency_key text,
    configuration text,
    dynamics text,
    agency text,
    counterfactual text,
    meta text,
    PRIMARY KEY ((scope_id), timestamp, resolution)
) WITH CLUSTERING ORDER BY (timestamp DESC);
```

### Caching: Redis

- Recent CAPSULEs cached for fast retrieval
- Idempotency key lookup cache
- TTL-based expiration

---

## Configuration

### Key Properties

```yaml
capsule:
  storage:
    type: cassandra
    keyspace: butterfly_capsules
  cache:
    enabled: true
    ttl-seconds: 3600
  idempotency:
    check-enabled: true
  retention:
    default-days: 365
```

### Infrastructure Dependencies

| Component | Purpose |
|-----------|---------|
| Apache Cassandra | Primary storage |
| Redis | Caching |
| Apache Kafka | Event streaming |

---

## Resolution Levels

| Resolution | Seconds | Use Case |
|------------|---------|----------|
| 1s | 1 | High-frequency tick data |
| 1m | 60 | Minute bars |
| 5m | 300 | Medium-frequency |
| 1h | 3600 | Hourly snapshots |
| 1d | 86400 | Daily snapshots |

---

## Quick Start

```bash
# Start infrastructure
cd CAPSULE
docker compose up -d

# Build
mvn clean install

# Run
mvn spring-boot:run
```

---

## Health and Monitoring

### Health Check

```bash
curl http://localhost:8081/actuator/health
```

### Key Metrics

- CAPSULE creation rate
- Query latency
- Cache hit ratio
- Storage utilization

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Full README](../../CAPSULE/README.md) | Complete documentation |
| [API Reference](../../CAPSULE/docs/api/) | API documentation |
| [Deployment Guide](../../CAPSULE/docs/deployment/) | Deployment guides |
| [Identity Model](../architecture/identity-model.md) | RimNodeId specification |

