# BUTTERFLY Developer Onboarding

> Your guide to becoming productive in the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: New contributors, engineers joining the team

---

## Welcome

Welcome to the BUTTERFLY ecosystem! This onboarding guide will help you understand the project structure, set up your development environment, and become productive quickly.

## Learning Path

We recommend following this sequence:

### Week 1: Foundation

1. **[Project Structure](project-structure.md)** - Understand the monorepo layout and service boundaries
2. **[Package Guide](package-guide.md)** - Deep dive into PLATO's engine/, governance/, integration/, and security/ packages
3. **[Development Overview](../../DEVELOPMENT_OVERVIEW.md)** - Set up your local environment

### Week 2: Hands-On

4. **[Common Workflows](common-workflows.md)** - Build, test, debug, and deploy
5. **Read the [Contributing Guide](../development/contributing.md)** - Understand branch naming, commits, and PR process
6. **Run the E2E suite** - `./butterfly-e2e/run-golden-path.sh`
7. **Make your first change** - Pick a "good first issue" from the backlog

### Week 3: Deep Dives

7. **Service-specific guides**:
   - [PLATO Getting Started](../../PLATO/docs/getting-started/quickstart.md)
   - [PERCEPTION Getting Started](../../PERCEPTION/GETTING_STARTED.md)
   - [CAPSULE Development](../../CAPSULE/DEVELOPMENT.md)
   - [ODYSSEY Setup](../../ODYSSEY/docs/DEV_ENVIRONMENT_SETUP.md)

8. **Architecture Decision Records** - [docs/adr/](../adr/)

## Quick Reference

| Resource | Purpose |
|----------|---------|
| [DEVELOPMENT_OVERVIEW.md](../../DEVELOPMENT_OVERVIEW.md) | Fast path to running stack |
| [docs/development/contributing.md](../development/contributing.md) | **Canonical contributing guide** (branch naming, commits, PR process) |
| [docs/development/testing-strategy.md](../development/testing-strategy.md) | Per-service test matrices |
| [docs/templates/](../templates/) | Documentation templates |
| [Troubleshooting](troubleshooting.md) | Common issues and solutions |

### Contributing to Services

| Service | Contributing Guide | Development Setup |
|---------|-------------------|-------------------|
| **CAPSULE** | [CONTRIBUTING.md](../../CAPSULE/CONTRIBUTING.md) | [DEVELOPMENT.md](../../CAPSULE/DEVELOPMENT.md) |
| **ODYSSEY** | [CONTRIBUTING.md](../../ODYSSEY/CONTRIBUTING.md) | [docs/DEV_ENVIRONMENT_SETUP.md](../../ODYSSEY/docs/DEV_ENVIRONMENT_SETUP.md) |
| **PERCEPTION** | [CONTRIBUTING.md](../../PERCEPTION/CONTRIBUTING.md) | [GETTING_STARTED.md](../../PERCEPTION/GETTING_STARTED.md) |
| **PLATO** | [CONTRIBUTING.md](../../PLATO/CONTRIBUTING.md) | [docs/getting-started/quickstart.md](../../PLATO/docs/getting-started/quickstart.md) |
| **NEXUS** | [CONTRIBUTING.md](../../butterfly-nexus/CONTRIBUTING.md) | [README.md](../../butterfly-nexus/README.md) |
| **butterfly-common** | [CONTRIBUTING.md](../../butterfly-common/CONTRIBUTING.md) | [README.md](../../butterfly-common/README.md) |

## Key Concepts

Before diving in, familiarize yourself with these core concepts:

### BUTTERFLY Services

| Service | Role | Primary Tech |
|---------|------|--------------|
| **PLATO** | Governance and intelligence | Java 17, Spring Boot WebFlux, Cassandra |
| **PERCEPTION** | Reality integration mesh, signals | Java 17, Spring Boot, Kafka, PostgreSQL |
| **CAPSULE** | 4D historical storage | Java 17, Spring Boot, TimescaleDB |
| **ODYSSEY** | Strategic cognition, futures | Java 17, Spring Boot, JanusGraph |
| **SYNAPSE** | Decision and execution | (Planned) |

### PLATO Primitives

PLATO manages four unified primitives:

- **Specs** - Typed, versioned intent and contract definitions
- **Artifacts** - Concrete objects (SQL transforms, features, equations)
- **Proofs** - Machine-checkable evidence that Specs/Artifacts satisfy properties
- **Plans** - Executable workflows that produce Artifacts and Proofs

### Communication Patterns

- **Kafka** - Event-driven async communication between services
- **REST APIs** - Synchronous request/response for queries and commands
- **WebSocket** - Real-time progress streaming (e.g., plan execution)

## Getting Help

- **Slack**: `#butterfly-dev` for general questions
- **Code Review**: Tag `@butterfly-core` for PR reviews
- **Documentation Issues**: File in the appropriate service repo

## Next Steps

Start with [Project Structure](project-structure.md) to understand how the codebase is organized.

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Project Structure](project-structure.md) | Monorepo layout and module dependencies |
| [Package Guide](package-guide.md) | PLATO package deep dive |
| [Common Workflows](common-workflows.md) | Build, test, debug, deploy |
| [Troubleshooting](troubleshooting.md) | Common issues and solutions |

