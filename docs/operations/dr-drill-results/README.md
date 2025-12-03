# DR Drill Results

> This directory contains disaster recovery drill results captured by the DR Drill Framework.

## Directory Contents

| File | Description |
|------|-------------|
| `latest.json` | Most recent drill result |
| `dr-drill-YYYYMMDD-HHMMSS.json` | Individual drill results by timestamp |

## Drill Result Format

Each result file contains:

```json
{
    "drill_id": "dr-drill-20251203-143000",
    "timestamp": "2025-12-03T14:30:00Z",
    "environment": "staging",
    "namespace": "butterfly",
    "scenario": "cassandra-restore",
    "rto_target_seconds": 1800,
    "recovery_time_seconds": 1250,
    "passed": true,
    "dry_run": false,
    "executed_by": "sre-engineer",
    "hostname": "runner-001"
}
```

## RTO Targets

| Scenario | RTO Target | Notes |
|----------|------------|-------|
| `cassandra-restore` | 30 minutes | Full restore from snapshot |
| `postgres-failover` | 1 minute | Automatic replica promotion |
| `kafka-recovery` | 5 minutes | Broker replacement + rebalance |
| `full-platform-restore` | 1 hour | All components from backup |
| `region-failover` | 1 hour | DR region activation |

## Viewing Results

```bash
# View latest result
cat latest.json | jq

# List all drills
ls -la dr-drill-*.json

# Find failed drills
grep '"passed": false' dr-drill-*.json

# Calculate average recovery time for scenario
jq -s '[.[] | select(.scenario=="cassandra-restore") | .recovery_time_seconds] | add / length' dr-drill-*.json
```

## Running Drills

See the [DR Drill Framework](../../../scripts/dr/drill-framework.sh) for execution instructions:

```bash
# Run a drill with timing capture
./scripts/dr/drill-framework.sh \
  --environment staging \
  --scenario cassandra-restore \
  --record-timing
```

## Related Documentation

- [Disaster Recovery Runbook](../runbooks/disaster-recovery.md)
- [Failure-Mode Catalog](../failure-modes.md)
- [Chaos Experiments](../../../chaos/README.md)

