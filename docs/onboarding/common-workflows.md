# Common Development Workflows

> Build, test, debug, and deploy procedures for BUTTERFLY services

**Last Updated**: 2025-12-03

---

## Quick Reference

| Task | Command |
|------|---------|
| Build all | `mvn clean install -DskipTests` |
| Run PLATO | `./PLATO/scripts/run-local.sh` |
| Run tests | `mvn test` |
| Run E2E | `./butterfly-e2e/run-golden-path.sh` |
| Start infra | `./scripts/dev-up.sh` |
| Stop infra | `./scripts/dev-down.sh` |

---

## 1. Environment Setup

### Prerequisites Check

```bash
# Run the setup script (checks and installs)
./scripts/setup.sh

# Or just check prerequisites
./scripts/setup.sh --check
```

**Required versions:**
- Java 17+
- Maven 3.9+
- Node.js 20+
- Docker 20+

### Install Shared Library

```bash
# butterfly-common must be installed first
mvn -f butterfly-common/pom.xml clean install -DskipTests
```

### Start Development Infrastructure

```bash
# Start Kafka and basic infrastructure
./scripts/dev-up.sh

# Start with full stack (including ODYSSEY)
FASTPATH_FULL_STACK=1 ./scripts/dev-up.sh --profile odyssey

# Stop all infrastructure
./scripts/dev-down.sh
```

---

## 2. Building

### Build Entire Ecosystem

```bash
# Build all services without tests
mvn clean install -DskipTests

# Build with tests
mvn clean verify
```

### Build Specific Service

```bash
# PLATO
mvn -f PLATO/pom.xml clean package

# PERCEPTION (multi-module)
mvn -f PERCEPTION/pom.xml clean package

# CAPSULE
mvn -f CAPSULE/pom.xml clean package

# ODYSSEY (multi-module)
mvn -f ODYSSEY/pom.xml clean package
```

### Build Docker Images

```bash
# PLATO
docker build -t plato-service:latest -f PLATO/Dockerfile PLATO/

# PERCEPTION
docker build -t perception-service:latest -f PERCEPTION/Dockerfile PERCEPTION/
```

---

## 3. Running Services

### PLATO

```bash
# Option A: Local script (recommended)
./PLATO/scripts/run-local.sh

# Option B: Maven
cd PLATO && mvn spring-boot:run

# Option C: Docker
./PLATO/scripts/dev-up.sh

# With full infrastructure (Cassandra, Kafka, Prometheus)
./PLATO/scripts/dev-up.sh full
```

**Access points:**
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/prometheus

### PERCEPTION

```bash
# Start backend
cd PERCEPTION/perception-api
mvn spring-boot:run

# Start acquisition with dev console
cd PERCEPTION/perception-acquisition
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Start portal (frontend)
cd PERCEPTION/perception-portal
npm install
npm run dev
```

**Access points:**
- API: http://localhost:8080/api/v1
- Portal: http://localhost:3000
- Camel routes: http://localhost:8080/actuator/camel/routes

### Docker Compose (Full Stack)

```bash
# PLATO with dependencies
docker-compose -f PLATO/docker-compose.yml up

# PERCEPTION with dependencies
docker-compose -f PERCEPTION/docker-compose.yml up

# E2E test stack
docker-compose -f butterfly-e2e/docker-compose.full.yml up --profile odyssey
```

---

## 4. Testing

### Unit Tests

```bash
# All services
mvn test

# Specific service
mvn -f PLATO/pom.xml test
mvn -f PERCEPTION/pom.xml test

# Specific test class
mvn -f PLATO/pom.xml test -Dtest=SpecServiceTest

# Test pattern
mvn -f PLATO/pom.xml test -Dtest="*Integration*"
```

### Integration Tests

```bash
# Run integration tests (requires Testcontainers)
mvn -f PLATO/pom.xml verify

# PERCEPTION integration tests
mvn -f PERCEPTION/pom.xml verify -Pintegration-tests
```

### E2E Tests

```bash
# Golden path integration test
./butterfly-e2e/run-golden-path.sh

# Full scenario suite
./butterfly-e2e/run-scenarios.sh

# Without starting Docker (use existing infra)
START_DEV_STACK=0 ./butterfly-e2e/run-scenarios.sh
```

### Test Coverage

```bash
# Generate coverage report
mvn -f PLATO/pom.xml test jacoco:report

# View report
open PLATO/target/site/jacoco/index.html

# Check coverage thresholds
mvn -f PLATO/pom.xml verify  # Fails if below threshold
```

### Frontend Tests (PERCEPTION Portal)

```bash
cd PERCEPTION/perception-portal

# Unit tests
npm run test

# Watch mode
npm run test:watch

# E2E tests (Playwright)
npm run test:e2e

# E2E with UI
npm run test:e2e:ui
```

---

## 5. Debugging

### IDE Setup

**IntelliJ IDEA:**
1. Import as Maven project
2. Enable annotation processing (Lombok)
3. Set Java 17 SDK

**VS Code:**
1. Install Java Extension Pack
2. Install Lombok extension
3. Configure `java.configuration.runtimes`

### Remote Debugging

```bash
# Start with debug port
JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  mvn -f PLATO/pom.xml spring-boot:run

# Or with script
./PLATO/scripts/run-local.sh debug
```

Then attach debugger to `localhost:5005`.

### Logging

```bash
# Increase log level
LOGGING_LEVEL_COM_Z254=DEBUG mvn spring-boot:run

# Or in application.yml
logging:
  level:
    com.z254.butterfly.plato: DEBUG
```

### Useful Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Service health status |
| `/actuator/env` | Environment configuration |
| `/actuator/beans` | Spring beans |
| `/actuator/mappings` | Request mappings |
| `/actuator/metrics` | Available metrics |
| `/actuator/prometheus` | Prometheus metrics |

---

## 6. Committing Changes

### Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```bash
# Format
<type>(<scope>): <subject>

# Examples
git commit -m "feat(plato): add governance policy evaluation API"
git commit -m "fix(perception): correct signal clustering algorithm"
git commit -m "docs(onboarding): add common workflows guide"
git commit -m "test(plato): add WebSocket integration tests"
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, `revert`

### Pre-commit Hooks

Husky runs automatically on commit:
- Commit message validation (commitlint)
- TypeScript/JavaScript linting (ESLint)

```bash
# Bypass hooks (not recommended)
git commit --no-verify -m "message"

# Reinstall hooks if needed
npm install
npx husky
```

### Branch Naming

```bash
# Feature
git checkout -b feature/add-governance-api

# Bug fix
git checkout -b fix/websocket-timeout

# Documentation
git checkout -b docs/onboarding-guide

# Refactor
git checkout -b refactor/extract-policy-engine
```

---

## 7. Pull Requests

### Before Submitting

```bash
# Run all checks
mvn clean verify

# Check code style
mvn checkstyle:check

# Security scan
mvn org.owasp:dependency-check-maven:check
```

### PR Checklist

- [ ] Tests pass locally
- [ ] Coverage maintained or improved
- [ ] Documentation updated
- [ ] Commit messages follow conventions
- [ ] No merge conflicts

### Review Process

1. Create PR with clear description
2. Link related issues
3. Wait for CI checks
4. Address review comments
5. Squash and merge after approval

---

## 8. Deployment

### Local Deployment

```bash
# Build JAR
mvn -f PLATO/pom.xml clean package -DskipTests

# Run JAR
java -jar PLATO/target/plato-service-*.jar
```

### Docker Deployment

```bash
# Build image
docker build -t plato-service:latest -f PLATO/Dockerfile PLATO/

# Run container
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e PLATO_PERSISTENCE_TYPE=inmemory \
  plato-service:latest
```

### CI/CD Pipeline

The `.github/workflows/` pipelines handle:
- `plato-ci.yml` - Build, test, coverage on PR
- `perception-ci.yml` - PERCEPTION CI pipeline
- `butterfly-e2e.yml` - Cross-service E2E tests
- `deploy.yml` - Production deployment on merge

---

## 9. Troubleshooting Quick Fixes

| Issue | Solution |
|-------|----------|
| `butterfly-common` not found | `mvn -f butterfly-common/pom.xml install` |
| Port 8080 in use | `lsof -i :8080` then `kill -9 <PID>` |
| Docker permission denied | `sudo usermod -aG docker $USER` |
| Testcontainers timeout | Increase Docker resources |
| Kafka connection refused | `./scripts/dev-up.sh` |

See [Troubleshooting](troubleshooting.md) for detailed solutions.

---

## Next Steps

- Review [Project Structure](project-structure.md) for codebase organization
- Explore [Package Guide](package-guide.md) for PLATO internals
- Check [Troubleshooting](troubleshooting.md) if issues arise

