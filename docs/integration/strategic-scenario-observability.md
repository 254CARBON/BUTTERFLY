# Strategic Scenario Observability Guide

## Overview

This document describes how to observe and trace the EURUSD Market Shock strategic scenario as it flows through the BUTTERFLY ecosystem:

```
PERCEPTION → CAPSULE → ODYSSEY → PLATO → NEXUS
```

## Correlation ID Propagation

All services in the strategic scenario propagate a `correlationId` (also known as `X-Correlation-ID`) to enable end-to-end tracing.

### Where Correlation IDs Appear

| Service     | Location                                           | Header Name        |
|------------|----------------------------------------------------|--------------------|
| PERCEPTION | Kafka headers, HTTP request/response               | `X-Correlation-ID` |
| CAPSULE    | Kafka headers, HTTP headers, Cassandra metadata    | `correlationId`    |
| ODYSSEY    | Kafka headers, HTTP headers, JanusGraph properties | `X-Correlation-ID` |
| PLATO      | HTTP headers, response body                        | `X-Correlation-ID` |
| NEXUS      | HTTP headers, response body, all client calls      | `X-Correlation-ID` |

### Example Correlation ID Format

```
nexus-strategic-a1b2c3d4-1701619200
golden-path-1701619200-abc123
```

## Metrics

### PERCEPTION Metrics

| Metric Name                                  | Description                              |
|---------------------------------------------|------------------------------------------|
| `perception.strategic.events.published`      | Count of market shock events published   |
| `perception.market.shock.detected`           | Count of market shocks detected          |

### CAPSULE Metrics

| Metric Name                                  | Description                              |
|---------------------------------------------|------------------------------------------|
| `capsule.strategic.events.consumed`          | Count of perception events consumed      |
| `capsule.strategic.capsules.created`         | Count of capsules created from events    |
| `capsule.strategic.processing.duration`      | Processing time for strategic events     |
| `capsule.strategic.errors`                   | Count of processing errors               |

### ODYSSEY Metrics

| Metric Name                                  | Description                              |
|---------------------------------------------|------------------------------------------|
| `odyssey.strategic.paths.generated`          | Count of path projections generated      |
| `odyssey.strategic.paths.published`          | Count of paths published to Kafka        |
| `odyssey.reflex.events.processed`            | Count of reflex events processed         |
| `odyssey.reflex.actions.triggered`           | Count of reflex actions triggered        |

### PLATO Metrics

| Metric Name                                  | Description                              |
|---------------------------------------------|------------------------------------------|
| `plato.strategic.evaluations`                | Count of strategic evaluations           |
| `plato.strategic.plans.generated`            | Count of plans generated                 |
| `plato.strategic.evaluation.duration`        | Evaluation processing time               |

### NEXUS Metrics

| Metric Name                                  | Description                              |
|---------------------------------------------|------------------------------------------|
| `nexus.strategic.orchestrations`             | Count of strategic orchestrations        |
| `nexus.strategic.orchestration.duration`     | End-to-end orchestration time            |
| `nexus.strategic.errors`                     | Count of orchestration errors            |
| `nexus.plato.client.requests`                | Count of PLATO client requests           |
| `nexus.plato.client.duration`                | PLATO client call duration               |

## Logging

### Log Format

All services use structured logging with the following MDC fields:

```
correlationId: The end-to-end correlation ID
tenantId: The tenant identifier (if applicable)
nodeId: The RIM node ID being processed
```

### Example Log Queries

#### Loki/Grafana Queries

```logql
# Find all logs for a specific correlation ID
{job="butterfly"} |= "correlationId=golden-path-1701619200-abc123"

# Find market shock events in PERCEPTION
{job="perception"} |= "MarketShockEvent" |= "EURUSD"

# Find capsule creation in CAPSULE
{job="capsule"} |= "Created CAPSULE from strategic event"

# Find path projections in ODYSSEY
{job="odyssey"} |= "path projection" |= "EURUSD"

# Find governance evaluations in PLATO
{job="plato"} |= "Strategic governance evaluation"

# Find strategic synthesis in NEXUS
{job="nexus"} |= "Strategic scenario orchestration"
```

#### Elasticsearch Queries

```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "correlationId": "golden-path-1701619200-abc123" } }
      ]
    }
  },
  "sort": [{ "@timestamp": "asc" }]
}
```

## Tracing

### OpenTelemetry Spans

The strategic scenario generates the following spans:

| Span Name                             | Service    | Description                          |
|--------------------------------------|------------|--------------------------------------|
| `perception.market_shock.detect`      | PERCEPTION | Market shock detection               |
| `perception.event.publish`            | PERCEPTION | Event publishing to Kafka            |
| `capsule.strategic.consume`           | CAPSULE    | Consuming perception events          |
| `capsule.strategic.create`            | CAPSULE    | Creating capsule from event          |
| `odyssey.path.generate`               | ODYSSEY    | Generating path projections          |
| `odyssey.path.publish`                | ODYSSEY    | Publishing paths to Kafka            |
| `plato.strategic.evaluate`            | PLATO      | Strategic governance evaluation      |
| `nexus.strategic.orchestrate`         | NEXUS      | End-to-end orchestration             |
| `nexus.client.perception`             | NEXUS      | PERCEPTION client call               |
| `nexus.client.capsule`                | NEXUS      | CAPSULE client call                  |
| `nexus.client.odyssey`                | NEXUS      | ODYSSEY client call                  |
| `nexus.client.plato`                  | NEXUS      | PLATO client call                    |

### Jaeger/Tempo Query

To view the complete trace for a strategic scenario:

1. Open Jaeger/Tempo UI
2. Search by correlation ID tag: `correlationId=<your-correlation-id>`
3. Or search by operation name: `nexus.strategic.orchestrate`

## Kafka Topics

### Topic Flow

```
perception.events    → CAPSULE consumes market shock events
capsule.snapshots    → ODYSSEY consumes capsule snapshots
odyssey.paths        → Path projections for downstream consumers
rim.fast-path        → High-frequency RIM updates (optional fast path)
```

### Monitoring Kafka

```bash
# List consumer groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Check consumer lag for strategic consumer
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group capsule-strategic-consumer

# View messages on perception.events
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic perception.events --from-beginning --max-messages 10
```

## Grafana Dashboards

### Recommended Dashboard Panels

#### 1. Strategic Scenario Overview

- **Panel**: Total orchestrations (counter)
- **Query**: `sum(rate(nexus_strategic_orchestrations_total[5m]))`

#### 2. End-to-End Latency

- **Panel**: Orchestration duration histogram
- **Query**: `histogram_quantile(0.95, rate(nexus_strategic_orchestration_duration_seconds_bucket[5m]))`

#### 3. Service Health

- **Panel**: Service availability by correlation check
- **Queries**:
  - `up{job="perception"}`
  - `up{job="capsule"}`
  - `up{job="odyssey"}`
  - `up{job="plato"}`
  - `up{job="nexus"}`

#### 4. Event Flow

- **Panel**: Events processed per stage
- **Queries**:
  - `rate(perception_strategic_events_published_total[5m])`
  - `rate(capsule_strategic_capsules_created_total[5m])`
  - `rate(odyssey_strategic_paths_generated_total[5m])`
  - `rate(plato_strategic_plans_generated_total[5m])`

#### 5. Error Rate

- **Panel**: Error rates by service
- **Query**: `sum by (service) (rate(butterfly_errors_total{scenario="strategic"}[5m]))`

### Dashboard JSON Template

See `/docs/operations/monitoring/dashboards/strategic-scenario-dashboard.json` for a pre-built Grafana dashboard.

## Alerting

### Recommended Alerts

#### High Latency Alert

```yaml
groups:
  - name: strategic-scenario
    rules:
      - alert: StrategicScenarioHighLatency
        expr: histogram_quantile(0.95, rate(nexus_strategic_orchestration_duration_seconds_bucket[5m])) > 30
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: Strategic scenario latency exceeds 30s at p95
          description: The EURUSD market shock scenario is taking longer than expected
```

#### Error Rate Alert

```yaml
      - alert: StrategicScenarioErrors
        expr: rate(nexus_strategic_errors_total[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: Strategic scenario error rate is elevated
          description: More than 10% of strategic orchestrations are failing
```

#### Service Unavailable Alert

```yaml
      - alert: StrategicServiceDown
        expr: up{job=~"perception|capsule|odyssey|plato|nexus"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.job }} service is down"
          description: Required service for strategic scenario is unavailable
```

## Manual Debugging

### Step-by-Step Trace

To manually trace a strategic scenario:

1. **Generate a correlation ID**:
   ```bash
   CORRELATION_ID="debug-$(date +%s)-$(uuidgen | cut -d'-' -f1)"
   echo "Using correlation ID: $CORRELATION_ID"
   ```

2. **Check PERCEPTION**:
   ```bash
   curl -s "http://localhost:8081/api/v1/rim/nodes/rim:entity:finance:EURUSD/state" \
     -H "X-Correlation-ID: $CORRELATION_ID" | jq .
   ```

3. **Check CAPSULE history**:
   ```bash
   curl -s "http://localhost:8082/api/v1/history?scopeId=rim:entity:finance:EURUSD" \
     -H "X-Correlation-ID: $CORRELATION_ID" | jq .
   ```

4. **Check ODYSSEY paths**:
   ```bash
   curl -s "http://localhost:8083/api/v1/strategic/paths/rim:entity:finance:EURUSD" \
     -H "X-Correlation-ID: $CORRELATION_ID" | jq .
   ```

5. **Check PLATO governance**:
   ```bash
   curl -s "http://localhost:8084/api/v1/strategic/governance/evaluate/rim:entity:finance:EURUSD?stressLevel=0.85&regime=CRISIS" \
     -H "X-Correlation-ID: $CORRELATION_ID" | jq .
   ```

6. **Run full NEXUS orchestration**:
   ```bash
   curl -s "http://localhost:8085/api/v1/synthesis/strategic/rim:entity:finance:EURUSD" \
     -H "X-Correlation-ID: $CORRELATION_ID" | jq .
   ```

7. **Search logs for correlation ID**:
   ```bash
   grep -r "$CORRELATION_ID" /var/log/butterfly/ 2>/dev/null || \
     docker logs perception 2>&1 | grep "$CORRELATION_ID"
   ```

## Health Endpoints

| Service    | Health Endpoint                                      |
|------------|-----------------------------------------------------|
| PERCEPTION | `GET /actuator/health`                              |
| CAPSULE    | `GET /actuator/health`                              |
| ODYSSEY    | `GET /actuator/health`                              |
| PLATO      | `GET /api/v1/strategic/governance/health`           |
| NEXUS      | `GET /api/v1/synthesis/strategic/health`            |

## Related Documentation

- [Strategic Scenario Definition](./strategic-scenario.md)
- [BUTTERFLY Architecture Overview](../architecture/overview.md)
- [Monitoring Guide](../operations/monitoring/README.md)
- [Alert Configuration](../operations/monitoring/alerts/README.md)

