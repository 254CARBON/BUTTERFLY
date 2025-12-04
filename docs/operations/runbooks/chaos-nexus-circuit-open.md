# Runbook: NEXUS Circuit Breakers Open

## Overview

This runbook provides remediation steps when NEXUS circuit breakers are in OPEN state, preventing communication with upstream services (CAPSULE, PERCEPTION, ODYSSEY, PLATO).

## Related Chaos Experiment

- **File**: `chaos/experiments/nexus-perception-timeout.yaml`
- **Purpose**: Validates NEXUS circuit breaker behavior on PERCEPTION timeout

## Detection

### Alerts

- `NexusCircuitBreakerOpen` - Any circuit breaker in OPEN state
- `NexusUpstreamDegraded` - Multiple upstream services unhealthy
- `NexusTemporalQueryDegraded` - Temporal queries returning partial data

### Metrics to Check

```promql
# Circuit breaker states
resilience4j_circuitbreaker_state{service="butterfly-nexus"}

# Failed calls rate
rate(resilience4j_circuitbreaker_calls_total{service="butterfly-nexus",kind="failed"}[5m])

# Upstream health
up{job=~"perception|capsule|odyssey|plato"}
```

## Understanding Circuit Breaker States

| State | Description | NEXUS Behavior |
|-------|-------------|----------------|
| CLOSED | Normal operation | Full functionality |
| OPEN | Too many failures | Returns cached data, fallbacks |
| HALF_OPEN | Testing recovery | Allowing limited requests |
| FORCED_OPEN | Manually opened | No requests allowed |

## Immediate Actions

### 1. Identify Which Circuit Breaker(s) Are Open

```bash
curl -s http://nexus-service:8080/actuator/circuitbreakers | jq '.circuitBreakers | to_entries[] | select(.value.state != "CLOSED") | {name: .key, state: .value.state}'
```

### 2. Check Upstream Service Health

```bash
# Check all upstream services
for svc in perception-api capsule-service odyssey-core plato-service; do
  echo "=== $svc ==="
  curl -s http://$svc:8080/actuator/health | jq '.status'
done
```

### 3. Check Circuit Breaker Metrics

```bash
# Get detailed metrics for a specific circuit breaker
CB_NAME="perception"  # or capsule, odyssey, plato
curl -s http://nexus-service:8080/actuator/circuitbreakers/$CB_NAME | jq .
```

## Remediation Steps

### Scenario A: Single Circuit Breaker Open

**Example**: PERCEPTION circuit breaker OPEN

1. **Verify PERCEPTION is actually down**:
   ```bash
   curl -s http://perception-api:8080/actuator/health
   ```

2. **If PERCEPTION is healthy**, the circuit may be stuck:
   ```bash
   # Transition to HALF_OPEN to test
   curl -X POST http://nexus-service:8080/actuator/circuitbreakers/perception/halfOpen
   ```

3. **If PERCEPTION is unhealthy**, fix PERCEPTION first (see PERCEPTION runbook)

### Scenario B: Multiple Circuit Breakers Open

1. **Check for common cause** (network, Kubernetes):
   ```bash
   # Check network policies
   kubectl get networkpolicies -n butterfly
   
   # Check DNS resolution
   kubectl exec nexus-0 -- nslookup perception-api
   ```

2. **Check for resource exhaustion**:
   ```bash
   kubectl top pods -n butterfly
   ```

3. **Check for cluster-wide issues**:
   ```bash
   kubectl get events -n butterfly --sort-by='.lastTimestamp'
   ```

### Scenario C: Circuit Breaker Stuck in HALF_OPEN

1. **Check if test requests are passing**:
   ```bash
   # Monitor metrics
   watch -n 1 'curl -s http://nexus-service:8080/actuator/circuitbreakers/perception | jq .metrics'
   ```

2. **Increase permitted calls in HALF_OPEN** (requires config change):
   ```yaml
   # application.yml
   resilience4j.circuitbreaker:
     instances:
       perception:
         permittedNumberOfCallsInHalfOpenState: 10  # default is 5
   ```

3. **Force close if upstream is healthy**:
   ```bash
   curl -X POST http://nexus-service:8080/actuator/circuitbreakers/perception/forceClose
   ```

## NEXUS Degradation Behavior

When circuit breakers are OPEN:

| Circuit | NEXUS Behavior |
|---------|----------------|
| PERCEPTION | Returns cached RIM state, temporal queries partial |
| CAPSULE | Returns cached history, new CAPSULEs queued |
| ODYSSEY | Returns cached world state, paths may be stale |
| PLATO | Governance checks use cached policies |

### Verify Cached Data Is Available

```bash
# Check cache statistics
curl -s http://nexus-service:8080/actuator/caches | jq .

# Check Redis cache
redis-cli -h redis-master KEYS "nexus:cache:*" | head -20
```

## Recovery Verification

After upstream service recovers:

1. **Wait for automatic transition**:
   - HALF_OPEN → CLOSED (if test requests succeed)
   - Typically 60-90 seconds

2. **Verify circuit closed**:
   ```bash
   curl -s http://nexus-service:8080/actuator/circuitbreakers | jq '.circuitBreakers | to_entries[] | {name: .key, state: .value.state}'
   ```

3. **Verify full functionality**:
   ```bash
   # Temporal query with all sources
   curl -X POST http://nexus-service:8080/api/v1/temporal/slice \
     -H "Content-Type: application/json" \
     -H "X-Tenant-ID: test" \
     -d '{"entityId":"test:entity","timeframe":{"lookback":"PT1H","lookahead":"PT30M"},"includeSources":["perception","capsule","odyssey"]}'
   ```

4. **Run validation chaos test**:
   ```bash
   chaos/scripts/run-chaos-suite.sh --experiment nexus-perception-timeout --validate-only
   ```

## Configuration Reference

NEXUS circuit breaker defaults (in `application.yml`):

```yaml
resilience4j.circuitbreaker:
  instances:
    perception:
      slidingWindowSize: 10
      failureRateThreshold: 50
      waitDurationInOpenState: 60s
      permittedNumberOfCallsInHalfOpenState: 5
      slowCallRateThreshold: 100
      slowCallDurationThreshold: 2s
```

## Escalation

If circuit breakers remain OPEN after 15 minutes:
1. Page NEXUS team lead
2. Check for wider infrastructure issues
3. Consider manual fallback to read-only mode

