# butterfly-e2e Harness

Developer- and CI-friendly harness that wires PERCEPTION → CAPSULE → ODYSSEY along the HFT/reflex path. It now ships a fast-path publishing CLI, a scenario catalog (positive + negative), and optional full-stack docker-compose profiles.

## What this gives you
- Single `docker compose` for Kafka plus a stub fast-path emitter (disabled when you seed via CLI).
- `scripts/fastpath-cli.sh` for generating or publishing RimFastEvent payloads from JSON files or command-line arguments.
- Scenario catalog under `butterfly-e2e/scenarios/` plus `run-scenarios.sh` to replay positive/negative cases and run ODYSSEY reflex assertions.
- Optional profiles in `docker-compose.full.yml` to boot ODYSSEY (and a capsule mock) inside the same network.
- CI workflow `.github/workflows/butterfly-e2e.yml` that exercises the scenario suite on PRs and nightly.

## Quick start
```bash
# bring up Kafka only (skip stub producer) and enable odyssey profile if needed
FASTPATH_SKIP_STUB=1 ./scripts/dev-up.sh           # add FASTPATH_FULL_STACK=1 --profile odyssey to boot ODYSSEY

# publish a canned scenario
./scripts/fastpath-cli.sh publish --scenario golden-path --topic rim.fast-path --bootstrap localhost:9092

# generate a minimal payload and send it
./scripts/fastpath-cli.sh generate --node rim:entity:finance:EURUSD --stress 0.97 --send

# exercise the catalog (golden + negative + ODYSSEY reflex assertions)
./butterfly-e2e/run-scenarios.sh

# tear down
./scripts/dev-down.sh
```

## Services
- `kafka` / `zookeeper`: minimal messaging backbone for rim.fast-path.
- `fastpath-producer`: publishes `scenarios/fastpath-event.json` to `${FASTPATH_TOPIC:-rim.fast-path}` after startup (skip with `FASTPATH_SKIP_STUB=1`).
- `odyssey-app` (profile `odyssey`, compose file `docker-compose.full.yml`): runs ODYSSEY via Maven inside a container for smoke tests.
- `capsule-mock` (profile `capsule`): placeholder TCP sink for failure testing.
- `kafka-inspector` (profile `tools`): opt-in tail of the topic for debugging (`FASTPATH_TOPIC` overridable).

## Scenario catalog
- `scenarios/catalog.json` declares positive, edge, and negative cases (`golden-path`, `edge-high-stress`, `minimal-ok`, `invalid-node`, `kafka-hiccup`).
- `run-scenarios.sh` replays the catalog with `fastpath-cli`, injects a Kafka outage for the hiccup case, and runs both `HftGoldenPathIntegrationTest` and `HftNegativePathIntegrationTest` by default.
- Disable stack management with `START_DEV_STACK=0` when pointing at an existing Kafka cluster. Set `RUN_REFLEX_ASSERTIONS=0` to skip Maven tests.

## CLI usage
- List scenarios: `./scripts/fastpath-cli.sh scenarios`
- Publish from file: `./scripts/fastpath-cli.sh publish --file butterfly-e2e/scenarios/fastpath-event.json`
- Publish a catalog entry: `./scripts/fastpath-cli.sh publish --scenario edge-high-stress --topic rim.fast-path`
- Generate payload: `./scripts/fastpath-cli.sh generate --node rim:entity:finance:SP500 --stress 0.99 --price 485.25 --volume 2100000 --send`
- Send raw/malformed JSON to test mapper resilience: add `--raw`.

## Expected flow
1. PERCEPTION stub or `fastpath-cli` publishes a RimFastEvent-shaped message to `rim.fast-path`.
2. ODYSSEY consumes, normalizes to `RimNodeId`, and executes reflex rules.
3. Reflex emits `ReflexActionEvent` and invokes the predictive writer (CAPSULE path).

## CI / Nightly
- `scenario-suite` job runs on PRs touching contracts/reflex files and nightly cron. It builds the CLI, replays the scenario catalog (including the Kafka hiccup), and executes ODYSSEY integration tests.
- Optional `full-stack-smoke` job (workflow dispatch input) boots Kafka + ODYSSEY via docker compose and seeds a golden-path event.

## Troubleshooting
- If Maven cannot find butterfly-common, run `mvn -f butterfly-common/pom.xml install -DskipTests`.
- Use `docker compose -f butterfly-e2e/docker-compose.yml logs fastpath-producer` to confirm the stub payload was published.
- Add `--profile tools` when bringing up the stack to tail the topic with `kcat`.
- `RUN_REFLEX_ASSERTIONS=1` ensures ODYSSEY reflex tests are executed; set to 0 to iterate quickly when you only need publish checks.

---

## Phase 1 Integration Completion Criteria

This section defines the acceptance criteria for Phase 1 of the BUTTERFLY integration. All criteria must be met before Phase 1 can be considered complete.

### Scenario Coverage

| Category | Scenarios | Status |
|----------|-----------|--------|
| **Golden Path** | `golden-path`, `strategic-eurusd-shock` | Required |
| **Edge Cases** | `edge-high-stress`, `minimal-ok`, `invalid-node` | Required |
| **Failure Injection** | `kafka-hiccup`, `nexus-capsule-timeout`, `nexus-odyssey-circuit-open`, `nexus-perception-stale` | Required |
| **Temporal Navigation** | `temporal-present-to-past`, `temporal-past-to-future`, `temporal-future-to-governance` | Required |
| **NEXUS Core** | `nexus-temporal-query`, `nexus-cross-inference`, `nexus-synthesis-cascade`, `nexus-learning-loop`, `nexus-contradiction-detection` | Required |

**Minimum Passing Scenarios:** 17 of 17 scenarios must pass.

### Service Level Objectives (SLOs)

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Temporal Slice Latency (P50)** | < 50ms | `nexus_temporal_slice_query_duration{quantile="0.50"}` |
| **Temporal Slice Latency (P95)** | < 100ms | `nexus_temporal_slice_query_duration{quantile="0.95"}` |
| **Temporal Slice Latency (P99)** | < 200ms | `nexus_temporal_slice_query_duration{quantile="0.99"}` |
| **Cross-System Coherence Score** | > 0.7 | Average `nexus_temporal_coherence_score` over test data |
| **Temporal Slice Error Rate** | < 0.1% | `rate(nexus_temporal_slice_errors_total[5m])` |

### Resilience Criteria

| Criterion | Description | Validation |
|-----------|-------------|------------|
| **Zero DLQ Messages (Golden Path)** | No messages sent to DLQ during golden path execution | Check DLQ topics are empty post-test |
| **Circuit Breakers Stable** | Circuit breakers never open during normal operation | `resilience4j_circuitbreaker_state == CLOSED` |
| **Graceful Degradation** | Partial failures return degraded responses, not errors | `nexus-capsule-timeout` scenario passes |
| **Fallback Activation** | Cached data served when upstream unavailable | `nexus-odyssey-circuit-open` scenario passes |
| **Trust Score Adjustment** | Stale data triggers trust score reduction | `nexus-perception-stale` scenario passes |

### Data Quality Criteria

| Criterion | Description | Validation |
|-----------|-------------|------------|
| **Historical Data Coverage** | CAPSULE returns valid historical data for test entities | 30-day history available in test harness |
| **Current State Freshness** | PERCEPTION returns data < 60s old (unless testing staleness) | `X-Data-Timestamp` header validation |
| **Future Projection Availability** | ODYSSEY returns at least 3 paths per entity | `temporal-past-to-future` scenario assertions |

### Integration Contract Criteria

| Criterion | Description | Validation |
|-----------|-------------|------------|
| **Avro Schema Compatibility** | All schemas pass backward compatibility checks | CI `avro-schema-compatibility` job passes |
| **Kafka Topic Documentation** | All core topics documented in registry | `docs/integration/kafka-topic-registry.md` complete |
| **RimNodeId Consistency** | All services use `butterfly-common` RimNodeId | Cross-service calls use `rim:{type}:{namespace}:{id}` |
| **Trace Propagation** | Correlation IDs flow across all service boundaries | `X-Correlation-ID` header present in all responses |

### Governance Flow Criteria

| Criterion | Description | Validation |
|-----------|-------------|------------|
| **Synthesis to Governance** | NEXUS strategic options trigger PLATO evaluation | `temporal-future-to-governance` scenario passes |
| **Decision Audit Trail** | All governance decisions have complete audit trails | Audit endpoint returns full decision context |
| **Reasoning Transparency** | Governance decisions include reasoning | `has_reasoning == true` in decision responses |

### Running Phase 1 Validation

```bash
# Full Phase 1 validation suite
./butterfly-e2e/run-phase1-validation.sh

# Individual validations
./butterfly-e2e/run-scenarios.sh --category golden-path
./butterfly-e2e/run-scenarios.sh --category temporal-navigation
./butterfly-e2e/run-scenarios.sh --category failure-injection
./butterfly-e2e/validate-slos.sh

# Check completion criteria
./butterfly-e2e/check-phase1-criteria.sh
```

### Phase 1 Sign-Off Checklist

Before declaring Phase 1 complete, verify:

- [ ] All 17 E2E scenarios pass consistently (3 consecutive runs)
- [ ] Temporal slice latency P95 < 100ms over 24-hour period
- [ ] Cross-system coherence score > 0.7 for all test entities
- [ ] Zero DLQ messages during 24-hour golden path soak test
- [ ] All circuit breakers remain CLOSED during normal operation
- [ ] Grafana dashboard `NEXUS Temporal Intelligence SLOs` shows green
- [ ] Prometheus alerts `nexus-temporal-slo-alerts` have zero firings
- [ ] Schema compatibility CI job passes for all Avro schemas
- [ ] Kafka topic registry documentation is complete and accurate
- [ ] Integration team sign-off obtained

### Post-Phase 1

Upon successful completion of Phase 1:

1. **Tag Release**: Create `v1.0.0-integration-complete` tag
2. **Freeze Contracts**: Lock v1 Avro schemas, any changes require compatibility review
3. **Enable Monitoring**: Activate production alerting for SLO breaches
4. **Document Lessons**: Update `docs/integration/phase1-retrospective.md`
5. **Plan Phase 2**: Focus on performance optimization, additional domains, and horizontal scaling
