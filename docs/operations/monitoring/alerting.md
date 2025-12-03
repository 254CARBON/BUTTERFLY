# BUTTERFLY Alerting Configuration

> Alert rules and notification configuration

**Last Updated**: 2025-12-03  
**Target Audience**: SREs, DevOps engineers

---

## Overview

This document covers alert configuration for the BUTTERFLY ecosystem using Prometheus Alertmanager.

---

## Alert Severity Levels

| Severity | Response Time | Notification | Examples |
|----------|---------------|--------------|----------|
| **critical** | 15 minutes | Page on-call | Service down, data loss |
| **warning** | 1 hour | Slack channel | High latency, disk filling |
| **info** | Next business day | Email | Scheduled maintenance |

---

## Core Alert Rules

### Service Health

```yaml
# service-health.rules.yml
groups:
  - name: butterfly.service.health
    interval: 30s
    rules:
      - alert: ServiceDown
        expr: up{job=~"butterfly-.*"} == 0
        for: 1m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "Service {{ $labels.application }} is down"
          description: "{{ $labels.application }} on {{ $labels.instance }} has been down for more than 1 minute."
          runbook: "https://docs.butterfly.example.com/runbooks/service-down"
          
      - alert: ServiceRestartLoop
        expr: increase(process_start_time_seconds{job=~"butterfly-.*"}[1h]) > 3
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Service {{ $labels.application }} is restart looping"
          description: "{{ $labels.application }} has restarted {{ $value }} times in the last hour."
          runbook: "https://docs.butterfly.example.com/runbooks/restart-loop"
          
      - alert: HealthCheckFailing
        expr: http_server_requests_seconds_count{uri="/actuator/health", status!="200"} > 0
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Health check failing for {{ $labels.application }}"
          description: "Health endpoint returning non-200 status."
```

### Latency

```yaml
# latency.rules.yml
groups:
  - name: butterfly.latency
    interval: 30s
    rules:
      - alert: HighP99Latency
        expr: |
          histogram_quantile(0.99, 
            sum(rate(http_server_requests_seconds_bucket{job=~"butterfly-.*"}[5m])) by (le, application)
          ) > 0.5
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "High P99 latency for {{ $labels.application }}"
          description: "P99 latency is {{ $value | humanizeDuration }} (threshold: 500ms)."
          runbook: "https://docs.butterfly.example.com/runbooks/high-latency"
          
      - alert: CriticalLatency
        expr: |
          histogram_quantile(0.99, 
            sum(rate(http_server_requests_seconds_bucket{job=~"butterfly-.*"}[5m])) by (le, application)
          ) > 2
        for: 2m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "Critical latency for {{ $labels.application }}"
          description: "P99 latency is {{ $value | humanizeDuration }} (threshold: 2s)."
          runbook: "https://docs.butterfly.example.com/runbooks/critical-latency"
```

### Error Rate

```yaml
# errors.rules.yml
groups:
  - name: butterfly.errors
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{job=~"butterfly-.*", status=~"5.."}[5m])) by (application)
          / sum(rate(http_server_requests_seconds_count{job=~"butterfly-.*"}[5m])) by (application)
          > 0.01
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "High error rate for {{ $labels.application }}"
          description: "Error rate is {{ $value | humanizePercentage }} (threshold: 1%)."
          runbook: "https://docs.butterfly.example.com/runbooks/high-error-rate"
          
      - alert: CriticalErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{job=~"butterfly-.*", status=~"5.."}[5m])) by (application)
          / sum(rate(http_server_requests_seconds_count{job=~"butterfly-.*"}[5m])) by (application)
          > 0.05
        for: 2m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "Critical error rate for {{ $labels.application }}"
          description: "Error rate is {{ $value | humanizePercentage }} (threshold: 5%)."
          runbook: "https://docs.butterfly.example.com/runbooks/critical-error-rate"
```

### JVM

```yaml
# jvm.rules.yml
groups:
  - name: butterfly.jvm
    interval: 30s
    rules:
      - alert: HighHeapUsage
        expr: |
          jvm_memory_used_bytes{job=~"butterfly-.*", area="heap"} 
          / jvm_memory_max_bytes{job=~"butterfly-.*", area="heap"} 
          > 0.85
        for: 10m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "High heap usage for {{ $labels.application }}"
          description: "Heap usage is {{ $value | humanizePercentage }} (threshold: 85%)."
          runbook: "https://docs.butterfly.example.com/runbooks/high-heap"
          
      - alert: CriticalHeapUsage
        expr: |
          jvm_memory_used_bytes{job=~"butterfly-.*", area="heap"} 
          / jvm_memory_max_bytes{job=~"butterfly-.*", area="heap"} 
          > 0.95
        for: 5m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "Critical heap usage for {{ $labels.application }}"
          description: "Heap usage is {{ $value | humanizePercentage }} (threshold: 95%). OOM risk imminent."
          runbook: "https://docs.butterfly.example.com/runbooks/critical-heap"
          
      - alert: FrequentGC
        expr: |
          rate(jvm_gc_pause_seconds_sum{job=~"butterfly-.*"}[5m]) > 0.1
        for: 10m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Frequent GC pauses for {{ $labels.application }}"
          description: "GC is consuming {{ $value | humanizePercentage }} of time."
```

### Kafka

```yaml
# kafka.rules.yml
groups:
  - name: butterfly.kafka
    interval: 30s
    rules:
      - alert: HighConsumerLag
        expr: |
          sum(kafka_consumer_lag_records{job=~"butterfly-.*"}) by (group, topic) > 10000
        for: 10m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "High consumer lag for {{ $labels.group }}"
          description: "Consumer group {{ $labels.group }} has {{ $value }} lag on topic {{ $labels.topic }}."
          runbook: "https://docs.butterfly.example.com/runbooks/consumer-lag"
          
      - alert: CriticalConsumerLag
        expr: |
          sum(kafka_consumer_lag_records{job=~"butterfly-.*"}) by (group, topic) > 100000
        for: 5m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "Critical consumer lag for {{ $labels.group }}"
          description: "Consumer group {{ $labels.group }} has {{ $value }} lag on topic {{ $labels.topic }}."
          runbook: "https://docs.butterfly.example.com/runbooks/critical-consumer-lag"
          
      - alert: ProducerErrors
        expr: |
          rate(kafka_producer_record_error_total{job=~"butterfly-.*"}[5m]) > 0
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Kafka producer errors in {{ $labels.application }}"
          description: "Producer is experiencing errors sending to {{ $labels.topic }}."
```

### Circuit Breaker

```yaml
# circuit-breaker.rules.yml
groups:
  - name: butterfly.circuitbreaker
    interval: 30s
    rules:
      - alert: CircuitBreakerOpen
        expr: |
          resilience4j_circuitbreaker_state{job="butterfly-nexus"} == 1
        for: 1m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Circuit breaker {{ $labels.name }} is OPEN"
          description: "Circuit breaker {{ $labels.name }} has opened due to failures."
          runbook: "https://docs.butterfly.example.com/runbooks/circuit-breaker"
          
      - alert: HighCircuitBreakerFailureRate
        expr: |
          resilience4j_circuitbreaker_failure_rate{job="butterfly-nexus"} > 50
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "High failure rate on {{ $labels.name }}"
          description: "Failure rate is {{ $value }}%."
```

### Database

```yaml
# database.rules.yml
groups:
  - name: butterfly.database
    interval: 30s
    rules:
      - alert: DatabaseConnectionPoolExhausted
        expr: |
          hikaricp_connections_pending{job=~"butterfly-.*"} > 0
        for: 5m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Connection pool exhausted for {{ $labels.application }}"
          description: "{{ $value }} connections pending in pool {{ $labels.pool }}."
          
      - alert: CassandraHighLatency
        expr: |
          histogram_quantile(0.99, 
            sum(rate(cassandra_session_request_duration_seconds_bucket{job=~"butterfly-.*"}[5m])) by (le)
          ) > 0.1
        for: 10m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "High Cassandra latency for {{ $labels.application }}"
          description: "P99 Cassandra latency is {{ $value | humanizeDuration }}."
```

---

## Alertmanager Configuration

```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m
  slack_api_url: 'https://hooks.slack.com/services/...'

route:
  group_by: ['alertname', 'application']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true
    - match:
        severity: critical
      receiver: 'slack-critical'
    - match:
        severity: warning
      receiver: 'slack-warning'
    - match:
        severity: info
      receiver: 'slack-info'

receivers:
  - name: 'default'
    slack_configs:
      - channel: '#butterfly-alerts'
        
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: '<pagerduty-service-key>'
        severity: '{{ .CommonLabels.severity }}'
        
  - name: 'slack-critical'
    slack_configs:
      - channel: '#butterfly-critical'
        color: 'danger'
        title: '🚨 CRITICAL: {{ .CommonLabels.alertname }}'
        text: '{{ .CommonAnnotations.description }}'
        
  - name: 'slack-warning'
    slack_configs:
      - channel: '#butterfly-alerts'
        color: 'warning'
        title: '⚠️ WARNING: {{ .CommonLabels.alertname }}'
        text: '{{ .CommonAnnotations.description }}'
        
  - name: 'slack-info'
    slack_configs:
      - channel: '#butterfly-info'
        color: 'good'
        title: 'ℹ️ INFO: {{ .CommonLabels.alertname }}'
        text: '{{ .CommonAnnotations.description }}'

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'application']
```

---

## Alert Testing

### Test Alert Rule

```bash
# Verify rule syntax
promtool check rules service-health.rules.yml

# Test rule expression
curl -G 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=up{job=~"butterfly-.*"} == 0'
```

### Fire Test Alert

```bash
# Create test alert
curl -X POST http://localhost:9093/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '[{
    "labels": {
      "alertname": "TestAlert",
      "severity": "warning",
      "application": "test"
    },
    "annotations": {
      "summary": "This is a test alert",
      "description": "Testing alerting pipeline"
    }
  }]'
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Monitoring Overview](README.md) | Monitoring strategy |
| [Metrics Catalog](metrics-catalog.md) | Available metrics |
| [Incident Response](../runbooks/incident-response.md) | Response procedures |

