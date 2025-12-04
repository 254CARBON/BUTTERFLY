# Runbook: SYNAPSE Unavailable / Degraded

## Overview

This runbook provides remediation steps when SYNAPSE (Action Execution Engine) becomes unavailable or degraded. SYNAPSE is responsible for executing actions from PLATO plans, and its unavailability impacts plan execution.

## Related Chaos Experiment

- **File**: `chaos/experiments/plato-synapse-degraded.yaml`
- **Purpose**: Validates PLATO graceful degradation when SYNAPSE is slow

## Detection

### Alerts

- `SynapseHighLatency` - P99 latency > 3s
- `SynapseErrorRate` - Error rate > 5%
- `SynapseCircuitOpen` - Circuit breaker to SYNAPSE is OPEN
- `SynapseUnhealthy` - Health check failing

### Metrics to Check

```promql
# Error rate
sum(rate(http_server_requests_seconds_count{service="synapse-service",status=~"5.."}[5m])) 
/ sum(rate(http_server_requests_seconds_count{service="synapse-service"}[5m]))

# P99 latency
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{service="synapse-service"}[5m])) by (le))

# Circuit breaker state
resilience4j_circuitbreaker_state{service="plato-service",name="synapse"}
```

## Immediate Actions

### 1. Assess Impact

```bash
# Check SYNAPSE health
curl -s http://synapse-service:8080/actuator/health | jq .

# Check PLATO circuit breaker state
curl -s http://plato-service:8080/actuator/circuitbreakers | jq '.circuitBreakers.synapse'

# Check action queue depth
curl -s http://synapse-service:8080/actuator/metrics/synapse.action.queue.size | jq .
```

### 2. Check Dependencies

SYNAPSE depends on:
- **Kafka**: For receiving action requests
- **Redis**: For action result caching and rate limiting
- **Cassandra**: For action persistence

```bash
# Check Kafka connectivity
kubectl exec -it synapse-0 -- curl -s http://localhost:8080/actuator/health/kafka

# Check Redis connectivity  
kubectl exec -it synapse-0 -- curl -s http://localhost:8080/actuator/health/redis

# Check Cassandra connectivity
kubectl exec -it synapse-0 -- curl -s http://localhost:8080/actuator/health/cassandra
```

### 3. Check Resource Usage

```bash
# Check pod resources
kubectl top pod -l app=synapse

# Check for OOM events
kubectl describe pod -l app=synapse | grep -A5 "OOMKilled"

# Check thread pool saturation
curl -s http://synapse-service:8080/actuator/metrics/executor.active | jq .
```

## Remediation Steps

### Scenario A: High Latency

**Symptoms**: P99 > 3s, no errors

1. Check for slow tool executions:
   ```bash
   curl -s "http://synapse-service:8080/actuator/metrics/synapse.action.execution.duration" | jq .
   ```

2. Check for Kafka consumer lag:
   ```bash
   kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group synapse-actions
   ```

3. Scale horizontally if needed:
   ```bash
   kubectl scale deployment synapse --replicas=4
   ```

### Scenario B: High Error Rate

**Symptoms**: 5xx errors > 5%

1. Check error logs:
   ```bash
   kubectl logs -l app=synapse --tail=100 | grep -i error
   ```

2. Check tool registry health:
   ```bash
   curl -s http://synapse-service:8080/actuator/health/toolRegistry | jq .
   ```

3. Check safety service:
   ```bash
   curl -s http://synapse-service:8080/actuator/health/safetyService | jq .
   ```

### Scenario C: Complete Unavailability

**Symptoms**: All requests failing, health check down

1. Check pod status:
   ```bash
   kubectl get pods -l app=synapse
   kubectl describe pod -l app=synapse
   ```

2. Restart if stuck:
   ```bash
   kubectl rollout restart deployment synapse
   ```

3. Check persistent volume:
   ```bash
   kubectl describe pvc synapse-data
   ```

## PLATO Fallback Behavior

When SYNAPSE is unavailable, PLATO should:

1. **Circuit Breaker Open**: Returns cached results or queues for retry
2. **DRY_RUN Mode**: Plans with DRY_RUN continue without SYNAPSE
3. **Plan Status**: Plans show `DEGRADED` or `QUEUED_FOR_RETRY`

### Manual Circuit Breaker Reset

If needed, manually close the circuit breaker:

```bash
# Force HALF_OPEN to test
curl -X POST http://plato-service:8080/actuator/circuitbreakers/synapse/halfOpen

# After confirming recovery, it will auto-close
```

## Verification

After remediation, verify:

1. **SYNAPSE Health**:
   ```bash
   curl -s http://synapse-service:8080/actuator/health | jq '.status'
   ```

2. **PLATO Integration**:
   ```bash
   # Circuit breaker should be CLOSED
   curl -s http://plato-service:8080/actuator/circuitbreakers | jq '.circuitBreakers.synapse.state'
   ```

3. **Run Chaos Test**:
   ```bash
   chaos/scripts/run-chaos-suite.sh --experiment plato-synapse-degraded --validate-only
   ```

## Prevention

- Set appropriate timeout and retry configurations
- Monitor Kafka consumer lag
- Autoscale based on queue depth
- Regular chaos testing (weekly)

## Escalation

If unable to resolve within 30 minutes:
1. Page on-call SRE
2. Engage SYNAPSE team lead
3. Consider DR failover if multi-region

