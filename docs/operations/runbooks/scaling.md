# Scaling Runbook

> Procedures for scaling BUTTERFLY services

**Last Updated**: 2025-12-04  
**Target Audience**: SREs, Platform engineers

---

## Overview

This runbook covers procedures for scaling BUTTERFLY services horizontally and vertically.

---

## Scaling Decision Matrix

### When to Scale

| Indicator | Threshold | Action |
|-----------|-----------|--------|
| CPU usage > 70% | 5 min sustained | Scale out |
| Memory usage > 80% | 10 min sustained | Scale out or up |
| P99 latency > 500ms | 5 min sustained | Scale out |
| Queue depth growing | 5 min sustained | Scale out consumers |
| Connection pool exhausted | Any | Scale out |

### When NOT to Scale

- During active incidents (fix root cause first)
- Without understanding the bottleneck
- When the issue is downstream (database, etc.)

---

## Horizontal Scaling

### Scale Service Replicas

```bash
# Check current replicas
kubectl get deployment/[service] -n butterfly

# Scale up
kubectl scale deployment/[service] --replicas=[N] -n butterfly

# Verify scaling
kubectl get pods -n butterfly -l app=[service]
kubectl rollout status deployment/[service] -n butterfly
```

### Service-Specific Considerations

#### CAPSULE

```bash
# Safe to scale 2-12 replicas (production HPA: min=4, max=12)
kubectl scale deployment/capsule --replicas=5 -n butterfly

# Verify Cassandra connections
kubectl logs -n butterfly -l app=capsule | grep -i "cassandra.*connected"
```

#### ODYSSEY

ODYSSEY consists of multiple components with different scaling characteristics:

**ODYSSEY Core** (main graph service):
```bash
# Safe to scale 2-15 replicas
# Graph queries may have session affinity requirements
kubectl scale deployment/odyssey-core --replicas=5 -n butterfly
```

**HorizonBox** (probabilistic foresight engine):
```bash
# Safe to scale 2-8 replicas
kubectl scale deployment/odyssey-horizonbox --replicas=3 -n butterfly
```

**QuantumRoom** (multi-agent scenario negotiator):
```bash
# Safe to scale 2-8 replicas
kubectl scale deployment/odyssey-quantumroom --replicas=3 -n butterfly
```

#### PERCEPTION

```bash
# Can scale to many replicas for parallel processing
# Consumer group partitions limit parallelism
kubectl scale deployment/perception --replicas=6 -n butterfly

# Check Kafka partition assignment
kubectl exec -it kafka-0 -n butterfly -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group perception-group
```

**Note**: PERCEPTION replicas should not exceed Kafka partition count for the topics it consumes.

#### PLATO

```bash
# Scale based on concurrent plan executions
kubectl scale deployment/plato --replicas=4 -n butterfly
```

#### NEXUS

```bash
# Gateway can scale horizontally without limits
# Ensure load balancer is configured
kubectl scale deployment/nexus --replicas=8 -n butterfly
```

#### SYNAPSE

```bash
# Safe to scale 2-12 replicas
# Handles action execution and safety orchestration
kubectl scale deployment/synapse --replicas=4 -n butterfly

# Check Redis connection for safety state
kubectl logs -n butterfly -l app=synapse | grep -i "redis.*connected"

# Verify Kafka consumer lag for action events
kubectl exec -it kafka-0 -n butterfly -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group synapse-service
```

**Note**: SYNAPSE scaling should consider safety circuit breaker patterns. Monitor `synapse_safety_violations_total` metric during scale operations.

### Update HPA Limits

```bash
# View current HPA
kubectl get hpa -n butterfly

# Update max replicas
kubectl patch hpa/[service]-hpa -n butterfly \
  -p '{"spec":{"maxReplicas": 15}}'

# Verify
kubectl describe hpa/[service]-hpa -n butterfly
```

---

## Vertical Scaling

### Increase Resource Limits

```bash
# Update deployment resources
kubectl patch deployment/[service] -n butterfly -p '
{
  "spec": {
    "template": {
      "spec": {
        "containers": [{
          "name": "[service]",
          "resources": {
            "requests": {
              "cpu": "1",
              "memory": "4Gi"
            },
            "limits": {
              "cpu": "4",
              "memory": "8Gi"
            }
          }
        }]
      }
    }
  }
}'

# This will trigger a rolling restart
kubectl rollout status deployment/[service] -n butterfly
```

### JVM Heap Adjustment

```bash
# Update JAVA_OPTS
kubectl set env deployment/[service] \
  JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC" \
  -n butterfly

# Verify new settings
kubectl logs -n butterfly -l app=[service] | grep -i "heap"
```

---

## Infrastructure Scaling

### Cassandra

```bash
# Add node to cluster
kubectl scale statefulset/cassandra --replicas=4 -n butterfly

# Wait for node to join
kubectl exec -it cassandra-0 -n butterfly -- nodetool status
# Wait until new node shows UN (Up/Normal)

# Run repair after adding node
kubectl exec -it cassandra-0 -n butterfly -- nodetool repair
```

### Kafka

```bash
# Add broker
kubectl scale statefulset/kafka --replicas=4 -n butterfly

# Wait for broker to join
kubectl exec -it kafka-0 -n butterfly -- kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092

# Reassign partitions to new broker
# Create reassignment JSON and execute
kubectl exec -it kafka-0 -n butterfly -- kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --reassignment-json-file /tmp/reassignment.json \
  --execute
```

### PostgreSQL

```bash
# For PostgreSQL, vertical scaling is usually preferred
# Add read replicas for read scaling

# Scale replica count (if using operator)
kubectl patch postgresql/butterfly-postgres -n butterfly \
  -p '{"spec":{"numberOfInstances": 3}}' --type=merge
```

### Redis

```bash
# For Redis Cluster mode
# Add new shard
kubectl scale statefulset/redis --replicas=9 -n butterfly

# Rebalance cluster
kubectl exec -it redis-0 -n butterfly -- redis-cli --cluster rebalance \
  redis-0.redis.butterfly.svc.cluster.local:6379
```

---

## Scale Down Procedures

### Safe Scale Down

```bash
# 1. Check current load
kubectl top pods -n butterfly -l app=[service]

# 2. Scale down gradually
kubectl scale deployment/[service] --replicas=3 -n butterfly
sleep 60
kubectl scale deployment/[service] --replicas=2 -n butterfly

# 3. Monitor for issues
# Watch latency and error rates in Grafana
```

### Cassandra Scale Down

```bash
# 1. Decommission node gracefully
kubectl exec -it cassandra-3 -n butterfly -- nodetool decommission

# 2. Wait for decommission to complete
kubectl exec -it cassandra-0 -n butterfly -- nodetool status

# 3. Scale down statefulset
kubectl scale statefulset/cassandra --replicas=3 -n butterfly
```

### Kafka Scale Down

```bash
# 1. Reassign partitions away from broker to remove
# Create reassignment JSON excluding broker 3
kubectl exec -it kafka-0 -n butterfly -- kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --reassignment-json-file /tmp/remove-broker-3.json \
  --execute

# 2. Wait for reassignment
kubectl exec -it kafka-0 -n butterfly -- kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --reassignment-json-file /tmp/remove-broker-3.json \
  --verify

# 3. Scale down
kubectl scale statefulset/kafka --replicas=3 -n butterfly
```

---

## Capacity Planning Reference

| Service | Min | Recommended | Max | HPA Triggers | Notes |
|---------|-----|-------------|-----|--------------|-------|
| CAPSULE | 2 | 3-4 | 12 | CPU 70%, Memory 80% | Scale with write load |
| ODYSSEY Core | 2 | 3-5 | 15 | CPU 70%, Memory 80% | Scale with graph size |
| ODYSSEY HorizonBox | 2 | 2-3 | 8 | CPU 70%, Memory 80% | Probabilistic engine |
| ODYSSEY QuantumRoom | 2 | 2-3 | 8 | CPU 70%, Memory 80% | Scenario negotiation |
| PERCEPTION | 3 | 3-6 | 15 | CPU 70%, Memory 80% | Limited by Kafka partitions |
| PLATO | 2 | 2-4 | 10 | CPU 70%, Memory 80% | Scale with plan concurrency |
| NEXUS | 3 | 3-5 | 20 | CPU 70%, Memory 80% | Gateway, scale freely |
| SYNAPSE | 2 | 3-4 | 12 | CPU 70%, Memory 80% | Action execution engine |

### HPA Custom Metrics Reference

In addition to CPU/Memory, consider scaling based on these service-specific metrics:

| Service | Custom Metric | Scale-Up Threshold |
|---------|---------------|-------------------|
| CAPSULE | `capsule_query_duration_seconds` P99 | > 500ms for 5 min |
| ODYSSEY | `odyssey_graph_query_duration_seconds` P99 | > 500ms for 5 min |
| PERCEPTION | `kafka_consumer_lag_records` | > 500 messages for 5 min |
| PLATO | `plato_engine_queue_depth` | > 100 pending for 5 min |
| NEXUS | `nexus_request_duration_seconds` P99 | > 200ms for 5 min |
| SYNAPSE | `synapse_execution_queue_depth` | > 50 pending for 5 min |

---

## Verification After Scaling

```bash
# 1. Check pod status
kubectl get pods -n butterfly -l app=[service]

# 2. Check pod distribution
kubectl get pods -n butterfly -o wide | grep [service]

# 3. Verify health
for pod in $(kubectl get pods -n butterfly -l app=[service] -o name); do
  kubectl exec $pod -n butterfly -- curl -s localhost:8080/actuator/health | jq '.status'
done

# 4. Check metrics
# Verify in Grafana that load is distributed

# 5. Monitor for 15 minutes
# Watch error rates and latency
```

---

## Troubleshooting

### Pods Not Scheduling

```bash
# Check events
kubectl describe pod -n butterfly -l app=[service] | grep -A10 Events

# Check node resources
kubectl describe nodes | grep -A10 "Allocated resources"

# Check PDB
kubectl get pdb -n butterfly
```

### Uneven Load Distribution

```bash
# Check service endpoints
kubectl get endpoints -n butterfly [service]

# Check if load balancer is configured correctly
kubectl describe svc -n butterfly [service]
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Capacity Planning](../capacity-planning.md) | Sizing guidelines |
| [Monitoring](../monitoring/README.md) | Metrics and dashboards |
| [Common Issues](common-issues.md) | Troubleshooting |

