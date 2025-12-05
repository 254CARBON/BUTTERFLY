# SYNAPSE Service

> Action Execution Layer for the BUTTERFLY Ecosystem

**Last Updated**: 2025-12-05  
**Service Port**: 8080  
**Full Documentation**: [SYNAPSE README](../../SYNAPSE/README.md)

---

## Overview

SYNAPSE is the action execution layer of BUTTERFLY, responsible for executing governance decisions, managing tool invocations, tracking action trails, and integrating with external systems through connectors.

### Service Type: Headless Backend Service

SYNAPSE is a **headless backend service** that executes actions orchestrated by PLATO and provides action trail data for NEXUS queries.

| Aspect | Details |
|--------|---------|
| **UI Strategy** | Headless (no dedicated UI) |
| **Primary Interface** | REST API + Kafka Events |
| **Management Portal** | Actions visible in NEXUS dashboard |

## Purpose

> "SYNAPSE turns decisions into actions and tracks their execution with full provenance."

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SYNAPSE                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         Core Domains                                  │   │
│  │                                                                       │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │   │
│  │  │  Action         │  │  Decision       │  │  Connector          │  │   │
│  │  │  Execution      │  │  Pipeline       │  │  Framework          │  │   │
│  │  │                 │  │                 │  │                     │  │   │
│  │  │ • Sync/Async    │  │ • Governance    │  │ • Slack             │  │   │
│  │  │ • Safety checks │  │ • Multi-step    │  │ • Webhook           │  │   │
│  │  │ • Result store  │  │ • Sequencing    │  │ • Email             │  │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │   │
│  │  │  Action Trail   │  │  Outcome        │  │  Sandbox            │  │   │
│  │  │  Service        │  │  Events         │  │  Enforcer           │  │   │
│  │  │                 │  │                 │  │                     │  │   │
│  │  │ • NEXUS queries │  │ • Kafka events  │  │ • Blast radius      │  │   │
│  │  │ • Provenance    │  │ • Audit trail   │  │ • Rate limiting     │  │   │
│  │  │ • Temporal view │  │ • Feedback loop │  │ • Safety controls   │  │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘  │   │
│  │                                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Technology: Java 21 │ Spring Boot │ Kafka │ Cassandra │ Redis │ WebFlux   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Capabilities

### 1. Action Execution

Execute actions with full safety controls:
- **Synchronous Execution**: Direct request-response pattern
- **Asynchronous Execution**: Submit and poll for results
- **Safety Checks**: Blast radius limits, rate limiting, sandbox enforcement
- **Retry Logic**: Configurable retry policies with exponential backoff

### 2. Decision Pipeline

Process governance decisions from PLATO:
- **Multi-Step Plans**: Execute complex workflows with dependencies
- **Governance Gates**: Enforce approval requirements for high-risk actions
- **DRY_RUN Mode**: Simulate actions without external side effects

### 3. Connector Framework

Integrate with external systems:
- **Slack Connector**: Send notifications and alerts
- **Webhook Connector**: Call external APIs
- **Email Connector**: Send email notifications
- **Custom Connectors**: Extensible framework for new integrations

### 4. Action Trail Service

Track and query action history:
- **NEXUS Integration**: Temporal queries by RIM node, plan ID, correlation ID
- **Full Provenance**: Link actions to PLATO plans, CAPSULE snapshots
- **Graceful Degradation**: Return partial results on query failures

### 5. Model Drift Remediation

Execute automated drift remediation workflows:
- **Canary Deployment Actions**: Trigger model deployments
- **Health Monitoring**: Check canary metrics
- **Rollback Actions**: Revert failed deployments

---

## Module Structure

| Module | Responsibility |
|--------|----------------|
| `synapse-api` | REST API endpoints |
| `synapse-execution` | Action execution engine |
| `synapse-connector` | External system integrations |
| `synapse-decision` | Decision pipeline processing |
| `synapse-domain` | Domain models and repositories |
| `synapse-kafka` | Kafka event producers/consumers |

---

## API Surface

### Action Execution

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/actions` | Execute action synchronously |
| POST | `/api/v1/actions/async` | Submit action asynchronously |
| POST | `/api/v1/actions/execute` | Execute action (PLATO integration) |
| GET | `/api/v1/actions/{actionId}` | Get action status |
| GET | `/api/v1/actions/{actionId}/status` | Get action status (alias) |
| POST | `/api/v1/actions/{actionId}/cancel` | Cancel pending action |
| POST | `/api/v1/actions/{actionId}/retry` | Retry failed action |

### Action Queries (NEXUS Integration)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/actions` | Query actions with filters |
| GET | `/api/v1/actions/nexus` | Query actions for NEXUS format |
| GET | `/api/v1/actions/by-rim-node/{rimNodeId}` | Actions by RIM node |
| GET | `/api/v1/actions/by-plato-plan/{planId}` | Actions by PLATO plan |
| GET | `/api/v1/actions/by-capsule/{capsuleId}` | Actions by CAPSULE |

### Decision Pipeline

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/decisions` | Submit decision for execution |
| GET | `/api/v1/decisions/{id}` | Get decision status |
| GET | `/api/v1/decisions` | List decisions with filters |
| GET | `/api/v1/decisions/{id}/invocations` | List tool invocations |
| GET | `/api/v1/decisions/{id}/stream` | Stream status updates (SSE) |

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `synapse.actions.requests` | Incoming action requests |
| `synapse.actions.outcomes` | Action execution outcomes |
| `synapse.decisions.events` | Decision lifecycle events |
| `synapse.decisions.feedback` | Terminal decision feedback |

---

## Configuration

### Key Properties

```yaml
synapse:
  execution:
    timeout-seconds: 30
    max-retries: 3
    backoff-multiplier: 2.0
  
  safety:
    blast-radius-limit: 100
    rate-limit-per-minute: 1000
    sandbox-enabled: true
  
  connectors:
    slack:
      enabled: true
      webhook-url: ${SLACK_WEBHOOK_URL}
    webhook:
      enabled: true
      timeout-seconds: 10
  
  kafka:
    topics:
      actions-requests: synapse.actions.requests
      actions-outcomes: synapse.actions.outcomes
      decisions-events: synapse.decisions.events
      decisions-feedback: synapse.decisions.feedback
```

### Infrastructure Dependencies

| Component | Purpose |
|-----------|---------|
| Cassandra | Action and decision persistence |
| Redis | Distributed locking and caching |
| Apache Kafka | Event streaming |

---

## Drift Remediation Integration

SYNAPSE executes drift remediation actions orchestrated by PLATO. See [Decision Execution - Model Drift Remediation](../../SYNAPSE/docs/decision-execution.md#model-drift-remediation) for details.

### Drift Remediation Tool IDs

| Tool ID | Action Type | Description |
|---------|-------------|-------------|
| `drift-remediation-notifier` | `NOTIFY` | Send drift alerts |
| `drift-remediation-canary` | `DEPLOY` | Trigger canary deployment |
| `drift-remediation-monitor` | `QUERY` | Check canary health |
| `drift-remediation-rollback` | `EXECUTE` | Force rollback |
| `drift-remediation-promote` | `EXECUTE` | Promote canary |

### Querying Drift Remediation Actions

```bash
# By RIM node (model entity)
curl "http://synapse:8080/api/v1/actions/nexus?rimNodeId=rim:model:my-model"

# By PLATO remediation plan
curl "http://synapse:8080/api/v1/actions/by-plato-plan/{planId}"

# Filter by tool ID
curl "http://synapse:8080/api/v1/actions?toolId=drift-remediation-canary"
```

---

## Multi-Tenancy

SYNAPSE supports multi-tenant deployments:
- Tenant isolation at action and decision level
- Per-tenant rate limiting
- Tenant-scoped connector configurations

---

## Quick Start

```bash
# Start infrastructure
cd SYNAPSE
docker compose up -d

# Build
mvn clean install

# Run API
cd synapse-api
mvn spring-boot:run
```

---

## Health and Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Metrics

- Action execution rate and latency
- Decision pipeline throughput
- Connector success/failure rates
- Kafka producer/consumer metrics

### Key Dashboards

- [BUTTERFLY Ecosystem SLO](../../docs/operations/monitoring/dashboards/butterfly-ecosystem-slo-dashboard.json)
- SYNAPSE Action Execution
- Decision Pipeline Health

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Full README](../../SYNAPSE/README.md) | Complete documentation |
| [Decision Execution](../../SYNAPSE/docs/decision-execution.md) | Decision pipeline details |
| [Connector Recovery Runbook](../../SYNAPSE/docs/runbooks/connector-recovery.md) | Connector troubleshooting |
| [Drift Governance](../../PERCEPTION/docs/DRIFT_GOVERNANCE.md) | Drift detection and remediation |
| [Drift Remediation Runbook](../../docs/operations/runbooks/drift-remediation-loop.md) | Operational procedures |
