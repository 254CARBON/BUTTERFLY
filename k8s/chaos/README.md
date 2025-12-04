# BUTTERFLY Chaos Engineering

This directory contains Chaos Mesh experiments for testing ecosystem-level resilience.

## Prerequisites

1. **Chaos Mesh** installed in the cluster:
   ```bash
   helm repo add chaos-mesh https://charts.chaos-mesh.org
   helm install chaos-mesh chaos-mesh/chaos-mesh -n chaos-mesh --create-namespace
   ```

2. **BUTTERFLY platform** deployed in the `butterfly` namespace

## Experiment Categories

### 1. Service Failure Scenarios
- `odyssey-down.yaml` - Complete ODYSSEY service failure
- `capsule-degraded.yaml` - CAPSULE partial degradation (50% pod kill)
- `perception-delay.yaml` - PERCEPTION network latency injection

### 2. Database Failure Scenarios
- `cassandra-failure.yaml` - Cassandra node failure
- `postgres-disconnect.yaml` - PostgreSQL connection issues

### 3. Network Partition Scenarios
- `network-partition.yaml` - Network partition between services
- `kafka-disconnect.yaml` - Kafka broker isolation

## Running Experiments

### Single Experiment
```bash
kubectl apply -f odyssey-down.yaml
```

### Watch Results
```bash
kubectl get podchaos,networkchaos,stresschaos -n butterfly -w
```

### Cleanup
```bash
kubectl delete -f odyssey-down.yaml
```

### Run All Experiments (Staging Only)
```bash
./run-all-experiments.sh staging
```

## Expected Behaviors

| Scenario | Expected Behavior |
|----------|-------------------|
| ODYSSEY down | NEXUS returns cached/fallback data; PLATO queues governance requests |
| CAPSULE degraded | Services use stale cache; read replicas take over |
| PERCEPTION delay | Event processing degrades gracefully; backpressure applied |

## Safety Guidelines

1. **Never run in production** without explicit approval
2. **Always have rollback ready** - `kubectl delete -f <experiment>`
3. **Monitor dashboards** during experiments
4. **Limit blast radius** - start with single pod, expand gradually
5. **Time-bound** - all experiments have automatic duration limits

