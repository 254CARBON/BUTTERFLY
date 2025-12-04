# BUTTERFLY CI/CD Pipeline

> Continuous Integration and Deployment processes

**Last Updated**: 2025-12-04  
**Target Audience**: Developers, DevOps engineers

---

## Overview

BUTTERFLY uses GitHub Actions for CI/CD with a multi-stage pipeline ensuring quality and reliable deployments.

---

## Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CI/CD Pipeline Flow                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Push/PR                                                                     │
│     │                                                                        │
│     ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ BUILD & TEST                                                            ││
│  │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        ││
│  │ │   Compile   │ │ Unit Tests  │ │  Lint/Check │ │  Security   │        ││
│  │ │             │ │             │ │             │ │    Scan     │        ││
│  │ └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘        ││
│  └────────────────────────────────────┬────────────────────────────────────┘│
│                                       │                                      │
│                                       ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ INTEGRATION                                                             ││
│  │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                        ││
│  │ │ Integration │ │  Contract   │ │   Build     │                        ││
│  │ │   Tests     │ │   Tests     │ │   Images    │                        ││
│  │ └─────────────┘ └─────────────┘ └─────────────┘                        ││
│  └────────────────────────────────────┬────────────────────────────────────┘│
│                                       │                                      │
│                                       ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ DEPLOY (main branch only)                                               ││
│  │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        ││
│  │ │  Staging    │ │   E2E       │ │ Production  │ │   Verify    │        ││
│  │ │  Deploy     │ │   Tests     │ │   Deploy    │ │             │        ││
│  │ └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘        ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## GitHub Actions Workflows

### Build & Test (`.github/workflows/build.yml` - example pattern)

```yaml
name: Build & Test

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Build
        run: mvn -B clean install -DskipTests
      
      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        with:
          name: build-artifacts
          path: |
            */build/libs/*.jar
            */build/reports/

  test:
    runs-on: ubuntu-latest
    needs: build
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run unit tests
        run: mvn -B test
      
      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: '**/target/surefire-reports/*.xml'
      
      - name: Upload coverage report
        uses: codecov/codecov-action@v3
        with:
          files: '**/target/site/jacoco/*.xml'

  lint:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run Checkstyle
        run: mvn -B checkstyle:check
      
      - name: Run SpotBugs
        run: mvn -B spotbugs:check

  security:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Run OWASP Dependency Check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'butterfly'
          path: '.'
          format: 'SARIF'
          args: '--failOnCVSS 7'
      
      - name: Upload SARIF report
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: dependency-check-report.sarif
      
      - name: Run TruffleHog
        uses: trufflesecurity/trufflehog@main
        with:
          path: ./
          extra_args: --only-verified
```

### Integration Tests (`.github/workflows/integration.yml`)

```yaml
name: Integration Tests

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  integration:
    runs-on: ubuntu-latest
    
    services:
      cassandra:
        image: cassandra:4.1
        ports:
          - 9042:9042
        options: >-
          --health-cmd "cqlsh -e 'describe cluster'"
          --health-interval 30s
          --health-timeout 10s
          --health-retries 10
      
      postgres:
        image: postgres:15
        ports:
          - 5432:5432
        env:
          POSTGRES_DB: perception
          POSTGRES_USER: butterfly
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      
      redis:
        image: redis:7
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run integration tests
        run: mvn -B verify -Pintegration-tests
        env:
          CASSANDRA_HOST: localhost
          POSTGRES_HOST: localhost
          REDIS_HOST: localhost
      
      - name: Upload results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: integration-results
          path: '**/target/failsafe-reports/*.xml'
```

### Deploy (`.github/workflows/deploy.yml`)

```yaml
name: Deploy

on:
  push:
    branches: [main]
  workflow_dispatch:
    inputs:
      environment:
        description: 'Environment to deploy to'
        required: true
        default: 'staging'
        type: choice
        options:
          - staging
          - production

jobs:
  build-images:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [capsule, odyssey, perception, plato, nexus]
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      
      - name: Login to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: ./apps/${{ matrix.service }}
          push: true
          tags: |
            ghcr.io/${{ github.repository }}/${{ matrix.service }}:${{ github.sha }}
            ghcr.io/${{ github.repository }}/${{ matrix.service }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
      
      - name: Scan image
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ghcr.io/${{ github.repository }}/${{ matrix.service }}:${{ github.sha }}
          format: 'sarif'
          output: 'trivy-${{ matrix.service }}.sarif'
          severity: 'CRITICAL,HIGH'
          exit-code: '1'

  deploy-staging:
    runs-on: ubuntu-latest
    needs: build-images
    environment: staging
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Configure kubectl
        uses: azure/setup-kubectl@v3
      
      - name: Set K8s context
        uses: azure/k8s-set-context@v3
        with:
          kubeconfig: ${{ secrets.STAGING_KUBECONFIG }}
      
      - name: Deploy to staging
        run: |
          kubectl set image deployment/capsule \
            capsule=ghcr.io/${{ github.repository }}/capsule:${{ github.sha }} \
            -n butterfly
          kubectl set image deployment/odyssey \
            odyssey=ghcr.io/${{ github.repository }}/odyssey:${{ github.sha }} \
            -n butterfly
          # ... other services
      
      - name: Wait for rollout
        run: |
          kubectl rollout status deployment/capsule -n butterfly
          kubectl rollout status deployment/odyssey -n butterfly
          # ... other services

  e2e-tests:
    runs-on: ubuntu-latest
    needs: deploy-staging
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Run E2E tests
        run: ./butterfly-e2e/run-scenarios.sh
        env:
          E2E_BASE_URL: ${{ secrets.STAGING_URL }}
          E2E_API_KEY: ${{ secrets.STAGING_API_KEY }}

  deploy-production:
    runs-on: ubuntu-latest
    needs: e2e-tests
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    environment: production
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Configure kubectl
        uses: azure/setup-kubectl@v3
      
      - name: Set K8s context
        uses: azure/k8s-set-context@v3
        with:
          kubeconfig: ${{ secrets.PRODUCTION_KUBECONFIG }}
      
      - name: Deploy to production
        run: |
          kubectl set image deployment/capsule \
            capsule=ghcr.io/${{ github.repository }}/capsule:${{ github.sha }} \
            -n butterfly
          # ... other services
      
      - name: Wait for rollout
        run: |
          kubectl rollout status deployment/capsule -n butterfly --timeout=300s
      
      - name: Notify deployment
        uses: slackapi/slack-github-action@v1
        with:
          channel-id: 'C012345'
          slack-message: "Deployed ${{ github.sha }} to production"
        env:
          SLACK_BOT_TOKEN: ${{ secrets.SLACK_BOT_TOKEN }}
```

---

## Release Process

### Semantic Versioning

```
MAJOR.MINOR.PATCH

1.0.0 → 1.0.1  (patch: bug fixes)
1.0.1 → 1.1.0  (minor: new features, backward compatible)
1.1.0 → 2.0.0  (major: breaking changes)
```

### Release Workflow

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      
      - name: Generate changelog
        id: changelog
        uses: TriPSs/conventional-changelog-action@v4
        with:
          skip-tag: true
          output-file: false
      
      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          body: ${{ steps.changelog.outputs.changelog }}
          files: |
            */build/libs/*.jar
```

---

## Environment Configuration

### Secrets Management

| Secret | Scope | Description |
|--------|-------|-------------|
| `STAGING_KUBECONFIG` | Staging | K8s config |
| `PRODUCTION_KUBECONFIG` | Production | K8s config |
| `STAGING_URL` | Staging | API endpoint |
| `STAGING_API_KEY` | Staging | Test API key |
| `CODECOV_TOKEN` | Repository | Coverage upload |
| `SLACK_BOT_TOKEN` | Repository | Notifications |

### Environment Protection

- **Staging**: Auto-deploy on main
- **Production**: Requires approval, main branch only

---

## Quality Gates

### Merge Requirements (PR Checklist)

| Check | Requirement | How to Verify Locally |
|-------|-------------|----------------------|
| Build | Must pass | `mvn clean compile` |
| Unit tests | Must pass | `mvn test` |
| Integration tests | Must pass | `mvn verify` |
| Security scan | No critical/high CVEs | (CI only) |
| Code coverage | ≥ 80% | `mvn test jacoco:report` |
| Code review | 1 approval | (PR process) |
| Lint/Style | Must pass | `mvn -Pquality verify` |
| Commit format | Conventional commits | Husky pre-commit hook |

### Per-Service CI Workflows

| Service | Workflow File | Trigger | What Runs |
|---------|---------------|---------|-----------|
| PERCEPTION | `perception-ci.yml` | PR, push to main | Build, test, lint, coverage |
| PLATO | `plato-ci.yml` | PR, push to main | Build, test, lint, coverage |
| CAPSULE | (via code-quality.yml) | PR, push to main | Build, test |
| ODYSSEY | (via code-quality.yml) | PR, push to main | Build, test |
| butterfly-e2e | `butterfly-e2e.yml` | Nightly, manual | Golden path, scenario suite |
| All | `code-quality.yml` | PR, push | Lint, static analysis |
| All | `security-scan.yml` | PR, push | OWASP dependency check |
| All | `contracts-guardrails.yml` | PR | Avro schema validation |

### What Constitutes "Green PR"

For a PR to be mergeable, all of the following must pass:

1. **Commitlint**: All commit messages follow conventional commits format
2. **Build**: Service compiles without errors
3. **Unit Tests**: All unit tests pass
4. **Coverage**: Coverage threshold met (≥80% for services, ≥90% for butterfly-common)
5. **Lint/Style**: Checkstyle/SpotBugs checks pass
6. **Security**: No critical/high CVEs in dependencies
7. **Code Review**: At least one approval from a maintainer

### Running CI Checks Locally

Before pushing, run these checks to catch issues early:

```bash
# Full check (recommended before PR)
mvn clean verify

# Just compile
mvn compile

# Just tests
mvn test

# Style check (Checkstyle + SpotBugs)
mvn -Pquality verify

# Coverage report
mvn test jacoco:report
# View: target/site/jacoco/index.html

# Commit message validation (if committing)
# Husky runs commitlint automatically on commit-msg hook
```

### Deployment Gates

| Check | Staging | Production |
|-------|---------|------------|
| All tests pass | ✅ | ✅ |
| E2E tests pass | ✅ | ✅ |
| Security scan | ✅ | ✅ |
| Manual approval | ❌ | ✅ |
| Rollback plan | ❌ | ✅ |

---

## Rollback Procedures

### Automatic Rollback

```yaml
# In deploy job
- name: Deploy with rollback
  run: |
    kubectl set image deployment/$SERVICE $SERVICE=$IMAGE -n butterfly
    if ! kubectl rollout status deployment/$SERVICE -n butterfly --timeout=300s; then
      echo "Deployment failed, rolling back..."
      kubectl rollout undo deployment/$SERVICE -n butterfly
      exit 1
    fi
```

### Manual Rollback

```bash
# Rollback to previous revision
kubectl rollout undo deployment/capsule -n butterfly

# Rollback to specific revision
kubectl rollout undo deployment/capsule -n butterfly --to-revision=2
```

---

## Troubleshooting CI Failures

### Common Issues

| Failure | Cause | Solution |
|---------|-------|----------|
| Commitlint fails | Commit message format | Use `<type>(<scope>): <subject>` |
| Checkstyle fails | Code style violations | Run `mvn checkstyle:check` and fix reported issues |
| Coverage too low | Insufficient tests | Add tests for new code |
| Security scan fails | Vulnerable dependency | Update dependency version |
| Integration test fails | Missing infrastructure | Check Docker containers |

### Debugging Workflow Failures

1. Check the CI job logs in GitHub Actions
2. Reproduce locally using the commands above
3. For flaky tests, check for race conditions or external dependencies
4. For PERCEPTION chaos tests, see `PERCEPTION/docs/runbooks/ci-pipeline-troubleshooting.md`

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Contributing](contributing.md) | Contribution guide |
| [Testing Strategy](testing-strategy.md) | Testing approach |
| [Deployment](../operations/deployment/README.md) | Deployment guides |
| [PERCEPTION CI Troubleshooting](../../PERCEPTION/docs/runbooks/ci-pipeline-troubleshooting.md) | CI debugging |
