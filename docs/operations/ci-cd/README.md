# BUTTERFLY CI/CD Pipeline Documentation

> Unified documentation for all continuous integration and deployment workflows

**Last Updated**: 2025-12-03  
**Target Audience**: All contributors, DevOps engineers, release managers

---

## Overview

BUTTERFLY uses GitHub Actions for CI/CD with 8 specialized workflows covering build, test, security, quality, and deployment. All pipelines are designed for fast feedback and reliable deployments.

## Pipeline Catalog

| Workflow | Trigger | Purpose | Avg Duration |
|----------|---------|---------|--------------|
| [perception-ci.yml](#perception-ci) | PR, Push | Full PERCEPTION CI (backend + portal) | ~12 min |
| [plato-ci.yml](#plato-ci) | PR, Push | PLATO build, test, Docker | ~8 min |
| [deploy.yml](#deploy) | Tag, Manual | Production deployment via Helm | ~15 min |
| [code-quality.yml](#code-quality) | PR, Push | Checkstyle, SpotBugs analysis | ~5 min |
| [security-scan.yml](#security-scan) | PR, Push, Weekly | CodeQL, OWASP, secret scanning | ~20 min |
| [contracts-guardrails.yml](#contracts-guardrails) | PR, Push | Avro schema ownership | ~1 min |
| [butterfly-e2e.yml](#butterfly-e2e) | PR, Nightly | Cross-service E2E tests | ~10 min |
| [perception-performance.yml](#perception-performance) | Manual, Nightly | Gatling performance tests | ~30 min |

---

## Pipeline Details

### perception-ci

**File**: `.github/workflows/perception-ci.yml`  
**Triggers**: Push/PR to `main`, `develop` on `PERCEPTION/**`, `butterfly-common/**`

**Jobs:**
1. **build** - Maven compile + unit tests (parallel threads)
2. **integration-tests** - PostgreSQL + Redis services, integration test suite
3. **chaos-tests** - Resilience testing with chaos scenarios
4. **coverage** - JaCoCo coverage with 70% threshold, Codecov upload
5. **portal-lint** - ESLint for perception-portal
6. **portal-test** - Vitest unit tests
7. **portal-e2e** - Playwright E2E tests
8. **portal-build** - Production build validation
9. **ci-summary** - Consolidated status report

**Key Artifacts:**
- Test reports (JUnit XML)
- Coverage reports (JaCoCo)
- Playwright reports and traces

---

### plato-ci

**File**: `.github/workflows/plato-ci.yml`  
**Triggers**: Push/PR to `main`, `develop` on `PLATO/**`

**Jobs:**
1. **build** - Maven build + unit tests
2. **integration-tests** - Cassandra service container, schema init
3. **coverage** - JaCoCo with 80% target, 70% hard minimum
4. **docker-build** - Docker image build + health check (main branch only)
5. **security-scan** - OWASP dependency check

**Key Artifacts:**
- Dependency check reports (HTML)
- Docker images tagged with SHA

---

### deploy

**File**: `.github/workflows/deploy.yml`  
**Triggers**: Git tags `v*.*.*`, manual dispatch with environment selection

**Environments:**
- `dev` - Development cluster
- `stage` - Staging cluster (canary deployment)
- `prod` - Production cluster (canary + promote)

**Jobs:**
1. **verify** - Build, unit tests, Checkstyle, SpotBugs
2. **build_and_scan** - Docker build, Trivy + Grype security scan, push to GHCR
3. **deploy** - Helm-based deployment with canary rollout

**Deployment Strategy:**
```
Tag v*.*.* → Build → Scan → Canary (1 replica) → Verify → Promote (full rollout) → Cleanup canary
```

---

### code-quality

**File**: `.github/workflows/code-quality.yml`  
**Triggers**: PR to `main`, `develop` on Java files

**Strategy**: Gradual enforcement - baseline violations allowed, new violations warned

**Jobs:**
1. **checkstyle** - Google style guide validation
2. **spotbugs** - Static analysis with Max effort, Low threshold
3. **new-code-quality** - Strict checks on newly added files

**PR Annotations**: Results annotated directly on PR diffs

---

### security-scan

**File**: `.github/workflows/security-scan.yml`  
**Triggers**: PR, Push, Weekly schedule (Sundays 2 AM UTC)

**Jobs:**
1. **codeql** - GitHub CodeQL SAST for all modules
2. **dependency-check** - OWASP dependency-check (fails on CVSS >= 7)
3. **secret-scan** - TruffleHog + Gitleaks
4. **license-check** - Third-party license compliance
5. **semgrep** - Additional SAST rules
6. **security-summary** - Consolidated security report

**SARIF Upload**: Results uploaded to GitHub Security tab

---

### contracts-guardrails

**File**: `.github/workflows/contracts-guardrails.yml`  
**Triggers**: All pushes and PRs

**Purpose**: Enforce Avro schema ownership in `butterfly-common`

**Rule**: All `.avsc` files must live in `butterfly-common/src/main/avro/`

---

### butterfly-e2e

**File**: `.github/workflows/butterfly-e2e.yml`  
**Triggers**: PR on E2E paths, Nightly at 5 AM UTC

**Jobs:**
1. **scenario-suite** - Golden path + negative scenario tests
2. **full-stack-smoke** - Optional Kafka + ODYSSEY integration (manual only)

**Key Scripts:**
- `butterfly-e2e/run-scenarios.sh` - Scenario harness
- `scripts/fastpath-cli.sh` - Event publishing CLI

---

### perception-performance

**File**: `.github/workflows/perception-performance.yml`  
**Triggers**: Manual dispatch, Nightly at 2 AM UTC

**Simulations:**
- `ApiSmokeSimulation` - Basic API health (default)
- `AcquisitionIngestionSimulation` - Ingestion load test
- `CriticalPathSimulation` - Core workflow performance
- `StreamCAndCapsuleSimulation` - Cross-service performance

**Artifacts**: Gatling HTML reports (30 day retention)

---

## Metrics and Tracking

### Build Duration Tracking

Each workflow generates a GitHub Step Summary with timing information. To view:

1. Go to Actions tab in GitHub
2. Select a workflow run
3. Scroll to "Summary" section

### PR-to-Merge Time

Track using GitHub's built-in Insights:
- Repository → Insights → Pull requests
- Filter by merge date range

### Coverage Trends

Coverage is uploaded to Codecov for trend analysis:
- PERCEPTION: https://codecov.io/gh/254CARBON/BUTTERFLY?flag=perception
- PLATO: https://codecov.io/gh/254CARBON/BUTTERFLY?flag=plato

### Recommended Dashboards

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| perception-ci duration | < 15 min | > 20 min |
| plato-ci duration | < 10 min | > 15 min |
| PR-to-merge time | < 4 hours | > 8 hours |
| Code coverage | > 80% | < 70% |
| Security findings | 0 critical | Any critical |

---

## Required Checks

Before merging to `main` or `develop`, these checks must pass:

### For PERCEPTION Changes
- `Build and Unit Tests`
- `Integration Tests`
- `Portal Lint`
- `Portal Unit Tests`
- `Code Coverage` (warning below 70%)

### For PLATO Changes
- `Build and Test`
- `Integration Tests`
- `Code Coverage` (fails below 70%)

### For All Changes
- `Checkstyle Analysis`
- `SpotBugs Analysis`
- `CodeQL SAST Analysis`
- `OWASP Dependency Check`
- `Secret Scanning`

---

## Quick Commands

```bash
# Run same checks as CI locally
mvn clean verify                           # Full build with tests
mvn checkstyle:check                       # Checkstyle validation
mvn spotbugs:check                         # SpotBugs analysis
mvn org.owasp:dependency-check-maven:check # Dependency scan

# Portal checks
cd PERCEPTION/perception-portal
npm run lint                               # ESLint
npm run test                               # Vitest
npm run test:e2e                           # Playwright E2E
npm run build                              # Production build

# E2E tests
./butterfly-e2e/run-golden-path.sh         # Quick golden path
./butterfly-e2e/run-scenarios.sh           # Full scenario suite
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Branch Protection](branch-protection.md) | Branch rules and release strategy |
| [Troubleshooting](troubleshooting.md) | CI failure debugging guide |
| [Common Workflows](../../onboarding/common-workflows.md) | Local development workflows |
| [Portal E2E Debugging](../../../PERCEPTION/docs/runbooks/portal-e2e-debugging.md) | Playwright debugging |

