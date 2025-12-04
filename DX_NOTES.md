# DX Notes

> Developer experience notes and observations for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-04

---

## Sprint 5: Developer Experience Alignment

The following improvements have been made to standardize developer experience across the ecosystem:

### Contributing Alignment

- **Canonical contributing guide**: [docs/development/contributing.md](docs/development/contributing.md) is now the single source of truth for branch naming, conventional commits, and PR process
- **Service CONTRIBUTING files** for all major services (CAPSULE, ODYSSEY, PERCEPTION, PLATO, NEXUS, butterfly-common) now reference the canonical guide and add service-specific details
- **Consistent branch prefixes**: `feature/`, `bugfix/`, `hotfix/`, `refactor/`, `docs/`, `perf/`, `chore/`
- **Commit types enforced via commitlint**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`

### Testing & CI Standardization

- **Per-service test matrix** documented in [docs/development/testing-strategy.md](docs/development/testing-strategy.md)
- **Green-PR criteria** documented in [docs/development/ci-cd.md](docs/development/ci-cd.md)
- **Consistent coverage targets**: ≥80% for services, ≥90% for butterfly-common

### Documentation Templates

- **Centralized templates** in [docs/templates/](docs/templates/):
  - `module-readme.md` - For service modules/packages
  - `runbook.md` - Operational runbooks
  - `adr.md` - Architecture Decision Records
  - `api-guide.md` - API usage guides
  - `release-notes.md` - Release announcements

---

## Golden Path Observations

Observations while wiring the PERCEPTION → CAPSULE → ODYSSEY golden path.

- Startup order matters: bring Kafka up first (`FASTPATH_SKIP_STUB=1 ./scripts/dev-up.sh`), then ODYSSEY with `hft.kafka.bootstrap-servers` pointing to it. CAPSULE and PERCEPTION can be layered in afterwards if you need full persistence.
- Missing contract JARs: ODYSSEY now depends on `butterfly-common` (HFT contracts). Run `mvn -f butterfly-common/pom.xml install -DskipTests` once per dev machine or cache it in CI.
- Sample data: `butterfly-e2e/scenarios/fastpath-event.json` seeds a full RimFastEvent for testing without a live PERCEPTION instance.
- Kafka topic visibility: use the `kafka-inspector` profile in `butterfly-e2e/docker-compose.yml` to tail `rim.fast-path` when debugging.
- CI parity: `.github/workflows/butterfly-e2e.yml` runs the reflex golden-path test nightly; mirror that locally with `butterfly-e2e/run-golden-path.sh` before PRs.
- Fast-path CLI: `scripts/fastpath-cli.sh` publishes scenarios (`--scenario edge-high-stress`) or generates payloads (`generate --node rim:entity:finance:EURUSD --stress 0.97 --send`). Add `--raw` to send malformed JSON for mapper testing.
- Scenario harness: `butterfly-e2e/run-scenarios.sh` replays the catalog (including a Kafka hiccup) and runs ODYSSEY reflex assertions; disable auto-compose with `START_DEV_STACK=0` for remote clusters.
- Full-stack smoke: set `FASTPATH_FULL_STACK=1` and pass `--profile odyssey` to `./scripts/dev-up.sh` to run ODYSSEY in Docker (compose file `butterfly-e2e/docker-compose.full.yml`).

Follow-up ideas (not implemented yet):
- Add schema-registry backed Avro serialization once producers move off JSON.
- Pre-populated JanusGraph/Redis snapshots to cut ODYSSEY cold-start time when running the full stack.

---

## Phase 0: Alignment & Contract Freeze

Implemented infrastructure for contract stability and documentation consistency:

### Documentation Linting

- **Semantic linter**: `scripts/lint-docs.py` validates bidirectional cross-references between docs/index.md and service READMEs
- **Link validation**: `.github/workflows/docs-lint.yml` runs markdown-link-check on all documentation
- **CI enforcement**: Documentation drift is flagged automatically in PRs

### Contract Freeze Enforcement

- **Approval gate**: `contracts-guardrails.yml` now requires `contract-change-approved` label for any schema changes
- **Freeze policy**: [docs/contracts/FREEZE_POLICY.md](docs/contracts/FREEZE_POLICY.md) documents the approval process
- **Labels created**: `dx`, `epic`, `phase-0`, `contract-change-approved`

### Module Bootstrap Automation

- **Enhanced setup.sh**: New flags `--module`, `--full`, `--status`, `--list`, `--stop`, `--stop-all`
- **NEXUS compose**: `butterfly-nexus/docker-compose.dev.yml` for standardized dev setup
- **Health checks**: Module status shows health of running services

### DX Backlog (GitHub Issues Created)

- [#1 - API Documentation Generation](https://github.com/254CARBON/BUTTERFLY/issues/1)
- [#2 - Template Scaffolding CLI](https://github.com/254CARBON/BUTTERFLY/issues/2)
- [#3 - IDE Snippets and Live Templates](https://github.com/254CARBON/BUTTERFLY/issues/3)

---

## Future DX Improvements

Based on Sprint 5 alignment work, these improvements are tracked as GitHub issues:

- **Auto-generate API docs from CI**: [Issue #1](https://github.com/254CARBON/BUTTERFLY/issues/1) - OpenAPI spec generation in CI pipelines
- **Template scaffolding CLI**: [Issue #2](https://github.com/254CARBON/BUTTERFLY/issues/2) - CLI tool to scaffold new modules using templates
- **IDE snippets/templates**: [Issue #3](https://github.com/254CARBON/BUTTERFLY/issues/3) - IntelliJ and VS Code snippets for common patterns
- **Chaos test automation**: Add more automation around chaos tests with clearer reporting
- **ADR for centralized patterns**: Create an ADR documenting the decision to centralize contributing and testing patterns

---

## Workstream 9: Governance Loop + Chaos Harness

### Governance + Decision Loop Scenario

- Added `butterfly-e2e/scenarios/governance-decision-loop.json` plus `scripts/run-governance-loop.sh` so contributors can launch PERCEPTION → CAPSULE → ODYSSEY → NEXUS → PLATO → SYNAPSE with a single command.
- The helper performs health checks, creates policies/specs/plans, executes them via SYNAPSE, and posts learning signals back into NEXUS; it surfaces the correlation id for quick log-chasing.
- **Quick usage:** `./scripts/run-governance-loop.sh --keep-stack` (full validation) or `./scripts/run-governance-loop.sh --skip-stack --allow-partial` (useful inside CI or when you only want NEXUS/ODYSSEY running).
- **Troubleshooting tips:**
  - If a health check fails, run the service-specific dev script (`./PLATO/scripts/dev-up.sh`, `mvn -f PERCEPTION/pom.xml spring-boot:run`, `./SYNAPSE/scripts/dev-up.sh`).
  - Override endpoints via `PERCEPTION_URL=http://localhost:8181 ./scripts/run-governance-loop.sh` if you expose services on non-standard ports.
  - Install `jq` + `curl` locally (`brew install jq curl` or `apt-get install jq curl`) before running; the helper will exit early if either is missing.
  - Use the emitted correlation id with `rg -n "<id>" -g '*.log'` across service logs when tracing failures.
- Nightly CI now invokes the helper in partial mode so full-loop coverage runs daily without slowing PR builds; the detailed governance drill remains an opt-in local workflow for contributors.

### Chaos Coverage

- New experiments target the resilience scenarios called out in the workstream:
  - `chaos/experiments/capsule-outage.yaml` / `k8s/chaos/experiments/capsule-outage.yaml`: hard-stops CAPSULE pods, verifies DLQ capture and NEXUS temporal fallbacks.
  - `chaos/experiments/nexus-partial-degradation.yaml` / `k8s/chaos/experiments/nexus-partial-degradation.yaml`: CPU + latency stress on NEXUS while PLATO/SYNAPSE keep pace.
  - `chaos/experiments/plato-latency-spike.yaml` / `k8s/chaos/experiments/plato-latency-spike.yaml`: PLATO governance APIs slowed to ensure SYNAPSE buffering + CAPSULE continuity.
- Each YAML ships with success/failure ConfigMaps and links to the relevant remediation runbooks so platform engineers can tie failures back to docs quickly.
- The chaos runner (`butterfly-e2e/run-chaos-scenarios.sh`) automatically sweeps the new definitions because they are categorized as `chaos`, and the nightly GitHub Action already executes the suite instead of every PR.

### Resilience CI Stage

A comprehensive resilience validation pipeline has been implemented:

- **Orchestration**: `butterfly-e2e/resilience-ci-stage.sh` coordinates all resilience tests
- **Automated DLQ Replay**: `butterfly-e2e/scripts/auto-dlq-replay.sh` triggers DLQ replay post-chaos
- **Scaling Validation**: `butterfly-e2e/scripts/validate-scaling-reactions.sh` validates scaling reactions using PERCEPTION runbook metrics
- **Report Generation**: `butterfly-e2e/scripts/generate-resilience-report.py` produces JUnit XML and Markdown reports

**CI Integration:**
- Dedicated workflow: `.github/workflows/resilience-ci.yml`
- Triggers: PRs to core services, Tuesday/Friday at 3 AM UTC, manual dispatch
- Configurable chaos experiments (capsule, nexus, plato, or all)

**SLO Targets:**
- Chaos recovery time: < 60s
- DLQ replay success rate: ≥ 93%
- Message loss rate: < 1%
- Scaling reaction time: < 2 min

**Usage:**
```bash
# Full resilience suite (requires Kubernetes cluster with Chaos Mesh)
./butterfly-e2e/resilience-ci-stage.sh --experiments all

# Skip chaos (for local testing without k8s)
./butterfly-e2e/resilience-ci-stage.sh --skip-chaos

# Dry run (validate config without execution)
./butterfly-e2e/resilience-ci-stage.sh --dry-run
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [DEVELOPMENT_OVERVIEW.md](DEVELOPMENT_OVERVIEW.md) | Quick start guide |
| [docs/development/contributing.md](docs/development/contributing.md) | Canonical contributing guide |
| [docs/development/testing-strategy.md](docs/development/testing-strategy.md) | Per-service test matrices |
| [docs/contracts/FREEZE_POLICY.md](docs/contracts/FREEZE_POLICY.md) | Contract freeze policy and approval process |
| [docs/onboarding/](docs/onboarding/) | New contributor onboarding |
| [.github/dx-issues/](.github/dx-issues/) | DX backlog issue templates |
