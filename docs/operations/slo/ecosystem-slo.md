# BUTTERFLY Ecosystem SLO Framework

## Overview

This document defines the Service Level Objectives (SLOs) for the BUTTERFLY platform ecosystem. These SLOs provide reliability targets that guide capacity planning, incident response, and feature development.

## SLO Principles

1. **Error Budget**: Each service has an error budget derived from its availability target
2. **Multi-Signal**: SLOs track availability, latency, and throughput
3. **Tenant-Aware**: Per-tenant SLO tracking enables isolation guarantees
4. **Cascading**: Cross-service dependencies factor into composite SLOs

---

## Service-Level SLOs

### PERCEPTION (Sensory Layer)

| Metric | Target | Window | Priority |
|--------|--------|--------|----------|
| Availability | 99.9% | 30 days | P0 |
| P50 Latency | ≤ 100ms | - | P1 |
| P95 Latency | ≤ 200ms | - | P0 |
| P99 Latency | ≤ 500ms | - | P1 |
| Throughput | 10k events/sec | - | P1 |
| Error Rate | < 0.1% | 1 hour | P0 |

**Key Endpoints:**
- `/api/v1/signals` - P95 < 150ms
- `/api/v1/scenarios` - P95 < 300ms
- `/api/v1/rim` - P95 < 200ms

### CAPSULE (Memory Layer)

| Metric | Target | Window | Priority |
|--------|--------|--------|----------|
| Availability | 99.9% | 30 days | P0 |
| Read P95 Latency | ≤ 100ms | - | P0 |
| Write P95 Latency | ≤ 200ms | - | P1 |
| P99 Latency | ≤ 500ms | - | P1 |
| Throughput (reads) | 5k req/sec | - | P1 |
| Throughput (writes) | 5k writes/sec | - | P1 |
| Error Rate | < 0.1% | 1 hour | P0 |

**Key Endpoints:**
- `/api/v1/entities/*` - P95 < 100ms (reads)
- `/api/v1/timeseries/*` - P95 < 150ms
- `/api/v1/streams/*` - P95 < 200ms

### ODYSSEY (Cognition Layer)

| Metric | Target | Window | Priority |
|--------|--------|--------|----------|
| Availability | 99.9% | 30 days | P0 |
| P50 Latency | ≤ 200ms | - | P1 |
| P95 Latency | ≤ 500ms | - | P0 |
| P99 Latency | ≤ 2s | - | P1 |
| Throughput | 1k paths/sec | - | P2 |
| Error Rate | < 0.5% | 1 hour | P0 |

**Key Endpoints:**
- `/api/v1/graph/*` - P95 < 500ms
- `/api/v1/forecast/*` - P95 < 1s
- `/api/v1/scenarios/*` - P95 < 800ms

### PLATO (Governance Layer)

| Metric | Target | Window | Priority |
|--------|--------|--------|----------|
| Availability | 99.5% | 30 days | P1 |
| P50 Latency | ≤ 1s | - | P1 |
| P95 Latency | ≤ 5s | - | P0 |
| P99 Latency | ≤ 30s | - | P2 |
| Throughput | 100 plans/sec | - | P2 |
| Error Rate | < 1% | 1 hour | P1 |

**Key Endpoints:**
- `/api/v1/specs/*` - P95 < 500ms
- `/api/v1/plans/*` - P95 < 5s
- `/api/v1/engines/*` - P95 < 10s

### NEXUS (Integration Layer)

| Metric | Target | Window | Priority |
|--------|--------|--------|----------|
| Availability | 99.99% | 30 days | P0 |
| Gateway P50 Latency | ≤ 30ms | - | P0 |
| Gateway P95 Latency | ≤ 50ms | - | P0 |
| Gateway P99 Latency | ≤ 100ms | - | P0 |
| Throughput | passthrough | - | P0 |
| Error Rate | < 0.01% | 1 hour | P0 |

**Key Endpoints:**
- `/api/v1/temporal/*` - P95 < 100ms
- `/api/v1/synthesis/*` - P95 < 200ms
- All proxied calls - < 50ms overhead

### SYNAPSE (Execution Engine)

| Metric | Target | Window | Priority |
|--------|--------|--------|----------|
| Availability | 99.9% | 30 days | P0 |
| Action Queue P95 | ≤ 100ms | - | P1 |
| Execution varies by tool | - | - | - |
| Throughput | 500 actions/sec | - | P1 |
| Error Rate | < 0.5% | 1 hour | P0 |

**Key Endpoints:**
- `/api/v1/actions/*` - P95 < 200ms (submission)
- `/api/v1/workflows/*` - P95 varies
- `/api/v1/tools/*` - P95 < 100ms

---

## Composite SLOs

### End-to-End Query SLO

A complete intelligence query traversing NEXUS → CAPSULE → PERCEPTION → ODYSSEY:

| Metric | Target |
|--------|--------|
| Availability | 99.5% |
| P95 Latency | ≤ 2s |
| Error Rate | < 1% |

### Plan Execution SLO

PLATO plan execution via SYNAPSE:

| Metric | Target |
|--------|--------|
| Availability | 99% |
| P95 Completion | ≤ 60s |
| Success Rate | > 95% |

---

## Error Budget Calculation

### Formula

```
Error Budget = 100% - Availability Target

Monthly Error Budget Minutes = (30 days × 24 hours × 60 minutes) × (1 - Availability)
```

### Service Error Budgets (30-day)

| Service | Availability | Error Budget | Minutes/Month |
|---------|--------------|--------------|---------------|
| PERCEPTION | 99.9% | 0.1% | 43.2 min |
| CAPSULE | 99.9% | 0.1% | 43.2 min |
| ODYSSEY | 99.9% | 0.1% | 43.2 min |
| PLATO | 99.5% | 0.5% | 216 min |
| NEXUS | 99.99% | 0.01% | 4.3 min |
| SYNAPSE | 99.9% | 0.1% | 43.2 min |

---

## Alert Thresholds

### Fast Burn (Critical - Page)

Triggers when error budget consumption rate would exhaust budget in < 2 hours:

```yaml
- alert: ButterflyErrorBudgetFastBurn
  expr: |
    (
      (1 - butterfly_slo_success_rate:ratio_rate1h{service=~".*"}) 
      / (1 - butterfly_slo_target{service=~".*"})
    ) > 14.4
  for: 2m
  labels:
    severity: critical
```

### Slow Burn (Warning - Ticket)

Triggers when error budget consumption rate would exhaust budget in < 6 hours:

```yaml
- alert: ButterflyErrorBudgetSlowBurn
  expr: |
    (
      (1 - butterfly_slo_success_rate:ratio_rate6h{service=~".*"}) 
      / (1 - butterfly_slo_target{service=~".*"})
    ) > 6
  for: 15m
  labels:
    severity: warning
```

---

## Tenant SLO Guarantees

### Tier-Based SLOs

| Tier | Availability | P95 Latency Multiplier | Rate Limit |
|------|--------------|------------------------|------------|
| Enterprise | 99.99% | 1.0x | Unlimited |
| Professional | 99.9% | 1.5x | 10k req/min |
| Standard | 99.5% | 2.0x | 1k req/min |
| Free | 99% | 3.0x | 100 req/min |

### Per-Tenant Metrics

All metrics are tagged with `tenantId` for per-tenant SLO tracking:

```promql
# Per-tenant error rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (tenantId)
/
sum(rate(http_server_requests_seconds_count[5m])) by (tenantId)
```

---

## Monitoring Queries

### Service Availability (30-day)

```promql
# PERCEPTION availability
1 - (
  sum(increase(http_server_requests_seconds_count{service="perception-api",status=~"5.."}[30d]))
  /
  sum(increase(http_server_requests_seconds_count{service="perception-api"}[30d]))
)
```

### Latency P95

```promql
histogram_quantile(0.95, 
  sum(rate(http_server_requests_seconds_bucket{service="perception-api"}[5m])) by (le)
)
```

### Error Budget Remaining

```promql
# Error budget remaining (percentage)
1 - (
  (1 - butterfly_slo_success_rate:ratio_rate30d{service="perception-api"})
  /
  (1 - 0.999)  # 99.9% target
)
```

---

## Review Cadence

| Review | Frequency | Participants |
|--------|-----------|--------------|
| SLO Health Check | Daily | On-call |
| Error Budget Review | Weekly | Engineering Lead |
| SLO Target Review | Quarterly | Platform Team |
| Tenant SLA Review | Monthly | Account Team |

