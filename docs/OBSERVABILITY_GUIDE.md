# BUTTERFLY Observability Guide

## Overview

This guide describes the observability architecture for the BUTTERFLY platform, covering metrics, logging, tracing, alerting, and dashboards. The observability stack enables multi-tenant, connector-aware monitoring across all services.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BUTTERFLY Services                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ PERCEPTION  │  │  CAPSULE    │  │  ODYSSEY    │  │  PLATO    │  NEXUS  │ │
│  │  Ingestion  │  │   4D Hist   │  │   Graph     │  │ Governance│ Gateway │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────┬─────┴────┬────┘ │
│         │                │                │                │          │      │
│         └────────────────┼────────────────┼────────────────┼──────────┘      │
│                          │                │                │                 │
└──────────────────────────┼────────────────┼────────────────┼─────────────────┘
                           │                │                │
                    ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
                    │ Prometheus  │  │    Loki     │  │   Tempo     │
                    │  (Metrics)  │  │   (Logs)    │  │  (Traces)   │
                    └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
                           │                │                │
                           └────────────────┼────────────────┘
                                            │
                                     ┌──────▼──────┐
                                     │   Grafana   │
                                     │ (Dashboards)│
                                     └──────┬──────┘
                                            │
                                     ┌──────▼──────┐
                                     │Alertmanager │
                                     │  (Alerts)   │
                                     └─────────────┘
```

## Components

### Shared Observability Conventions

| Domain | Convention | Details / Examples |
|--------|------------|--------------------|
| Metric names | `<layer>_<signal>_<unit>`, `butterfly:<category>:<signal>` | Core HTTP histograms/counters keep Spring/Micrometer defaults (`http_server_requests_seconds_*`). Ecosystem rollups live under `butterfly:` (e.g., `butterfly:slo_availability:ratio_rate5m`). |
| Required metric labels | `service`, `application`, `environment`, `operation`, `status`, `tenantId` (if multi-tenant) | `service` is derived from `spring.application.name` and injected automatically by the `ServiceLabelMeterFilter`. Explicit instrumentation must set `operation` (logical action) and `status` (success/error) for timers and counters. |
| Trace attributes | `service.name`, `bf.service`, `bf.operation`, `bf.tenantId`, `bf.connectorId`, `bf.planId`, `bf.sloBudget` | Add to root spans so Grafana/Tempo exemplars can pivot back to SLO dashboards. `bf.sloBudget` is populated with the current `%` of monthly budget remaining. |
| Log fields | Structured JSON with `service`, `tenantId`, `traceId`, `spanId`, `correlationId`, `operation`, `errorBudgetBurnRate` | MDC helpers in `butterfly-common` populate these fields. Include `errorBudgetBurnRate` whenever emitting WARN/ERROR around SLO usage. |

**Instrumentation checklist:**
1. Start timers via `Timer.builder("service.operation.duration")` and tag them with `operation`, `status`, and context-specific fields (e.g., `routeId`, `planId`).
2. When emitting custom counters, convert any boolean outcomes into `status=success|failure` tags to align with SLO rollups.
3. For long-running workflows (PLATO plans, SYNAPSE actions) emit `duration_seconds` histograms with `executionType` and `result` tags to drive burn-down charts.
4. Populate trace attributes and MDC at the ingress of each service (gateway filters, Kafka consumers, WebSocket handshakes) so subsequent logs/metrics automatically inherit the shared context.

### Metrics (Prometheus + Micrometer)

All services expose Prometheus-compatible metrics via Spring Boot Actuator at `/actuator/prometheus`.

**Standard Tags:**
- `application` - Service name
- `environment` - dev/staging/prod
- `tenantId` - Tenant identifier (when applicable)
- `routeId` / `sourceId` - Connector identifiers (PERCEPTION)

**Key Metric Categories:**
- HTTP request metrics: `http_server_requests_seconds`
- JVM metrics: `jvm_*`
- Custom business metrics: `{service}.*`
- Circuit breaker: `resilience4j_circuitbreaker_*`
- Kafka: `kafka_consumer_*`, `kafka_producer_*`

### Logging (Loki + Logstash)

All services use structured JSON logging with MDC correlation fields.

**Log Format:**
```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "com.z254.butterfly.capsule.service.CapsuleService",
  "message": "Capsule created successfully",
  "service": "capsule-service",
  "environment": "prod",
  "traceId": "abc123",
  "spanId": "def456",
  "tenantId": "tenant-123",
  "correlationId": "corr-789"
}
```

**Required MDC Fields:**
- `traceId`, `spanId` - Distributed tracing
- `tenantId` - Multi-tenancy
- `correlationId` - Request correlation
- `requestId` - Per-request tracking

### Tracing (Tempo + OpenTelemetry)

Distributed tracing uses W3C Trace Context propagation.

**Trace Propagation:**
- HTTP: `traceparent`, `tracestate` headers
- Kafka: `traceparent` header in message headers
- WebSocket: Captured at handshake, propagated in session

**Sampling Configuration:**
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, adjust for prod
```

## Multi-Tenant Observability

### Tenant-Aware Metrics

The `TenantAwareMeterFilter` automatically injects `tenantId` into application metrics from `TenantContextHolder` or MDC.

```java
// Automatic - tenantId added if in context
Counter.builder("capsule.created").register(registry).increment();

// Explicit - when you need specific tenant tagging
Counter.builder("capsule.created")
    .tag("tenantId", tenantId)
    .register(registry)
    .increment();
```

### Querying by Tenant

```promql
# Error rate for specific tenant
sum(rate(http_server_requests_seconds_count{tenantId="tenant-123", status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{tenantId="tenant-123"}[5m]))

# Top tenants by request volume
topk(10, sum by (tenantId) (rate(http_server_requests_seconds_count[5m])))
```

## Service Level Objectives (SLOs)

### Defined SLOs

| Service | Availability | Latency Target | Error Budget |
|---------|-------------|----------------|--------------|
| PERCEPTION | 99.9% | P95 < 200ms | 43.2 min/month |
| CAPSULE | 99.9% | P95 < 100ms | 43.2 min/month |
| ODYSSEY | 99.9% | P95 < 500ms | 43.2 min/month |
| PLATO | 99.5% | P99 < 5s | 3.6 hours/month |
| NEXUS | 99.99% | P95 < 50ms | 4.3 min/month |
| SYNAPSE | 99.9% | P95 < 200ms | 43.2 min/month |

### Cross-Service Golden Signals & Recording Rules

Prometheus recording rules (see `butterfly-nexus/config/prometheus-alerts.yml` and the Helm config map) normalize the golden signals into `butterfly:` metric families:

| Signal | Recording Rule | Description |
|--------|----------------|-------------|
| Availability | `butterfly:slo_availability:ratio_rate5m{service}` | `1 - (5xx / total)` computed via `http_server_requests_seconds_count` with a derived `service` label for every Spring Boot app. |
| Latency | `butterfly:slo_latency:p95_seconds{service}`, `butterfly:slo_latency:p99_seconds{service}` | Histogram quantiles calculated from `http_server_requests_seconds_bucket`. |
| Error rate | `butterfly:slo_error_ratio:rate5m{service}` | Direct ratio of 5xx events to total requests per service. |
| Saturation | `butterfly:slo_saturation:cpu_ratio{service}`, `butterfly:slo_saturation:jvm_heap_ratio{service}` | Average CPU (`system_cpu_usage`) and heap utilization (`jvm_memory_used_bytes/jvm_memory_max_bytes`). |
| Error budget | `butterfly:slo_error_budget_burn_rate:ratio{service}` & `butterfly:slo_error_budget_remaining:ratio{service}` | Burn rate derived from the per-service target (`butterfly:slo_target:availability_ratio{service}`) and actual availability. |

Every target is encoded as a constant metric (`butterfly:slo_target:availability_ratio{service="perception"} == 0.999`, `butterfly:slo_latency_target:p95_seconds{service="nexus"} == 0.05`) so alerting rules and dashboards can look up thresholds without hard-coding them a second time.

Golden signal helpers allow engineers to reason about SLO health with single queries:

```promql
# Availability success ratio (5 minute window)
butterfly:slo_availability:ratio_rate5m{service="perception"}

# Error budget burn rate (ratio above 1 means the budget is being depleted faster than planned)
butterfly:slo_error_budget_burn_rate:ratio{service="nexus"}

# p95 latency in milliseconds
butterfly:slo_latency:p95_seconds{service="capsule"} * 1000
```

### Automated SLO Validation

`butterfly-e2e/check-slo-compliance.sh` executes the E2E golden path, queries Prometheus for the cross-service SLO metrics, and fails fast when:

1. Any service's 5-minute availability drops below its documented target.
2. The burn rate exceeds 2× sustainable (warning) or 14.4× (critical).
3. The recorded p95 latency breaches the per-service target.

Environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `PROMETHEUS_URL` | `http://localhost:9090` | Prometheus API base URL |
| `PROMQL_TIMEOUT` | `30s` | Query timeout |
| `REQUIRED_SERVICES` | `perception,capsule,odyssey,plato,nexus,synapse` | Services that must be validated |

CI runs this script nightly and after the scenario harness completes so that any regression in SLO metrics fails the pipeline immediately.

### Burn Rate Alerts

Multi-window, multi-burn-rate alerting detects budget consumption:

| Alert Type | Burn Rate | Detection Window | Budget Exhaustion |
|-----------|-----------|------------------|-------------------|
| Fast Burn | 14.4x | 1h AND 5m | ~2 hours |
| Slow Burn | 3x | 6h AND 30m | ~10 days |

## Dashboards

### Core Dashboards

1. **BUTTERFLY Ecosystem Overview** (`butterfly-ecosystem-overview`)
   - Service health status
   - Overall availability
   - Cross-service latency
   - Tenant distribution
   - Error budget trend spark lines

2. **BUTTERFLY Cross-Ecosystem SLO** (`butterfly-ecosystem-slo`)
   - Golden signal gauges (`butterfly:slo_*` metrics)
   - Per-service burn-down table with error budget remaining
   - Latency heat maps comparing actual vs. target
   - Evidence-loop (PERCEPTION→CAPSULE→ODYSSEY) vs. decision-loop (PLATO→SYNAPSE→NEXUS) latency waterfalls

3. **Service-Specific Dashboards**
   - PERCEPTION: `ingestion-overview`, `ingestion-tenant-health`, `pipeline-health`
   - CAPSULE: `capsule-slo`, `capsule-tenant`
   - ODYSSEY: `odyssey-slo`, `odyssey-graph`
   - PLATO: `plato-governance`, `plato-engines`
   - NEXUS: `nexus-gateway`

4. **Connector Dashboards**
   - Ingestion Overview: Success/failure rates by route
   - DLQ Replay: Queue depth, replay status
   - Tenant Health: Per-tenant connector health

### Dashboard Variables

All dashboards support filtering by:
- `$service` - Service name
- `$tenantId` - Tenant identifier
- `$environment` - Environment
- Time range picker

## Alerting

### Alert Severity Levels

| Severity | Response Time | Examples |
|----------|--------------|----------|
| Critical | 15 minutes | Fast burn SLO breach, service down |
| Warning | 1 hour | Slow burn SLO breach, high latency |
| Info | Best effort | Unusual patterns, capacity warnings |

### Alert Routing

```yaml
# Alertmanager routes
route:
  receiver: 'default-receiver'
  routes:
    - match:
        severity: critical
      receiver: pagerduty-critical
      continue: true
    - match:
        severity: warning
      receiver: slack-warnings
    - match:
        team: perception
      receiver: perception-team
```

### Alert Silencing

Silence alerts during maintenance:
```bash
# Create silence
curl -X POST http://alertmanager:9093/api/v2/silences \
  -d '{
    "matchers": [{"name": "service", "value": "capsule-service", "isRegex": false}],
    "startsAt": "2024-01-15T10:00:00Z",
    "endsAt": "2024-01-15T12:00:00Z",
    "createdBy": "oncall",
    "comment": "Scheduled maintenance"
  }'
```

## Runbooks

| Alert | Runbook |
|-------|---------|
| `*SLOFastBurn`, `*SLOSlowBurn` | [SLO Breach](operations/runbooks/slo-breach.md) |
| `ConnectorFailureRate` | [Connector Failures](operations/runbooks/connector-failures.md) |
| `TenantDlqRate*` | [DLQ Replay](operations/runbooks/dlq-replay.md) |
| `HighP99Latency` | [Common Issues - High Latency](operations/runbooks/common-issues.md#high-latency) |
| `CircuitBreakerOpen` | [Common Issues - Circuit Breaker](operations/runbooks/common-issues.md#circuit-breaker-open) |

## Correlation and Tracing

### Request Flow Tracing

1. Request arrives at NEXUS gateway
2. `X-Correlation-ID` generated (or extracted from request)
3. Trace context propagated to downstream services
4. MDC populated with `traceId`, `tenantId`, `correlationId`
5. All logs and metrics tagged with context

### Finding Related Data

```bash
# Find trace by correlation ID
curl "http://tempo:3200/api/search?tags=correlationId=corr-123"

# Find logs by trace ID
curl "http://loki:3100/loki/api/v1/query_range" \
  -d 'query={traceId="abc123"}'

# Find metrics for same request (via dashboard variables)
# Use Grafana's exemplar feature to jump from metrics to traces
```

## Configuration

### Service Configuration

Add to `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,circuitbreakers
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:development}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.75,0.95,0.99
  tracing:
    sampling:
      probability: ${TRACING_SAMPLE_RATE:1.0}
```

### Logback Configuration

Use structured JSON logging in production:

```xml
<springProfile name="prod">
    <root level="INFO">
        <appender-ref ref="ASYNC_JSON"/>
    </root>
</springProfile>
```

## Troubleshooting

### No Metrics

1. Check `/actuator/prometheus` endpoint
2. Verify Prometheus scrape config
3. Check service tags match Prometheus job

### Missing Traces

1. Verify `traceparent` header propagation
2. Check Tempo/Zipkin endpoint configuration
3. Verify sampling probability > 0

### Missing Tenant Tags

1. Verify `TenantContextHolder` is populated
2. Check `TenantAwareMeterFilter` is registered
3. Verify MDC is set before metric recording

## References

- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [LogQL Reference](https://grafana.com/docs/loki/latest/logql/)
- [TraceQL Reference](https://grafana.com/docs/tempo/latest/traceql/)
- [SRE Book - SLOs](https://sre.google/sre-book/service-level-objectives/)
