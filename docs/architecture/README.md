# BUTTERFLY Architecture

> Technical architecture documentation for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: Architects, senior developers, technical leads

---

## Overview

This section provides comprehensive technical documentation for the BUTTERFLY ecosystem architecture, covering system design, data flows, communication patterns, and technology decisions.

## Architecture Documents

| Document | Description |
|----------|-------------|
| [Ecosystem Overview](ecosystem-overview.md) | Complete technical architecture of all services |
| [Data Flow](data-flow.md) | Cross-service data flow documentation |
| [Communication Patterns](communication-patterns.md) | Kafka, REST, WebSocket integration patterns |
| [Identity Model](identity-model.md) | Canonical RimNodeId and identity resolution |
| [Security Architecture](security-architecture.md) | End-to-end security design |
| [Technology Stack](technology-stack.md) | Technology decisions and rationale |

---

## Quick Reference

### System Context

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BUTTERFLY ECOSYSTEM                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  External Systems          BUTTERFLY Services          Consumers            │
│  ═══════════════          ═════════════════          ═════════════          │
│                                                                              │
│  ┌─────────────┐          ┌───────────────┐          ┌─────────────┐       │
│  │ Web Sources │─────────▶│  PERCEPTION   │─────────▶│ Dashboards  │       │
│  │ APIs, Feeds │          │   (Sense)     │          │ Analytics   │       │
│  └─────────────┘          └───────────────┘          └─────────────┘       │
│                                  │                                          │
│  ┌─────────────┐          ┌──────▼────────┐          ┌─────────────┐       │
│  │ Historical  │◀────────▶│   CAPSULE     │─────────▶│ Audit/      │       │
│  │ Data        │          │  (Remember)   │          │ Compliance  │       │
│  └─────────────┘          └───────────────┘          └─────────────┘       │
│                                  │                                          │
│  ┌─────────────┐          ┌──────▼────────┐          ┌─────────────┐       │
│  │ Market Data │─────────▶│   ODYSSEY     │─────────▶│ Strategy    │       │
│  │ Economic    │          │   (Think)     │          │ Tools       │       │
│  └─────────────┘          └───────────────┘          └─────────────┘       │
│                                  │                                          │
│  ┌─────────────┐          ┌──────▼────────┐          ┌─────────────┐       │
│  │ Policy      │◀────────▶│    PLATO      │─────────▶│ Governance  │       │
│  │ Rules       │          │   (Govern)    │          │ Reports     │       │
│  └─────────────┘          └───────────────┘          └─────────────┘       │
│                                  │                                          │
│                           ┌──────▼────────┐          ┌─────────────┐       │
│                           │    NEXUS      │─────────▶│ Executive   │       │
│                           │  (Integrate)  │          │ Insights    │       │
│                           └───────────────┘          └─────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Service Responsibilities

| Service | Domain | Primary Responsibility |
|---------|--------|------------------------|
| **PERCEPTION** | Sensing | Information acquisition, trust scoring, event detection |
| **CAPSULE** | Memory | 4D history storage, temporal queries, lineage |
| **ODYSSEY** | Cognition | World modeling, future projection, actor analysis |
| **PLATO** | Governance | Policy enforcement, AI engines, research orchestration |
| **NEXUS** | Integration | Cross-system reasoning, temporal fusion, evolution |

### Technology Stack Summary

| Layer | Technologies |
|-------|--------------|
| **Runtime** | Java 17/21, Spring Boot 3.2.x |
| **Messaging** | Apache Kafka 3.6.x, Avro |
| **Graph DB** | JanusGraph 1.0.x |
| **Time Series** | Apache Cassandra 4.1.x |
| **Relational** | PostgreSQL 15.x |
| **Cache** | Redis 7.x, Apache Ignite 2.16.x |
| **Data Lake** | Apache Iceberg + Nessie |
| **Observability** | Micrometer, Prometheus, Grafana |

---

## Design Principles

### 1. Event-Driven Architecture

All services communicate primarily through Apache Kafka for asynchronous, decoupled communication:

- **Durability**: Events are persisted and can be replayed
- **Scalability**: Independent scaling of producers and consumers
- **Resilience**: Services can operate independently during outages
- **Audit**: Complete event history for compliance

### 2. Canonical Identity

All entities use the canonical `RimNodeId` format for consistent identification across services:

```
rim:{nodeType}:{namespace}:{localId}
```

### 3. Shared Contracts

The `butterfly-common` library provides:
- Domain models (CAPSULE components)
- Identity primitives (RimNodeId)
- Avro schemas for events
- Kafka configuration helpers

### 4. Defense in Depth

Security implemented at multiple layers:
- Network (TLS, mTLS)
- Authentication (JWT, API keys, OAuth2)
- Authorization (RBAC, resource-level ACL)
- Application (input validation, rate limiting)

### 5. Observable by Default

All services expose:
- Health endpoints (`/actuator/health`)
- Metrics (`/actuator/prometheus`)
- Structured logging
- Distributed tracing support

---

## Architecture Decision Records

Key architectural decisions are documented in ADRs:

| ADR | Decision |
|-----|----------|
| [ADR-0001](../adr/0001-event-driven-architecture.md) | Event-Driven Architecture with Apache Kafka |
| [ADR-0002](../adr/0002-shared-contract-library.md) | Shared Contract Library (butterfly-common) |
| [ADR-0003](../adr/0003-canonical-identity-model.md) | Canonical Identity Model (RimNodeId) |
| [ADR-0004](../adr/0004-dead-letter-queue-strategy.md) | Dead Letter Queue Strategy |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Documentation Index](../index.md) | Main documentation portal |
| [Getting Started](../getting-started/README.md) | Quick start guides |
| [Services Overview](../services/README.md) | Individual service documentation |
| [Operations](../operations/README.md) | Deployment and operations |

