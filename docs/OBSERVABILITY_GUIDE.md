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

2. **Service-Specific Dashboards**
   - PERCEPTION: `ingestion-overview`, `ingestion-tenant-health`, `pipeline-health`
   - CAPSULE: `capsule-slo`, `capsule-tenant`
   - ODYSSEY: `odyssey-slo`, `odyssey-graph`
   - PLATO: `plato-governance`, `plato-engines`
   - NEXUS: `nexus-gateway`

3. **Connector Dashboards**
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

