# AURORA - Self-Healing Engine

> Autonomous Root Cause Analysis and Remediation for the BUTTERFLY Ecosystem

**Version**: 1.0.0  
**Port**: 8086  
**Status**: Active Development

---

## Overview

AURORA is the self-healing layer of the BUTTERFLY ecosystem. It provides intelligent root cause analysis (RCA), governed auto-remediation, and chaos immunization capabilities.

### Key Capabilities

| Capability | Description |
|------------|-------------|
| **Root Cause Analysis** | Causal reasoning from correlated anomalies using graph-based analysis |
| **Auto-Remediation** | Governed, automated healing actions with safety controls |
| **Chaos Immunization** | Learning from incidents to prevent recurrence |
| **Safety Controls** | Blast-radius limits, pre-checks, rollback management |

---

## Architecture

```
PERCEPTION (anomaly detection) 
      │ aurora.anomalies.detected
      ▼
   AURORA (RCA + Remediation Engine)
      │
      ├──► PLATO (governance approval, blast-radius eval)
      │
      ├──► SYNAPSE (execute healing actions via connectors)
      │
      └──► CAPSULE (store evidence, chaos learnings)
```

### Core Components

| Component | Responsibility |
|-----------|---------------|
| `AnomalyStreamProcessor` | Kafka Streams topology for anomaly consumption |
| `RootCauseAnalyzer` | Causal graph construction and hypothesis generation |
| `AutoRemediator` | Remediation orchestration with safety checks |
| `SafetyGuard` | Blast-radius estimation, pre-checks |
| `RollbackManager` | Health-window monitoring and rollback |
| `ChaosImmunizer` | Pattern learning and immunity rule generation |

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- Kafka + Schema Registry
- Cassandra
- Redis

### Development

```bash
# Start infrastructure
docker-compose up -d kafka schema-registry redis cassandra

# Build
mvn clean install

# Run
mvn spring-boot:run

# Or with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Docker

```bash
# Build and run all services
docker-compose up -d

# View logs
docker-compose logs -f aurora
```

---

## API Endpoints

### Incidents

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/incidents` | List incidents with filters |
| GET | `/api/v1/incidents/{id}` | Get incident details |
| GET | `/api/v1/incidents/{id}/rca` | Get RCA results for incident |
| GET | `/api/v1/incidents/{id}/remediations` | Get remediation history |

### RCA

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/rca/analyze` | Trigger manual RCA |
| GET | `/api/v1/rca/hypotheses` | Query hypotheses |

### Remediation

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/remediation/execute` | Execute remediation manually |
| GET | `/api/v1/remediation/{id}/status` | Get remediation status |
| POST | `/api/v1/remediation/{id}/rollback` | Trigger rollback |

### Immunity

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/immunity/rules` | List immunity rules |
| POST | `/api/v1/immunity/rules` | Create manual rule |

---

## Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `aurora.anomalies.detected` | IN | Input anomalies from PERCEPTION |
| `aurora.anomalies.enriched` | INTERNAL | Enriched anomaly stream |
| `aurora.rca.hypotheses` | OUT | RCA outputs to PLATO/NEXUS |
| `aurora.remediation.actions` | OUT | Healing actions for SYNAPSE |
| `aurora.remediation.results` | IN | Action outcomes from SYNAPSE |
| `aurora.chaos.learnings` | OUT | Immunity learnings |
| `aurora.incidents.dlq` | OUT | Failed processing |

---

## Configuration

Key configuration properties:

```properties
# RCA Configuration
aurora.rca.correlation-window=5m
aurora.rca.min-confidence-threshold=0.6
aurora.rca.max-hypotheses=5

# Safety Controls
aurora.safety.max-concurrent-remediations=3
aurora.safety.default-blast-radius-limit=1000
aurora.safety.dry-run-default=true
aurora.safety.auto-approve-risk-threshold=0.3

# PLATO Integration
aurora.plato.url=http://localhost:8082
aurora.plato.fallback.allow-on-unavailable=true
aurora.plato.fallback.auto-approve-low-risk=true
```

See `application.properties` for full configuration options.

---

## Safety Controls

AURORA implements multiple safety layers:

1. **Blast Radius Estimation**: Calculates affected scope before remediation
2. **Concurrent Limits**: Maximum simultaneous remediations
3. **Dry-Run Default**: Safe mode by default
4. **PLATO Governance**: Approval required for high-risk actions
5. **Rollback Window**: Automatic rollback on degradation
6. **Cooldown Period**: Prevents rapid repeated remediations

---

## Monitoring

### Health Check

```bash
curl http://localhost:8086/actuator/health
```

### Metrics

Key metrics exposed via `/actuator/prometheus`:

- `aurora.incidents.created` - Incidents created
- `aurora.incidents.mttr` - Mean time to resolution
- `aurora.rca.latency` - RCA analysis latency
- `aurora.remediations.completed` - Successful remediations
- `aurora.immunity.rules.applied` - Rules applied

---

## Integration

### PERCEPTION → AURORA

Consumes `aurora.anomalies.detected` topic with anomaly events.

### AURORA → PLATO

Submits remediation plans for governance approval via HTTP.

### AURORA → SYNAPSE

Dispatches healing actions via Kafka or HTTP.

### AURORA → CAPSULE

Stores evidence and chaos learnings.

---

## Development

### Project Structure

```
AURORA/
├── schema/avro/           # Avro schemas
├── src/main/java/.../
│   ├── config/            # Configuration classes
│   ├── api/               # REST controllers
│   ├── domain/            # Domain models
│   ├── rca/               # RCA engine
│   ├── remediation/       # Remediation engine
│   ├── immunity/          # Chaos immunity
│   ├── governance/        # PLATO integration
│   ├── stream/            # Kafka Streams
│   ├── kafka/             # Kafka producers
│   ├── client/            # External clients
│   ├── observability/     # Logging, metrics
│   └── health/            # Health indicators
└── src/test/java/         # Tests
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -P integration-tests
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Architecture Overview](../docs/getting-started/architecture-overview.md) | System architecture |
| [Kafka Contracts](../docs/integration/kafka-contracts.md) | Event schemas |
| [SYNAPSE Service](../docs/services/synapse.md) | Execution layer |

---

## License

Copyright © 2024 254STUDIOZ LLC. All rights reserved.
