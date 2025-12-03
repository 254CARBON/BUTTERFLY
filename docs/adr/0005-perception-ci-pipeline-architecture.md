# ADR-0005: PERCEPTION CI Pipeline Architecture

## Status

Accepted

## Date

2025-12-03

## Context

The PERCEPTION module is the largest codebase in the BUTTERFLY ecosystem, containing:
- 20+ backend Java modules (acquisition, intelligence, signals, API, etc.)
- A Next.js frontend portal with TypeScript
- Integration with multiple external systems (Kafka, PostgreSQL, Redis)
- Chaos engineering test infrastructure

Previously, PERCEPTION lacked a dedicated CI pipeline, relying on ad-hoc testing and the general security scan workflow. This created several problems:
- No automated validation of pull requests
- Integration tests and chaos tests were not run automatically
- Frontend code quality was not enforced
- No coverage tracking or thresholds

## Decision

We implement a comprehensive GitHub Actions CI pipeline for PERCEPTION with the following architecture:

### Pipeline Structure

**Backend Jobs:**
1. `build` - Maven compile + unit tests (excludes chaos/integration)
2. `integration-tests` - TestContainers-based integration tests with real services
3. `chaos-tests` - Resilience validation tests (circuit breakers, recovery)
4. `coverage` - JaCoCo aggregate coverage with 70% threshold

**Frontend Jobs:**
1. `portal-lint` - ESLint validation
2. `portal-test` - Vitest unit tests
3. `portal-e2e` - Playwright end-to-end tests
4. `portal-build` - Production build validation

### Parallelization Strategy

- `build`, `portal-lint`, and `portal-test` run in parallel (no dependencies)
- `integration-tests`, `chaos-tests`, and `coverage` depend on `build`
- `portal-e2e` and `portal-build` depend on `portal-lint` and `portal-test`
- Summary job aggregates results from all jobs

### Path Filtering

Pipeline triggers only on:
- Changes to `PERCEPTION/**`
- Changes to `butterfly-common/**` (shared contracts)
- Changes to the workflow file itself

## Consequences

### Positive

- Pull requests are validated automatically before merge
- Integration issues are caught early with TestContainers tests
- Resilience patterns are validated via chaos tests
- Frontend code quality is enforced consistently
- Coverage metrics provide visibility into test gaps
- Parallel execution minimizes CI wait time

### Negative

- CI runs take longer due to comprehensive test suite (~15-20 minutes)
- Integration tests require service containers (Postgres, Redis)
- E2E tests require Playwright browser installation
- Additional GitHub Actions minutes consumption

### Neutral

- Developers must ensure chaos tests pass before merging
- Coverage threshold may require test additions for new code
- E2E test failures require local debugging with Playwright tools

## Alternatives Considered

### Alternative 1: Single Monolithic Job

Run all tests in a single job sequentially.

**Not chosen because:**
- Much longer total execution time
- No parallelization benefits
- Single failure blocks all results

### Alternative 2: Matrix Strategy for Backend Modules

Use a matrix to test each backend module independently.

**Not chosen because:**
- PERCEPTION modules are tightly coupled
- Would require complex dependency management
- Integration tests need the full build anyway

### Alternative 3: Separate Frontend Pipeline

Create a completely separate workflow for the portal.

**Not chosen because:**
- Adds maintenance overhead
- Harder to correlate backend/frontend changes
- Path filtering achieves similar isolation

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [PERCEPTION CONTRIBUTING.md](../../PERCEPTION/CONTRIBUTING.md) - Test matrix requirements
- [Playwright CI Configuration](https://playwright.dev/docs/ci)
- [JaCoCo Maven Plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)

