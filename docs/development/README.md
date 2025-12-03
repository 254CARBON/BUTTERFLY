# BUTTERFLY Development Guide

> Developer resources for contributing to BUTTERFLY

**Last Updated**: 2025-12-03  
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

- Java 21 (Temurin/Adoptium recommended)
- Maven 3.9+ or Gradle 8.x
- Docker and Docker Compose
- Node.js 18+ (for frontend tools)
- Git

### Clone Repository

```bash
git clone https://github.com/your-org/butterfly.git
cd butterfly/apps
```

### Setup Development Environment

```bash
# Install pre-commit hooks
npm install
npx husky install

# Start infrastructure
docker compose -f docker-compose.infra.yml up -d

# Build all services
./gradlew build

# Run specific service
./gradlew :capsule:bootRun
```

---

## Project Structure

```
apps/
├── butterfly-common/     # Shared library
├── butterfly-nexus/      # API Gateway
├── butterfly-e2e/        # E2E tests
├── capsule/              # 4D Atomic History Service
├── odyssey/              # Strategic Cognition Engine
├── perception/           # Sensory Layer
├── plato/                # Governance Service
└── docs/                 # Documentation
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
3. Run `./gradlew check` locally
4. Create PR with description
5. Address review feedback
6. Merge after approval

---

## Local Development

### Running Services

```bash
# Single service with hot reload
./gradlew :capsule:bootRun

# All services via Docker Compose
docker compose up

# Specific service
docker compose up capsule
```

### Database Setup

```bash
# Cassandra
docker compose up cassandra
./gradlew :capsule:cassandraMigrate

# PostgreSQL
docker compose up postgres
./gradlew :perception:flywayMigrate
```

### Debugging

```bash
# Start with debug port
./gradlew :capsule:bootRun --debug-jvm

# Connect IDE debugger to port 5005
```

---

## IDE Setup

### IntelliJ IDEA (Recommended)

1. Open project root as Gradle project
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
# Build everything
./gradlew build

# Run tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Check code quality
./gradlew check

# Format code
./gradlew spotlessApply

# Update dependencies
./gradlew dependencyUpdates

# Generate API docs
./gradlew openApiGenerate
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

