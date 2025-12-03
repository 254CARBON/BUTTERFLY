# BUTTERFLY Ecosystem Documentation

> **Unified Cognitive Intelligence Platform**  
> _Engineered by 254STUDIOZ_

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](reference/changelog.md)
[![Status](https://img.shields.io/badge/status-production-success.svg)](operations/README.md)
[![License](https://img.shields.io/badge/license-proprietary-red.svg)](../LICENSE.md)

**Last Updated**: 2025-12-03  
**Documentation Version**: 1.0.0

---

## Welcome to BUTTERFLY

BUTTERFLY is an enterprise-grade **cognitive intelligence platform** that transforms raw information into strategic insight and actionable intelligence. The platform integrates six cooperating services into a unified cognitive organism capable of sensing, understanding, reasoning, governing, and acting across any domain.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BUTTERFLY ECOSYSTEM                                │
│                    Unified Cognitive Intelligence Platform                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│    ┌──────────────────────────────────────────────────────────────────┐    │
│    │                         NEXUS                                      │    │
│    │              The Cognitive Cortex                                  │    │
│    │   Temporal Intelligence │ Reasoning │ Synthesis │ Evolution       │    │
│    └────────────────────────────────┬───────────────────────────────────┘    │
│                                     │                                        │
│    ┌────────────┬───────────────────┼───────────────────┬────────────┐     │
│    │            │                   │                   │            │     │
│    ▼            ▼                   ▼                   ▼            ▼     │
│ ┌──────┐   ┌──────────┐       ┌──────────┐       ┌──────────┐  ┌───────┐ │
│ │PERCEP│   │ CAPSULE  │       │ ODYSSEY  │       │  PLATO   │  │SYNAPSE│ │
│ │ TION │   │          │       │          │       │          │  │(plan) │ │
│ │      │   │   4D     │       │Strategic │       │Governance│  │       │ │
│ │Sense │   │ Memory   │       │Cognition │       │   & AI   │  │Execute│ │
│ └──────┘   └──────────┘       └──────────┘       └──────────┘  └───────┘ │
│  Present      Past              Future            Governance     Action   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Quick Navigation

### By Role

| I want to... | Start here |
|--------------|------------|
| **Get started quickly** | [Quick Start Guide](getting-started/README.md) |
| **Understand the architecture** | [Architecture Overview](getting-started/architecture-overview.md) |
| **Integrate with BUTTERFLY APIs** | [API Documentation](api/README.md) |
| **Deploy to production** | [Operations Guide](operations/README.md) |
| **Contribute to development** | [Development Guide](development/README.md) |
| **Understand security** | [Security Documentation](security/README.md) |

### By Service

| Service | Description | Documentation |
|---------|-------------|---------------|
| **PERCEPTION** | Sensory and interpretation layer | [Service Guide](services/perception.md) \| [Source](../PERCEPTION/README.md) |
| **CAPSULE** | 4D atomic history storage | [Service Guide](services/capsule.md) \| [Source](../CAPSULE/README.md) |
| **ODYSSEY** | Strategic cognition engine | [Service Guide](services/odyssey.md) \| [Source](../ODYSSEY/README.md) |
| **PLATO** | Governance and intelligence | [Service Guide](services/plato.md) \| [Source](../PLATO/README.md) |
| **NEXUS** | Integration cortex layer | [Service Guide](services/nexus.md) \| [Source](../butterfly-nexus/README.md) |
| **butterfly-common** | Shared contracts and identity | [Library Guide](services/butterfly-common.md) \| [Source](../butterfly-common/README.md) |

---

## Documentation Sections

### [Getting Started](getting-started/README.md)

Essential guides for new users and developers:

- [Quick Start Guide](getting-started/README.md) - Get BUTTERFLY running in minutes
- [Architecture Overview](getting-started/architecture-overview.md) - Understand the system design
- [Installation Guide](getting-started/installation.md) - Detailed installation instructions
- [First Steps](getting-started/first-steps.md) - Guided walkthrough for your first integration

### [Architecture](architecture/README.md)

Deep-dive into system design and technical decisions:

- [Ecosystem Overview](architecture/ecosystem-overview.md) - Complete technical architecture
- [Data Flow](architecture/data-flow.md) - Cross-service data flow documentation
- [Communication Patterns](architecture/communication-patterns.md) - Kafka, REST, WebSocket patterns
- [Identity Model](architecture/identity-model.md) - Canonical RimNodeId documentation
- [Security Architecture](architecture/security-architecture.md) - End-to-end security design
- [Technology Stack](architecture/technology-stack.md) - Technology decisions and rationale

### [Services](services/README.md)

Individual service documentation and summaries:

- [PERCEPTION](services/perception.md) - Reality Integration Mesh and signal processing
- [CAPSULE](services/capsule.md) - 4D atomic history service
- [ODYSSEY](services/odyssey.md) - Strategic cognition and world modeling
- [PLATO](services/plato.md) - Governance, intelligence, and six AI engines
- [NEXUS](services/nexus.md) - Integration layer and cognitive cortex
- [butterfly-common](services/butterfly-common.md) - Shared library and contracts

### [API Documentation](api/README.md)

Comprehensive API reference and integration guides:

- [API Catalog](api/api-catalog.md) - Unified catalog of all endpoints
- [Authentication](api/authentication.md) - JWT, OAuth2, and API key authentication
- [Error Handling](api/error-handling.md) - Standardized error codes and responses
- [Rate Limiting](api/rate-limiting.md) - Rate limit policies and best practices
- [Versioning](api/versioning.md) - API versioning strategy

### [Integration](integration/README.md)

Guides for integrating with BUTTERFLY:

- [Client Guide](integration/client-guide.md) - Integration patterns and best practices
- [Kafka Contracts](integration/kafka-contracts.md) - Event schemas and topic catalog
- [Webhooks](integration/webhooks.md) - Webhook integration patterns
- **SDKs**: [Java](integration/sdks/java.md) | [Python](integration/sdks/python.md) | [TypeScript](integration/sdks/typescript.md)

### [Operations](operations/README.md)

Production deployment and operational guides:

- **Deployment**: [Docker](operations/deployment/docker.md) | [Kubernetes](operations/deployment/kubernetes.md) | [Production Checklist](operations/deployment/production-checklist.md)
- **Monitoring**: [Metrics Catalog](operations/monitoring/metrics-catalog.md) | [Dashboards](operations/monitoring/dashboards.md) | [Alerting](operations/monitoring/alerting.md)
- **Runbooks**: [Incident Response](operations/runbooks/incident-response.md) | [Disaster Recovery](operations/runbooks/disaster-recovery.md) | [Scaling](operations/runbooks/scaling.md)
- [Capacity Planning](operations/capacity-planning.md)

### [Security](security/README.md)

Security architecture and compliance documentation:

- [Security Model](security/security-model.md) - Security architecture overview
- [Compliance](security/compliance.md) - SOC2 and regulatory compliance
- [Hardening](security/hardening.md) - Production hardening guide
- [Vulnerability Management](security/vulnerability-management.md) - Vulnerability handling processes

### [Development](development/README.md)

Guides for contributors and developers:

- [Contributing](development/contributing.md) - Contribution guidelines
- [Coding Standards](development/coding-standards.md) - Code style and conventions
- [Testing Strategy](development/testing-strategy.md) - Testing approach and frameworks
- [CI/CD](development/ci-cd.md) - Continuous integration and deployment

### [Reference](reference/README.md)

Reference materials and resources:

- [Glossary](reference/glossary.md) - Unified terminology
- [FAQ](reference/faq.md) - Frequently asked questions
- [Changelog](reference/changelog.md) - Ecosystem-level version history

### [Architecture Decision Records](adr/README.md)

Documented architectural decisions:

- [ADR-0001](adr/0001-event-driven-architecture.md) - Event-Driven Architecture
- [ADR-0002](adr/0002-shared-contract-library.md) - Shared Contract Library
- [ADR-0003](adr/0003-canonical-identity-model.md) - Canonical Identity Model
- [View All ADRs](adr/README.md)

---

## Key Concepts

### The BUTTERFLY Model

BUTTERFLY implements a cognitive architecture with five key capabilities:

| Capability | Service | Function |
|------------|---------|----------|
| **Sense** | PERCEPTION | Ingest, normalize, and interpret external information |
| **Remember** | CAPSULE | Store and retrieve 4D atomic units of history |
| **Think** | ODYSSEY | Model the world, project futures, understand actors |
| **Govern** | PLATO | Enforce policies, generate proofs, coordinate AI engines |
| **Integrate** | NEXUS | Unify all capabilities with temporal reasoning |

### Core Primitives

| Primitive | Description |
|-----------|-------------|
| **RimNodeId** | Canonical identity format: `rim:{nodeType}:{namespace}:{localId}` |
| **CAPSULE** | 4D atomic unit with Configuration, Dynamics, Agency, Counterfactual, Meta |
| **Spec** | Typed, versioned intent and contract definition |
| **Artifact** | Concrete output: SQL transform, feature, equation, report |
| **Proof** | Machine-checkable evidence that properties are satisfied |
| **Plan** | Executable workflow producing Artifacts and Proofs |

### Communication Model

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Communication Patterns                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Apache Kafka                               │   │
│  │  rim.fast-path │ perception.events │ odyssey.paths │ ...     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              ▲                                       │
│                              │ Async Events                         │
│    ┌─────────┬──────────────┼──────────────┬─────────┐            │
│    │         │              │              │         │            │
│    ▼         ▼              ▼              ▼         ▼            │
│  ┌─────┐  ┌───────┐    ┌───────┐    ┌───────┐  ┌───────┐        │
│  │PERCEP│  │CAPSULE│    │ODYSSEY│    │ PLATO │  │ NEXUS │        │
│  └─────┘  └───────┘    └───────┘    └───────┘  └───────┘        │
│    │         │              │              │         │            │
│    └─────────┴──────────────┴──────────────┴─────────┘            │
│                              │                                       │
│                              ▼ REST/WebSocket                       │
│                    ┌─────────────────────┐                         │
│                    │    Client APIs      │                         │
│                    └─────────────────────┘                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Category | Technology | Version |
|----------|------------|---------|
| **Language** | Java | 17 / 21 |
| **Framework** | Spring Boot | 3.2.x |
| **Messaging** | Apache Kafka | 3.6.x |
| **Graph DB** | JanusGraph | 1.0.x |
| **Time Series** | Apache Cassandra | 4.1.x |
| **Relational** | PostgreSQL | 15.x |
| **Cache** | Redis, Apache Ignite | 7.x, 2.16.x |
| **Data Lake** | Apache Iceberg + Nessie | 1.4.x |
| **Serialization** | Apache Avro | 1.11.x |
| **Observability** | Micrometer, Prometheus, Grafana | - |

---

## Getting Help

| Channel | Purpose |
|---------|---------|
| **Slack**: `#butterfly-dev` | General questions and discussion |
| **GitHub Issues** | Bug reports and feature requests |
| **Email**: engineering@254studioz.com | Technical support |
| **Email**: security@254studioz.com | Security concerns |

---

## Quick Links

| Resource | Link |
|----------|------|
| Development Setup | [DEVELOPMENT_OVERVIEW.md](../DEVELOPMENT_OVERVIEW.md) |
| E2E Testing | [butterfly-e2e](../butterfly-e2e/README.md) |
| Security Policy | [SECURITY.md](../PLATO/SECURITY.md) |
| License | [LICENSE.md](../LICENSE.md) |

---

## Document Information

| Field | Value |
|-------|-------|
| **Version** | 1.0.0 |
| **Last Updated** | 2025-12-03 |
| **Maintained By** | BUTTERFLY Documentation Team |
| **Contact** | engineering@254studioz.com |

---

**Built with precision by 254STUDIOZ**

