# BUTTERFLY Grafana Dashboards

> Dashboard templates and visualization best practices

**Last Updated**: 2025-12-03  
**Target Audience**: SREs, DevOps engineers

---

## Overview

This guide covers the standard Grafana dashboards for monitoring BUTTERFLY services.

---

## Dashboard Catalog

| Dashboard | ID | Purpose |
|-----------|-----|---------|
| BUTTERFLY Overview | `butterfly-overview` | Ecosystem health at a glance |
| Service Details | `butterfly-service-{name}` | Per-service deep dive |
| Kafka | `butterfly-kafka` | Messaging infrastructure |
| Databases | `butterfly-databases` | Data layer health |
| JVM | `butterfly-jvm` | JVM performance |
| SLO | `butterfly-slo` | Service level objectives |

---

## BUTTERFLY Overview Dashboard

### Panels

#### 1. Service Health Matrix

```json
{
  "type": "stat",
  "title": "Service Health",
  "targets": [
    {
      "expr": "up{job=~\"butterfly-.*\"}",
      "legendFormat": "{{application}}"
    }
  ],
  "options": {
    "colorMode": "background",
    "graphMode": "none",
    "textMode": "name"
  },
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"options": {"0": {"text": "DOWN", "color": "red"}}, "type": "value"},
        {"options": {"1": {"text": "UP", "color": "green"}}, "type": "value"}
      ]
    }
  }
}
```

#### 2. Request Rate

```json
{
  "type": "timeseries",
  "title": "Request Rate by Service",
  "targets": [
    {
      "expr": "sum(rate(http_server_requests_seconds_count[5m])) by (application)",
      "legendFormat": "{{application}}"
    }
  ],
  "options": {
    "legend": {"displayMode": "table", "placement": "right"}
  }
}
```

#### 3. Error Rate

```json
{
  "type": "timeseries",
  "title": "Error Rate by Service",
  "targets": [
    {
      "expr": "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) by (application) / sum(rate(http_server_requests_seconds_count[5m])) by (application) * 100",
      "legendFormat": "{{application}}"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percent",
      "thresholds": {
        "steps": [
          {"color": "green", "value": null},
          {"color": "yellow", "value": 0.1},
          {"color": "red", "value": 1}
        ]
      }
    }
  }
}
```

#### 4. P99 Latency

```json
{
  "type": "timeseries",
  "title": "P99 Latency by Service",
  "targets": [
    {
      "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))",
      "legendFormat": "{{application}}"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "s",
      "thresholds": {
        "steps": [
          {"color": "green", "value": null},
          {"color": "yellow", "value": 0.3},
          {"color": "red", "value": 0.5}
        ]
      }
    }
  }
}
```

---

## Service Details Dashboard

### Variables

```yaml
- name: service
  type: query
  query: "label_values(up{job=~\"butterfly-.*\"}, application)"
  
- name: instance
  type: query
  query: "label_values(up{application=\"$service\"}, instance)"
```

### Panels

#### Request Breakdown

```json
{
  "type": "timeseries",
  "title": "Requests by Endpoint",
  "targets": [
    {
      "expr": "sum(rate(http_server_requests_seconds_count{application=\"$service\"}[5m])) by (uri, method)",
      "legendFormat": "{{method}} {{uri}}"
    }
  ]
}
```

#### Latency Heatmap

```json
{
  "type": "heatmap",
  "title": "Request Latency Distribution",
  "targets": [
    {
      "expr": "sum(rate(http_server_requests_seconds_bucket{application=\"$service\"}[5m])) by (le)",
      "format": "heatmap",
      "legendFormat": "{{le}}"
    }
  ],
  "options": {
    "color": {"scheme": "Spectral"}
  }
}
```

#### Error Breakdown

```json
{
  "type": "table",
  "title": "Recent Errors",
  "targets": [
    {
      "expr": "sum(increase(http_server_requests_seconds_count{application=\"$service\", status=~\"5..\"}[1h])) by (uri, status, exception)",
      "instant": true,
      "format": "table"
    }
  ]
}
```

---

## JVM Dashboard

### Memory Panel

```json
{
  "type": "timeseries",
  "title": "JVM Heap Usage",
  "targets": [
    {
      "expr": "jvm_memory_used_bytes{application=\"$service\", area=\"heap\"}",
      "legendFormat": "Used"
    },
    {
      "expr": "jvm_memory_committed_bytes{application=\"$service\", area=\"heap\"}",
      "legendFormat": "Committed"
    },
    {
      "expr": "jvm_memory_max_bytes{application=\"$service\", area=\"heap\"}",
      "legendFormat": "Max"
    }
  ],
  "fieldConfig": {
    "defaults": {"unit": "bytes"}
  }
}
```

### GC Panel

```json
{
  "type": "timeseries",
  "title": "GC Pause Duration",
  "targets": [
    {
      "expr": "rate(jvm_gc_pause_seconds_sum{application=\"$service\"}[5m])",
      "legendFormat": "{{action}} ({{cause}})"
    }
  ],
  "fieldConfig": {
    "defaults": {"unit": "s"}
  }
}
```

### Thread Panel

```json
{
  "type": "gauge",
  "title": "Thread Count",
  "targets": [
    {
      "expr": "jvm_threads_live{application=\"$service\"}",
      "legendFormat": "Live"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "max": 1000,
      "thresholds": {
        "steps": [
          {"color": "green", "value": null},
          {"color": "yellow", "value": 500},
          {"color": "red", "value": 800}
        ]
      }
    }
  }
}
```

---

## Kafka Dashboard

### Consumer Lag

```json
{
  "type": "timeseries",
  "title": "Consumer Lag by Group",
  "targets": [
    {
      "expr": "sum(kafka_consumer_lag_records) by (group, topic)",
      "legendFormat": "{{group}} - {{topic}}"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "thresholds": {
        "steps": [
          {"color": "green", "value": null},
          {"color": "yellow", "value": 1000},
          {"color": "red", "value": 10000}
        ]
      }
    }
  }
}
```

### Throughput

```json
{
  "type": "timeseries",
  "title": "Message Throughput",
  "targets": [
    {
      "expr": "sum(rate(kafka_producer_records_sent_total[5m])) by (topic)",
      "legendFormat": "Produced: {{topic}}"
    },
    {
      "expr": "sum(rate(kafka_consumer_records_consumed_total[5m])) by (topic)",
      "legendFormat": "Consumed: {{topic}}"
    }
  ]
}
```

---

## SLO Dashboard

### Availability SLO

```json
{
  "type": "gauge",
  "title": "Availability (Target: 99.9%)",
  "targets": [
    {
      "expr": "(1 - (sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[30d])) / sum(rate(http_server_requests_seconds_count[30d])))) * 100"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "min": 99,
      "max": 100,
      "unit": "percent",
      "thresholds": {
        "steps": [
          {"color": "red", "value": null},
          {"color": "yellow", "value": 99.5},
          {"color": "green", "value": 99.9}
        ]
      }
    }
  }
}
```

### Error Budget

```json
{
  "type": "timeseries",
  "title": "Error Budget Remaining",
  "targets": [
    {
      "expr": "1 - ((sum(increase(http_server_requests_seconds_count{status=~\"5..\"}[30d])) / sum(increase(http_server_requests_seconds_count[30d]))) / 0.001)"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percentunit",
      "thresholds": {
        "steps": [
          {"color": "red", "value": null},
          {"color": "yellow", "value": 0.25},
          {"color": "green", "value": 0.5}
        ]
      }
    }
  }
}
```

---

## Importing Dashboards

### From File

```bash
# Import dashboard JSON
curl -X POST http://localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRAFANA_API_KEY" \
  -d @dashboard.json
```

### From ConfigMap (Kubernetes)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards
  labels:
    grafana_dashboard: "1"
data:
  butterfly-overview.json: |
    { ... dashboard JSON ... }
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Monitoring Overview](README.md) | Monitoring strategy |
| [Metrics Catalog](metrics-catalog.md) | Available metrics |
| [Alerting](alerting.md) | Alert configuration |

