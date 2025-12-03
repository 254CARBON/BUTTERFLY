# BUTTERFLY Monitoring Guide

> Observability strategy and implementation for BUTTERFLY

**Last Updated**: 2025-12-03  
**Target Audience**: SREs, DevOps engineers

---

## Overview

BUTTERFLY implements comprehensive observability through metrics, logging, and distributed tracing.

---

## Observability Stack

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Observability Architecture                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                      BUTTERFLY Services                                  ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ││
│  │  │ CAPSULE  │  │ ODYSSEY  │  │PERCEPTION│  │  PLATO   │  │  NEXUS   │  ││
│  │  │/metrics  │  │/metrics  │  │/metrics  │  │/metrics  │  │/metrics  │  ││
│  │  │/logs     │  │/logs     │  │/logs     │  │/logs     │  │/logs     │  ││
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  ││
│  │       │             │             │             │             │         ││
│  └───────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────┘│
│          │             │             │             │             │          │
│  ┌───────┴─────────────┴─────────────┴─────────────┴─────────────┴─────────┐│
│  │                        Collection Layer                                  ││
│  │                                                                          ││
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      ││
│  │  │    Prometheus    │  │   Fluent Bit     │  │  OpenTelemetry   │      ││
│  │  │  (metrics pull)  │  │  (log shipping)  │  │    Collector     │      ││
│  │  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘      ││
│  │           │                     │                     │                 ││
│  └───────────┼─────────────────────┼─────────────────────┼─────────────────┘│
│              │                     │                     │                  │
│  ┌───────────┴─────────────────────┴─────────────────────┴─────────────────┐│
│  │                         Storage Layer                                    ││
│  │                                                                          ││
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      ││
│  │  │    Prometheus    │  │  Elasticsearch   │  │      Jaeger      │      ││
│  │  │     (TSDB)       │  │     (Logs)       │  │    (Traces)      │      ││
│  │  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘      ││
│  │           │                     │                     │                 ││
│  └───────────┼─────────────────────┼─────────────────────┼─────────────────┘│
│              │                     │                     │                  │
│  ┌───────────┴─────────────────────┴─────────────────────┴─────────────────┐│
│  │                       Visualization Layer                                ││
│  │                                                                          ││
│  │  ┌────────────────────────────────────────────────────────────────────┐ ││
│  │  │                          Grafana                                    │ ││
│  │  │   Dashboards | Alerts | Exploration                                │ ││
│  │  └────────────────────────────────────────────────────────────────────┘ ││
│  │                                                                          ││
│  └──────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Section Contents

| Document | Description |
|----------|-------------|
| [Metrics Catalog](metrics-catalog.md) | All service metrics |
| [Dashboards](dashboards.md) | Grafana dashboards |
| [Alerting](alerting.md) | Alert configuration |

---

## Quick Start

### Access Dashboards

| Tool | URL | Purpose |
|------|-----|---------|
| Grafana | `http://localhost:3000` | Visualization |
| Prometheus | `http://localhost:9090` | Metrics query |
| Jaeger | `http://localhost:16686` | Traces |
| Kibana | `http://localhost:5601` | Logs |

### Default Credentials

```bash
# Grafana (change immediately!)
Username: admin
Password: admin

# Prometheus
No authentication (secure with network policy)
```

---

## Metrics Collection

### Spring Boot Actuator

All services expose metrics via Spring Boot Actuator:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:development}
```

### Prometheus Scrape Config

```yaml
scrape_configs:
  - job_name: 'butterfly-services'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        target_label: __address__
        regex: (.+)
        replacement: ${1}
```

---

## Logging

### Structured Logging Format

```json
{
  "timestamp": "2024-12-03T10:00:00.000Z",
  "level": "INFO",
  "logger": "com.z254.butterfly.capsule.service.CapsuleService",
  "message": "Capsule created successfully",
  "service": "capsule",
  "traceId": "abc123",
  "spanId": "def456",
  "context": {
    "capsuleId": "cap_xyz",
    "scopeId": "rim:entity:finance:EURUSD",
    "duration_ms": 45
  }
}
```

### Logback Configuration

```xml
<configuration>
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"service":"${SERVICE_NAME}"}</customFields>
      <includeMdcKeyName>traceId</includeMdcKeyName>
      <includeMdcKeyName>spanId</includeMdcKeyName>
    </encoder>
  </appender>
  
  <root level="INFO">
    <appender-ref ref="JSON"/>
  </root>
</configuration>
```

---

## Distributed Tracing

### Trace Propagation

Services propagate trace context via headers:

| Header | Purpose |
|--------|---------|
| `traceparent` | W3C Trace Context |
| `tracestate` | W3C Trace State |
| `X-Correlation-Id` | Business correlation |

### Sampling Strategy

```yaml
opentelemetry:
  traces:
    sampler:
      type: parentbased_traceidratio
      arg: 0.1  # 10% sampling in production
```

---

## Key Queries

### PromQL Examples

```promql
# Request rate by service
sum(rate(http_server_requests_seconds_count[5m])) by (application)

# 99th percentile latency
histogram_quantile(0.99, 
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))

# Error rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) 
  / sum(rate(http_server_requests_seconds_count[5m]))

# JVM heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# Kafka consumer lag
sum(kafka_consumer_lag) by (group, topic)
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Operations Overview](../README.md) | Operations index |
| [Alerting](alerting.md) | Alert configuration |
| [Runbooks](../runbooks/README.md) | Incident response |

