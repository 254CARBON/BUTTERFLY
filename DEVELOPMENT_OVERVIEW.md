# BUTTERFLY Developer Overview

This overview unifies the main dev entry points across CAPSULE, ODYSSEY, PERCEPTION, and PLATO and highlights the end-to-end integration harness.

**Last Updated**: 2025-12-03

## Quick Start

Run the setup script to configure your development environment:

```bash
./scripts/setup.sh
```

This will:
1. Check all prerequisites (Java 17, Maven 3.9+, Node 20+, Docker)
2. Install butterfly-common to your local Maven repository
3. Set up Husky git hooks for commit message validation and linting
4. Install portal (frontend) dependencies

Use `--check` to only verify prerequisites, or `--start` to also start the dev infrastructure.

## Prerequisites

| Tool | Minimum Version | Purpose |
|------|-----------------|---------|
| Java JDK | 17 | Backend services |
| Maven | 3.9+ | Build tool |
| Node.js | 20+ | Frontend portal, tooling |
| Docker | 20+ | Dev infrastructure |
| Docker Compose | v2+ | Multi-container orchestration |

## Fast Path to a Working Stack

```bash
# 1. One-time setup
./scripts/setup.sh

# 2. Start Kafka (without auto-seeding)
FASTPATH_SKIP_STUB=1 ./scripts/dev-up.sh

# 3. For full stack with ODYSSEY in Docker:
FASTPATH_FULL_STACK=1 ./scripts/dev-up.sh --profile odyssey

# 4. Run ODYSSEY locally (alternative to Docker)
mvn -f ODYSSEY/pom.xml spring-boot:run -Dhft.kafka.bootstrap-servers=localhost:9092

# 5. Publish test events
./scripts/fastpath-cli.sh publish --scenario golden-path --bootstrap localhost:9092

# 6. Generate custom events
./scripts/fastpath-cli.sh generate --node rim:entity:finance:EURUSD --stress 0.97 --send
```

### Running Individual Services

```bash
# CAPSULE
docker compose -f CAPSULE/docker-compose.yml up

# PERCEPTION (dev profile with hot reload)
docker compose -f PERCEPTION/docker-compose.dev.yml up

# ODYSSEY
docker compose -f ODYSSEY/docker-compose.yml up

# PLATO (with in-memory persistence)
./PLATO/scripts/dev-up.sh

# PLATO (with full infrastructure: Cassandra, Kafka, Prometheus)
./PLATO/scripts/dev-up.sh full
```

## Pre-commit Hooks

The repository uses Husky to enforce code quality standards on every commit:

### Commit Message Format

Commits must follow [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, `revert`

**Examples:**
```bash
# Feature
git commit -m "feat(perception): add signal clustering API"

# Bug fix
git commit -m "fix(plato): correct temporal rule extraction"

# Documentation
git commit -m "docs(capsule): update SDK examples"
```

### Pre-commit Checks

On every commit, the following checks run automatically:
- **TypeScript/JavaScript linting** for portal files (ESLint)
- **Commit message validation** via commitlint

To bypass hooks temporarily (not recommended):
```bash
git commit --no-verify -m "your message"
```

## E2E Harness + Failure Injection

```bash
# Run the full scenario suite (positive + negative cases)
./butterfly-e2e/run-scenarios.sh

# Disable docker management when using external Kafka
START_DEV_STACK=0 ./butterfly-e2e/run-scenarios.sh
```

**Scenario catalog:** `butterfly-e2e/scenarios/catalog.json`

Payloads: `edge-high-stress`, `minimal-ok`, `invalid-node`, `kafka-hiccup`, etc.

### Full-stack Smoke Test

```bash
# Run ODYSSEY inside Docker with Kafka
docker compose -f butterfly-e2e/docker-compose.full.yml up --profile odyssey
```

## Golden-path Integration Test

```bash
# Installs butterfly-common and runs ODYSSEY HftGoldenPathIntegrationTest
./butterfly-e2e/run-golden-path.sh
```

Mirrored in CI via `.github/workflows/butterfly-e2e.yml`.

## Testing

### Backend Tests

```bash
# All modules
mvn test

# Specific module
mvn -f PERCEPTION/pom.xml test
mvn -f PLATO/pom.xml test
mvn -f CAPSULE/pom.xml test
mvn -f ODYSSEY/pom.xml test

# Integration tests
mvn -f PERCEPTION/pom.xml verify -Pintegration-tests

# Chaos tests (PERCEPTION)
mvn -f PERCEPTION/pom.xml test -pl perception-api -Dgroups=chaos

# With coverage
mvn test jacoco:report
```

### Frontend Tests (PERCEPTION Portal)

```bash
cd PERCEPTION/perception-portal

# Unit tests
npm run test

# Watch mode
npm run test:watch

# E2E tests
npm run test:e2e

# E2E with UI
npm run test:e2e:ui
```

## Tooling and Style

- **Java 17** across all repos, Maven 3.8+
- Prefer Google Java Format or IntelliJ default formatter
- Keep Lombok usage minimal
- TypeScript strict mode for frontend code
- Conventional commit messages enforced via git hooks

## Where to Go Next

| Module | Getting Started |
|--------|-----------------|
| CAPSULE | `CAPSULE/DEVELOPMENT.md` |
| ODYSSEY | `ODYSSEY/docs/DEV_ENVIRONMENT_SETUP.md` |
| PERCEPTION | `PERCEPTION/GETTING_STARTED.md` |
| PLATO | `PLATO/docs/getting-started/quickstart.md` |

### PLATO-Specific Resources

| Document | Description |
|----------|-------------|
| [PLATO README](PLATO/README.md) | Project overview and quick start |
| [Governance Guide](PLATO/docs/architecture/governance.md) | Control Plane and meta-specs |
| [WebSocket Guide](PLATO/docs/architecture/websocket.md) | Real-time plan execution events |
| [Security Architecture](PLATO/docs/architecture/security.md) | OAuth2, RBAC, API keys |
| [OpenAPI Spec](PLATO/openapi/plato-v1.yaml) | Formal API specification |

## Troubleshooting

### Setup Issues

**Java version mismatch:**
```bash
# Check your Java version
java -version

# On macOS with multiple JDKs
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

**Maven not finding butterfly-common:**
```bash
# Rebuild and install
mvn -f butterfly-common/pom.xml clean install -DskipTests
```

**Husky hooks not running:**
```bash
# Reinstall hooks
npm install
npx husky
```

**Kafka connection refused:**
```bash
# Make sure the dev stack is running
./scripts/dev-up.sh

# Check Kafka logs
docker compose -f butterfly-e2e/docker-compose.yml logs kafka
```

### CI/CD Issues

See `PERCEPTION/docs/runbooks/ci-pipeline-troubleshooting.md` for common CI failures and fixes.

## Minimal Multi-service Recipe

1. Start Kafka: `./scripts/dev-up.sh`
2. Launch ODYSSEY with `hft.kafka.enabled=true` to consume `rim.fast-path`
3. Check producer logs: `docker compose -f butterfly-e2e/docker-compose.yml logs fastpath-producer`
4. Monitor ODYSSEY's `ReflexActionEvent` log statements

## Related Documentation

- [DX_NOTES.md](DX_NOTES.md) - Additional developer experience notes
- [docs/adr/](docs/adr/) - Architecture Decision Records
- [docs/onboarding/](docs/onboarding/) - New contributor onboarding guide
- [PERCEPTION/CONTRIBUTING.md](PERCEPTION/CONTRIBUTING.md) - PERCEPTION contribution guide
- [PLATO/CONTRIBUTING.md](PLATO/CONTRIBUTING.md) - PLATO contribution guide
- [CAPSULE/CONTRIBUTING.md](CAPSULE/CONTRIBUTING.md) - CAPSULE contribution guide
- [PLATO/ENGINEERING_ROADMAP.md](PLATO/ENGINEERING_ROADMAP.md) - PLATO strategic roadmap
