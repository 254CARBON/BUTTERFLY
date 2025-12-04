# BUTTERFLY Development Guide

> Developer resources for contributing to BUTTERFLY

**Last Updated**: 2025-12-04  
**Target Audience**: Developers, contributors

---

## Overview

This section provides comprehensive guidance for developers working on the BUTTERFLY ecosystem.

---

## Section Contents

| Document | Description |
|----------|-------------|
| [Contributing](contributing.md) | How to contribute (canonical guide) |
| [Coding Standards](coding-standards.md) | Code style guide |
| [Testing Strategy](testing-strategy.md) | Testing approach |
| [CI/CD](ci-cd.md) | Build and deployment pipelines |
| [Templates](../templates/) | Documentation templates (module README, runbook, ADR) |

---

## Quick Start

### Prerequisites

- Java 17 (Temurin/Adoptium recommended)
- Maven 3.9+
- Docker and Docker Compose
- Node.js 20+ (for frontend tools)
- Git

### Clone Repository

```bash
git clone https://github.com/254CARBON/BUTTERFLY.git
cd BUTTERFLY/apps
```

### Setup Development Environment

For a unified dev setup, prefer the root scripts described in `DEVELOPMENT_OVERVIEW.md`:

```bash
# One-time setup (checks tools, installs butterfly-common, hooks, portal deps)
./scripts/setup.sh

# Start dev Kafka stack (see DEVELOPMENT_OVERVIEW for flags/profiles)
./scripts/dev-up.sh
```

You can still work on individual services in isolation using Maven and per-service Docker Compose files (see sections below and service-specific docs).

---

## Project Structure

```
apps/
├── butterfly-common/     # Shared library and Avro contracts
├── butterfly-nexus/      # Integration cortex / gateway
├── butterfly-e2e/        # E2E harness and scenarios
├── CAPSULE/              # 4D Atomic History Service
├── ODYSSEY/              # Strategic Cognition Engine
├── PERCEPTION/           # Sensory Layer
├── PLATO/                # Governance Service
└── docs/                 # Unified ecosystem documentation
```

---

## Development Workflow

### Branch Strategy

```
main
  │
  ├── develop           # Integration branch
  │     │
  │     ├── feature/*   # New features
  │     ├── bugfix/*    # Bug fixes
  │     └── refactor/*  # Refactoring
  │
  ├── release/*         # Release preparation
  │
  └── hotfix/*          # Production fixes
```

### Commit Message Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting
- `refactor`: Code refactoring
- `test`: Adding tests
- `chore`: Maintenance

**Examples:**
```
feat(capsule): add time-travel query optimization

fix(nexus): resolve circuit breaker timeout issue

docs(api): update authentication examples
```

### Pull Request Process

1. Create feature branch from `develop`
2. Implement changes with tests
3. Run `mvn clean verify` locally (from `apps/`)
4. Create PR with description
5. Address review feedback
6. Merge after approval

---

## Local Development

### Running Services

```bash
# Single service with hot reload (example)
mvn -f CAPSULE/pom.xml spring-boot:run
mvn -f PERCEPTION/pom.xml spring-boot:run
mvn -f PLATO/pom.xml spring-boot:run

# Service via Docker Compose (per-module)
docker compose -f CAPSULE/docker-compose.yml up
docker compose -f PERCEPTION/docker-compose.yml up
docker compose -f PLATO/docker-compose.yml up
docker compose -f butterfly-nexus/docker-compose.yml up
```

### Database Setup

Database bootstrap and migration flows differ per service. Refer to:

- `CAPSULE/docs/operations/deployment.md` and `CAPSULE/schema/README.md`
- `PERCEPTION/docs/operations/deployment.md`
- `PLATO/docs/getting-started/configuration.md`

for authoritative instructions on Cassandra/PostgreSQL schemas and tooling.

### Debugging

```bash
# Start with debug port
mvn -f CAPSULE/pom.xml spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Connect IDE debugger to port 5005
```

---

## IDE Setup

### IntelliJ IDEA (Recommended)

1. Open project root as Maven project
2. Enable annotation processing
3. Install plugins:
   - Lombok
   - Spring Boot
   - CheckStyle

### VS Code

1. Install Java Extension Pack
2. Install Spring Boot Extension Pack
3. Configure `settings.json`:

```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.format.settings.url": ".java-format.xml"
}
```

---

## Useful Commands

```bash
# Build everything (from apps/)
mvn clean verify

# Run only tests
mvn test

# Run integration tests where defined
mvn verify -Pintegration-tests

# Code quality (Checkstyle + SpotBugs)
mvn -Pquality verify

# Coverage report (aggregate or per-module)
mvn test jacoco:report
```

---

## Getting Help

| Resource | Description |
|----------|-------------|
| `#dev-butterfly` | Slack channel |
| GitHub Issues | Bug reports |
| GitHub Discussions | Questions |
| Weekly office hours | Live support |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Architecture](../architecture/README.md) | System design |
| [API Documentation](../api/README.md) | API reference |
| [Operations](../operations/README.md) | Deployment |
