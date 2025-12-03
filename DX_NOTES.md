# DX Notes

> Developer experience notes and observations for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03

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

## Future DX Improvements

Based on Sprint 5 alignment work, these improvements are recommended for future sprints:

- **Auto-generate API docs from CI**: Use OpenAPI spec generation in CI pipelines
- **Template scaffolding CLI**: Create a CLI tool to scaffold new modules using templates
- **Chaos test automation**: Add more automation around chaos tests with clearer reporting
- **ADR for centralized patterns**: Create an ADR documenting the decision to centralize contributing and testing patterns
- **IDE snippets/templates**: Add IntelliJ and VS Code snippets for common patterns

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [DEVELOPMENT_OVERVIEW.md](DEVELOPMENT_OVERVIEW.md) | Quick start guide |
| [docs/development/contributing.md](docs/development/contributing.md) | Canonical contributing guide |
| [docs/development/testing-strategy.md](docs/development/testing-strategy.md) | Per-service test matrices |
| [docs/onboarding/](docs/onboarding/) | New contributor onboarding |
