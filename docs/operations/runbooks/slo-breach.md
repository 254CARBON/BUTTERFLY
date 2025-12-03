# SLO Breach Response Runbook

## Overview

This runbook provides procedures for responding to Service Level Objective (SLO) breaches in the BUTTERFLY ecosystem. SLO alerts use multi-window, multi-burn-rate detection to provide both fast detection and low false positive rates.

## Alert Types

### Fast Burn Alerts (Critical)
- **Burn Rate:** 14.4x sustainable rate
- **Time to Budget Exhaustion:** ~2 hours
- **Action Required:** Immediate investigation and mitigation

### Slow Burn Alerts (Warning)
- **Burn Rate:** 3x sustainable rate
- **Time to Budget Exhaustion:** ~10 days
- **Action Required:** Investigation within shift

## SLO Targets by Service

| Service | Availability | Error Budget (monthly) | Latency SLO |
|---------|-------------|------------------------|-------------|
| PERCEPTION | 99.9% | 43.2 min | P95 < 200ms |
| CAPSULE | 99.9% | 43.2 min | P95 < 100ms (reads), < 200ms (writes) |
| ODYSSEY | 99.9% | 43.2 min | P95 < 500ms |
| PLATO | 99.5% | 3.6 hours | P99 < 5s |
| NEXUS | 99.99% | 4.3 min | P95 < 50ms |

## Triggered By

- `*SLOFastBurn` - Fast burn rate alerts
- `*SLOSlowBurn` - Slow burn rate alerts
- `*LatencySLOBreach` - Latency threshold exceeded
- `*TenantHighErrorRate` - Per-tenant error rate breach

## Diagnostic Steps

### 1. Identify Scope

```bash
# Check which services are affected
curl -s "http://prometheus:9090/api/v1/alerts" | jq '.data.alerts[] | select(.state=="firing")'

# Check error rate by service
curl -s "http://prometheus:9090/api/v1/query?query=sum%20by%20(application)%20(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]))"
```

### 2. Check Recent Deployments

```bash
# List recent deployments
kubectl get deployments -A -o json | jq '.items[] | select(.metadata.labels.app | startswith("butterfly")) | {name: .metadata.name, image: .spec.template.spec.containers[0].image, lastUpdate: .metadata.creationTimestamp}'
```

### 3. Check Service Health

```bash
# Service endpoints
curl -s http://localhost:8081/actuator/health # PERCEPTION
curl -s http://localhost:8080/actuator/health # CAPSULE
curl -s http://localhost:8000/actuator/health # ODYSSEY
curl -s http://localhost:8080/actuator/health # PLATO
curl -s http://localhost:8084/actuator/health # NEXUS
```

### 4. Check Circuit Breaker States

```bash
# Check resilience4j circuit breakers
curl -s http://localhost:808X/actuator/circuitbreakers | jq '.circuitBreakers | to_entries[] | {name: .key, state: .value.state, failureRate: .value.failureRate}'
```

### 5. Review Logs

```bash
# Query Loki for recent errors
curl -s "http://loki:3100/loki/api/v1/query_range" \
  -d 'query={service="capsule-service"} |= "ERROR"' \
  -d 'start=1h'

# Filter by tenant
curl -s "http://loki:3100/loki/api/v1/query_range" \
  -d 'query={service="capsule-service", tenantId="tenant-123"} |= "ERROR"'
```

### 6. Check Distributed Traces

```bash
# Find traces with errors in Tempo
curl -s "http://tempo:3200/api/search?tags=error=true&service.name=capsule-service&limit=10"
```

## Mitigation Steps

### Fast Burn Response

1. **Immediate** (0-5 min)
   - Page on-call engineer
   - Check if rollback is needed (recent deployment?)
   - Enable any available feature flags to reduce load

2. **Short-term** (5-30 min)
   - Scale up affected service if resource-constrained
   - Enable rate limiting if traffic spike
   - Reroute traffic if single instance affected

3. **Resolution** (30+ min)
   - Root cause analysis
   - Fix and deploy
   - Update SLO if target was unrealistic

### Slow Burn Response

1. **Acknowledge** (0-15 min)
   - Review alert and confirm trend
   - Check if correlated with known issue

2. **Investigate** (15-60 min)
   - Review error logs and traces
   - Identify pattern (specific endpoint? tenant? time of day?)
   - Check dependency health

3. **Plan** (1-4 hours)
   - Create ticket for root cause fix
   - Implement short-term mitigation if available
   - Update runbook if new failure mode

## Error Budget Exhaustion

If monthly error budget is fully consumed:

1. **Freeze non-critical deployments** to the affected service
2. **Prioritize reliability work** over new features
3. **Review and potentially revise SLO targets**
4. **Conduct post-incident review**

## Related Dashboards

- [BUTTERFLY Ecosystem Overview](https://grafana/d/butterfly-ecosystem-overview)
- [CAPSULE SLO Dashboard](https://grafana/d/capsule-slo)
- [ODYSSEY SLO Dashboard](https://grafana/d/odyssey-slo)
- [PLATO Governance](https://grafana/d/plato-governance)
- [NEXUS Gateway](https://grafana/d/nexus-gateway)

## Escalation

| Time Since Alert | Action |
|-----------------|--------|
| 0-15 min | On-call engineer investigates |
| 15-30 min | Escalate to service owner |
| 30-60 min | Escalate to team lead |
| 1+ hour | Escalate to engineering management |

## Related Runbooks

- [Common Issues](./common-issues.md)
- [High Latency](./common-issues.md#high-latency)
- [Circuit Breaker](./common-issues.md#circuit-breaker-open)
- [Incident Response](./incident-response.md)

