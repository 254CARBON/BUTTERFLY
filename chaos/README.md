# Chaos Engineering for BUTTERFLY

> Chaos Mesh experiments for validating platform resilience

**Last Updated**: 2025-12-04  
**Target Audience**: SREs, Platform engineers

---

## Overview

This directory contains Chaos Mesh configurations and experiments for testing BUTTERFLY platform resilience. All experiments are designed to validate recovery targets defined in the [Failure-Mode Catalog](../docs/operations/failure-modes.md).

## Directory Structure

```
chaos/
├── README.md                    # This file
├── helm/
│   └── chaos-mesh-values.yaml   # Chaos Mesh installation values
├── experiments/
│   ├── kafka-broker-loss.yaml   # Kafka single broker failure
│   ├── cassandra-node-failure.yaml
│   ├── postgres-primary-failure.yaml
│   ├── redis-failover.yaml
│   ├── network-partition.yaml
│   ├── pod-cpu-stress.yaml
│   ├── ignite-partition.yaml
│   ├── capsule-outage.yaml      # CAPSULE pod kill + DLQ verification
│   ├── nexus-partial-degradation.yaml  # NEXUS CPU/latency stress
│   └── plato-latency-spike.yaml # PLATO governance API latency
└── scripts/
    └── run-chaos-suite.sh       # Automated chaos runner
```

## Prerequisites

1. Kubernetes cluster with admin access
2. Helm 3.x installed
3. kubectl configured for target cluster
4. Prometheus/Grafana for observability

## Installation

### Install Chaos Mesh

```bash
# Add Chaos Mesh Helm repository
helm repo add chaos-mesh https://charts.chaos-mesh.org
helm repo update

# Install in staging environment
helm install chaos-mesh chaos-mesh/chaos-mesh \
  --namespace chaos-mesh \
  --create-namespace \
  -f helm/chaos-mesh-values.yaml

# Verify installation
kubectl get pods -n chaos-mesh
```

### Configure RBAC

Chaos Mesh requires permissions to inject faults:

```bash
# Apply RBAC for butterfly namespace
kubectl apply -f helm/chaos-mesh-rbac.yaml
```

## Running Experiments

### Manual Execution

```bash
# Run single experiment
kubectl apply -f experiments/kafka-broker-loss.yaml

# Check experiment status
kubectl get podchaos -n butterfly

# Delete experiment (stops chaos)
kubectl delete -f experiments/kafka-broker-loss.yaml
```

### Automated Suite

```bash
# Run full chaos suite with pass/fail reporting
./scripts/run-chaos-suite.sh --env staging --report

# Run specific experiment category
./scripts/run-chaos-suite.sh --env staging --category kafka

# Dry-run mode (validate YAML only)
./scripts/run-chaos-suite.sh --env staging --dry-run
```

## Experiment Categories

| Category | Experiments | Frequency |
|----------|-------------|-----------|
| Kafka | broker-loss, cluster-failure | Weekly |
| Database | cassandra-node, postgres-primary | Weekly |
| Cache | redis-failover, ignite-partition | Bi-weekly |
| Network | cross-service partition | Weekly |
| Pods | cpu-stress, memory-pressure, pod-kill | Monthly |
| Services | capsule-outage, nexus-degradation, plato-latency | Twice weekly |

## Pass/Fail Criteria

Each experiment has defined success criteria:

| Experiment | Pass Criteria |
|------------|---------------|
| kafka-broker-loss | Consumer rebalance < 30s, no message loss |
| cassandra-node-failure | Read/write continuity maintained |
| postgres-primary-failure | Failover completes < 60s |
| redis-failover | Sentinel promotes replica < 10s |
| network-partition | Circuit breaker trips < 30s |
| pod-cpu-stress | HPA scales out < 60s |
| capsule-outage | DLQ capture successful, NEXUS fallback < 60s, recovery < 60s |
| nexus-partial-degradation | PLATO/SYNAPSE continue without errors, HPA scales < 2 min |
| plato-latency-spike | SYNAPSE buffering active, client retries succeed, no cascade |

## Safety Controls

### Namespace Isolation

Experiments are scoped to `butterfly` namespace only:

```yaml
selector:
  namespaces:
    - butterfly
```

### Duration Limits

All experiments have maximum duration:

```yaml
spec:
  duration: "60s"  # Auto-terminates
```

### Annotation Protection

Critical pods can be protected:

```yaml
metadata:
  annotations:
    chaos-mesh.org/inject: "false"
```

## Monitoring During Chaos

### Grafana Dashboards

- **Chaos Mesh Dashboard**: Experiment status and injection events
- **Service Health**: Error rates and latencies during chaos
- **Recovery Metrics**: Time-to-recovery measurements

### Key Metrics

```promql
# Active chaos experiments
chaos_mesh_experiments_active

# Service error rate during chaos
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))

# Circuit breaker state changes
resilience4j_circuitbreaker_state
```

## Scheduling

### Staging Environment

- **Frequency**: Weekly (Tuesday 2 AM UTC)
- **Scope**: Full suite rotation
- **Notifications**: #butterfly-chaos Slack channel

### Canary Environment

- **Frequency**: Monthly (First Saturday 3 AM UTC)
- **Scope**: P1/P2 experiments only
- **Notifications**: PagerDuty (informational)

## Troubleshooting

### Experiment Not Injecting

```bash
# Check Chaos Mesh controller logs
kubectl logs -n chaos-mesh -l app.kubernetes.io/component=controller-manager

# Verify target pods exist
kubectl get pods -n butterfly -l app=kafka
```

### Experiment Stuck

```bash
# Force delete experiment
kubectl delete podchaos <name> -n butterfly --force --grace-period=0

# Check for finalizer issues
kubectl get podchaos <name> -n butterfly -o yaml | grep finalizers
```

### Recovery Not Happening

```bash
# Check if experiment is still active
kubectl get chaos -A

# Verify chaos-daemon is running on target nodes
kubectl get pods -n chaos-mesh -l app.kubernetes.io/component=chaos-daemon
```

## CI Integration

The Resilience CI Stage integrates chaos experiments into the CI pipeline:

- **Workflow**: `.github/workflows/resilience-ci.yml`
- **Triggers**: PRs to core services, Tuesday/Friday 3 AM UTC, manual dispatch
- **Orchestrator**: `butterfly-e2e/resilience-ci-stage.sh`

### Automated Post-Chaos Validation

After each chaos experiment:
1. **DLQ Replay**: Automatically replays messages from DLQ
2. **Scaling Validation**: Verifies HPA reactions using PERCEPTION runbook metrics
3. **Report Generation**: Produces JUnit XML for CI gating

### Usage in CI

```bash
# Run from CI with all experiments
./butterfly-e2e/resilience-ci-stage.sh --experiments all --env staging

# Skip chaos (local testing without k8s)
./butterfly-e2e/resilience-ci-stage.sh --skip-chaos

# Run specific experiments
./butterfly-e2e/resilience-ci-stage.sh --experiments capsule,nexus
```

## Related Documentation

| Document | Description |
|----------|-------------|
| [Failure-Mode Catalog](../docs/operations/failure-modes.md) | Failure modes and targets |
| [Disaster Recovery](../docs/operations/runbooks/disaster-recovery.md) | Recovery procedures |
| [PERCEPTION Scaling Runbook](../PERCEPTION/docs/runbooks/scaling.md) | Scaling indicators and metrics |
| [DLQ Replay Tool](../PERCEPTION/ingestion-dlq-replay/README.md) | DLQ replay documentation |
| [Chaos Mesh Docs](https://chaos-mesh.org/docs/) | Official documentation |

