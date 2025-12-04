# Runbook: Database Partial Outage / Cross-Service Network Partition

## Overview

This runbook provides remediation steps for scenarios where database services (Cassandra, PostgreSQL, Redis) experience partial outages, or when network partitions occur between BUTTERFLY services.

## Related Chaos Experiments

- **File**: `chaos/experiments/capsule-odyssey-partition.yaml`
- **Purpose**: Validates CAPSULE-ODYSSEY partition recovery
- **File**: `chaos/experiments/cassandra-node-failure.yaml`
- **File**: `chaos/experiments/postgres-primary-failure.yaml`
- **File**: `chaos/experiments/redis-failover.yaml`

## Detection

### Alerts

- `CassandraNodeDown` - Cassandra node unreachable
- `PostgresPrimaryUnavailable` - PostgreSQL primary not responding
- `RedisClusterDegraded` - Redis cluster has failed nodes
- `CrossServicePartition` - Services cannot reach each other

### Metrics to Check

```promql
# Cassandra node status
cassandra_node_status{cluster="butterfly"}

# PostgreSQL replication lag
pg_replication_lag{database="butterfly"}

# Redis cluster health
redis_cluster_state{cluster="butterfly"}

# Cross-service connectivity
probe_success{job="blackbox-exporter",target=~".*butterfly.*"}
```

## Immediate Actions

### 1. Identify the Affected Component

```bash
# Check all database health endpoints
echo "=== Cassandra ===" && curl -s http://capsule-service:8080/actuator/health/cassandra | jq .status
echo "=== PostgreSQL ===" && curl -s http://plato-service:8080/actuator/health/db | jq .status
echo "=== Redis ===" && curl -s http://nexus-service:8080/actuator/health/redis | jq .status

# Check service-to-service connectivity
for src in capsule odyssey plato nexus synapse perception; do
  for dst in capsule odyssey plato nexus synapse perception; do
    if [ "$src" != "$dst" ]; then
      echo "$src -> $dst: $(kubectl exec ${src}-0 -- curl -s -o /dev/null -w '%{http_code}' http://${dst}-service:8080/actuator/health 2>/dev/null)"
    fi
  done
done
```

### 2. Check Network Policies

```bash
# List network policies
kubectl get networkpolicies -n butterfly -o wide

# Check for recent changes
kubectl get networkpolicies -n butterfly -o yaml | grep -A5 "creationTimestamp"
```

## Remediation by Scenario

### Scenario A: Cassandra Partial Outage

**Symptoms**: CAPSULE queries slow or failing, history unavailable

1. **Check Cassandra cluster status**:
   ```bash
   kubectl exec cassandra-0 -- nodetool status
   ```

2. **Identify affected nodes**:
   ```bash
   kubectl exec cassandra-0 -- nodetool describecluster
   ```

3. **If node is DOWN, attempt restart**:
   ```bash
   kubectl delete pod cassandra-2  # Replace with affected node
   ```

4. **If node won't recover, check storage**:
   ```bash
   kubectl describe pvc cassandra-data-cassandra-2
   ```

5. **CAPSULE fallback behavior**:
   - Reads from available replicas (RF=3)
   - Writes queued for retry
   - History queries may be slower

### Scenario B: PostgreSQL Primary Failure

**Symptoms**: PLATO writes failing, ODYSSEY affected

1. **Check PostgreSQL cluster**:
   ```bash
   kubectl exec postgres-0 -- pg_isready
   kubectl exec postgres-0 -- psql -c "SELECT * FROM pg_stat_replication;"
   ```

2. **If primary down, failover to replica**:
   ```bash
   # If using Patroni
   kubectl exec postgres-0 -- patronictl failover --candidate postgres-1
   ```

3. **PLATO/ODYSSEY fallback**:
   - Read-only operations continue
   - Writes fail with 503
   - Governance evaluations use cached policies

### Scenario C: Redis Cluster Degraded

**Symptoms**: NEXUS cache misses, SYNAPSE rate limiting issues

1. **Check Redis cluster**:
   ```bash
   kubectl exec redis-0 -- redis-cli cluster info
   kubectl exec redis-0 -- redis-cli cluster nodes
   ```

2. **Fix failing node**:
   ```bash
   kubectl delete pod redis-2  # Replace with affected node
   ```

3. **Verify cluster recovery**:
   ```bash
   kubectl exec redis-0 -- redis-cli cluster check localhost:6379
   ```

4. **NEXUS/SYNAPSE fallback**:
   - Cache misses increase
   - Direct database queries
   - Rate limiting may be inconsistent

### Scenario D: Cross-Service Network Partition

**Symptoms**: Services healthy individually, cross-service calls failing

1. **Diagnose partition**:
   ```bash
   # From ODYSSEY, ping CAPSULE
   kubectl exec odyssey-0 -- curl -v http://capsule-service:8080/actuator/health
   
   # Check DNS
   kubectl exec odyssey-0 -- nslookup capsule-service
   ```

2. **Check for stuck network chaos**:
   ```bash
   kubectl get networkchaos -n butterfly-chaos
   kubectl delete networkchaos --all -n butterfly-chaos
   ```

3. **Check Kubernetes network plugin**:
   ```bash
   kubectl get pods -n kube-system -l k8s-app=calico-node  # or your CNI
   kubectl logs -n kube-system -l k8s-app=calico-node --tail=50
   ```

4. **Restart affected pods** (if connections stale):
   ```bash
   kubectl rollout restart deployment odyssey-core capsule
   ```

## Data Consistency Verification

After partition heals or database recovers:

1. **Check for data inconsistencies**:
   ```bash
   # Compare CAPSULE count across nodes
   for i in 0 1 2; do
     echo "cassandra-$i: $(kubectl exec cassandra-$i -- cqlsh -e 'SELECT COUNT(*) FROM butterfly.capsules;')"
   done
   ```

2. **Run repair if needed** (Cassandra):
   ```bash
   kubectl exec cassandra-0 -- nodetool repair butterfly
   ```

3. **Verify cross-service consistency**:
   ```bash
   # Query same entity from CAPSULE and ODYSSEY
   ENTITY_ID="rim:test:verification"
   
   echo "CAPSULE:" 
   curl -s "http://capsule-service:8080/api/v1/capsules?scopeId=$ENTITY_ID" | jq '.content | length'
   
   echo "ODYSSEY:"
   curl -s "http://odyssey-core:8080/api/v1/entities?entityId=$ENTITY_ID" | jq '.totalElements'
   ```

## Recovery Verification

1. **All databases healthy**:
   ```bash
   for svc in capsule-service plato-service nexus-service; do
     echo "=== $svc ===" && curl -s http://$svc:8080/actuator/health | jq '.components | to_entries[] | select(.key | test("cassandra|db|redis")) | {(.key): .value.status}'
   done
   ```

2. **Cross-service communication restored**:
   ```bash
   # Run golden path E2E
   butterfly-e2e/run-golden-path.sh
   ```

3. **Run chaos validation**:
   ```bash
   chaos/scripts/run-chaos-suite.sh --experiment capsule-odyssey-partition --validate-only
   ```

## Prevention

- Use appropriate replication factors (RF=3 for Cassandra)
- Configure connection pool timeouts
- Implement circuit breakers for all cross-service calls
- Regular chaos testing to validate resilience
- Monitor replication lag continuously

## Escalation

| Time | Action |
|------|--------|
| 0-15 min | On-call investigates |
| 15-30 min | Engage database team |
| 30+ min | Incident commander, consider DR failover |
| 1+ hour | Post-incident review scheduled |

