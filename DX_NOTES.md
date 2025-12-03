# DX Notes

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
