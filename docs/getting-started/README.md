# Getting Started with BUTTERFLY

> Your fast path to understanding and running the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: New users, developers, evaluators

---

## Overview

This guide provides multiple paths to get started with BUTTERFLY, depending on your role and objectives.

## Choose Your Path

### I want to...

| Goal | Recommended Path | Time |
|------|------------------|------|
| **Understand BUTTERFLY** | [Architecture Overview](architecture-overview.md) | 15 min |
| **Run BUTTERFLY locally** | [Installation Guide](installation.md) | 30 min |
| **Make my first API call** | [First Steps](first-steps.md) | 20 min |
| **Contribute to development** | [Development Guide](../development/README.md) | 1 hour |
| **Deploy to production** | [Operations Guide](../operations/README.md) | 2+ hours |

---

## Quick Start (5 Minutes)

The fastest way to get BUTTERFLY running locally:

### Prerequisites

| Tool | Minimum Version | Purpose |
|------|-----------------|---------|
| Java JDK | 17 | Backend services |
| Maven | 3.9+ | Build tool |
| Docker | 20+ | Container runtime |
| Docker Compose | v2+ | Multi-container orchestration |

### One-Command Setup

```bash
# Clone and setup
git clone https://github.com/254CARBON/BUTTERFLY.git
cd BUTTERFLY/apps

# Run the setup script (installs deps, configures hooks)
./scripts/setup.sh

# Start the development infrastructure
./scripts/dev-up.sh
```

### Verify Installation

```bash
# Check service health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Access Swagger UI (when services are running)
open http://localhost:8080/swagger-ui.html
```

---

## What is BUTTERFLY?

BUTTERFLY is an enterprise **cognitive intelligence platform** consisting of six integrated services:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BUTTERFLY ECOSYSTEM                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                         NEXUS                                  │  │
│  │                   Integration Layer                            │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│       ┌──────────┬───────────┼───────────┬──────────┐              │
│       │          │           │           │          │              │
│       ▼          ▼           ▼           ▼          ▼              │
│  ┌────────┐ ┌────────┐ ┌──────────┐ ┌────────┐ ┌────────┐        │
│  │PERCEP- │ │CAPSULE │ │ ODYSSEY  │ │ PLATO  │ │SYNAPSE │        │
│  │  TION  │ │        │ │          │ │        │ │(future)│        │
│  │        │ │   4D   │ │Strategic │ │Govern- │ │        │        │
│  │ Sense  │ │ Memory │ │Cognition │ │  ance  │ │Execute │        │
│  └────────┘ └────────┘ └──────────┘ └────────┘ └────────┘        │
│   Present     Past       Future      Control     Action           │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Service Summary

| Service | Purpose | Key Capabilities |
|---------|---------|------------------|
| **PERCEPTION** | Sense the world | Signal acquisition, trust scoring, event detection, RIM |
| **CAPSULE** | Remember history | 4D atomic history, temporal queries, lineage tracking |
| **ODYSSEY** | Understand futures | World modeling, path projection, actor analysis |
| **PLATO** | Govern and reason | Policy enforcement, program synthesis, research orchestration |
| **NEXUS** | Integrate all | Temporal fusion, cross-system reasoning, self-evolution |

---

## Learning Path

### Week 1: Foundation

1. **Read** [Architecture Overview](architecture-overview.md) - Understand the system design
2. **Follow** [Installation Guide](installation.md) - Set up your development environment
3. **Complete** [First Steps](first-steps.md) - Make your first API calls

### Week 2: Deep Dive

4. **Explore** [Service Documentation](../services/README.md) - Learn each service
5. **Study** [API Documentation](../api/README.md) - Understand the API surface
6. **Review** [Communication Patterns](../architecture/communication-patterns.md) - Learn integration patterns

### Week 3: Advanced

7. **Read** [Architecture Decision Records](../adr/README.md) - Understand design decisions
8. **Explore** [Integration Guide](../integration/README.md) - Build integrations
9. **Review** [Operations Guide](../operations/README.md) - Production deployment

---

## Service Ports (Development)

| Service | Port | Swagger UI |
|---------|------|------------|
| PERCEPTION | 8080 | http://localhost:8080/swagger-ui.html |
| CAPSULE | 8081 | http://localhost:8081/swagger-ui.html |
| ODYSSEY | 8082 | http://localhost:8082/swagger-ui.html |
| PLATO | 8080 | http://localhost:8080/swagger-ui.html |
| NEXUS | 8084 | http://localhost:8084/swagger-ui.html |

### Infrastructure Ports

| Component | Port |
|-----------|------|
| Kafka | 9092 |
| Schema Registry | 8081 |
| PostgreSQL | 5432 |
| Cassandra | 9042 |
| Redis | 6379 |
| Prometheus | 9090 |
| Grafana | 3000 |

---

## Common Tasks

### Start Individual Services

```bash
# Start CAPSULE
docker compose -f CAPSULE/docker-compose.yml up -d

# Start PERCEPTION
docker compose -f PERCEPTION/docker-compose.yml up -d

# Start ODYSSEY
docker compose -f ODYSSEY/docker-compose.yml up -d

# Start PLATO
./PLATO/scripts/dev-up.sh
```

### Run Tests

```bash
# All modules
mvn test

# Specific module
mvn -f PERCEPTION/pom.xml test
mvn -f PLATO/pom.xml test
mvn -f CAPSULE/pom.xml test
mvn -f ODYSSEY/pom.xml test

# E2E golden path test
./butterfly-e2e/run-golden-path.sh
```

### Build All Services

```bash
# Build everything
mvn clean install

# Skip tests (faster)
mvn clean install -DskipTests
```

---

## Next Steps

| Section | Description |
|---------|-------------|
| [Architecture Overview](architecture-overview.md) | Deep dive into system architecture |
| [Installation Guide](installation.md) | Detailed installation instructions |
| [First Steps](first-steps.md) | Guided first integration |
| [API Documentation](../api/README.md) | Complete API reference |

---

## Getting Help

- **Slack**: `#butterfly-dev` for questions
- **GitHub Issues**: Bug reports and features
- **Email**: engineering@254studioz.com

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Documentation Index](../index.md) | Main documentation portal |
| [Development Overview](../../DEVELOPMENT_OVERVIEW.md) | Developer setup guide |
| [Services Overview](../services/README.md) | Individual service guides |
| [Troubleshooting](../onboarding/troubleshooting.md) | Common issues and solutions |
