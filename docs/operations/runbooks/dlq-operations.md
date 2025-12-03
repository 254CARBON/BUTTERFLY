# Dead Letter Queue (DLQ) Operations Runbook

> Procedures for managing, investigating, and replaying dead letter queue messages

**Last Updated**: 2025-12-03  
**Target Audience**: SREs, Platform engineers, On-call responders  
**Related Documents**: [DLQ Strategy ADR](../../adr/0004-dead-letter-queue-strategy.md) | [Ingestion DLQ Replay](../../../PERCEPTION/ingestion-dlq-replay/README.md)

---

## Overview

BUTTERFLY uses topic-based Dead Letter Queues (DLQs) to handle message processing failures. Each topic has a corresponding DLQ topic with the naming pattern `{original-topic}.dlq`.

### DLQ Architecture

```
┌─────────────────┐     Success     ┌─────────────────┐
│  Source Topic   │ ───────────────▶│    Consumer     │
│  (e.g., rim.*)  │                 │   Processing    │
└────────┬────────┘                 └─────────────────┘
         │
         │ Failure (after retries)
         ▼
┌─────────────────┐                 ┌─────────────────┐
│   DLQ Topic     │ ◀───────────────│   DlqPublisher  │
│ (e.g., rim.*.dlq)│                 │                 │
└─────────────────┘                 └─────────────────┘
```

---

## DLQ Topics

| Original Topic | DLQ Topic | Service | Alert Threshold |
|----------------|-----------|---------|-----------------|
| `rim.fast-path` | `rim.fast-path.dlq` | PERCEPTION | > 100/hour |
| `acquisition.errors.http` | `acquisition.errors.http.dlq` | PERCEPTION | > 50/hour |
| `capsule.snapshots` | `capsule.snapshots.dlq` | CAPSULE | > 10/hour |
| `odyssey.paths` | `odyssey.paths.dlq` | ODYSSEY | > 20/hour |
| `plato.specs` | `plato.specs.dlq` | PLATO | > 5/hour |

---

## Failure Categories

Messages in DLQs are categorized by failure type to guide remediation:

| Category | Retry Strategy | Auto-Replay | Manual Action Required |
|----------|---------------|-------------|------------------------|
| `DESERIALIZATION` | Never retry | No | Fix producer schema |
| `VALIDATION` | Never retry | No | Fix data at source |
| `DOWNSTREAM_FAILURE` | Retry when healthy | Yes | Wait for dependency |
| `PERSISTENCE` | Retry with backoff | Yes | Check database health |
| `UNKNOWN` | Manual review | No | Investigate root cause |

---

## DLQ Record Structure

Each DLQ message contains metadata for investigation:

```json
{
  "id": "dlq-uuid-12345",
  "originalTopic": "rim.fast-path",
  "partition": 3,
  "offset": 1234567,
  "key": "tenant-123:user-456",
  "payload": "{...original message...}",
  "headers": {"X-Tenant-Id": "tenant-123"},
  "originalTimestamp": "2025-12-03T14:30:00Z",
  "dlqTimestamp": "2025-12-03T14:30:05Z",
  "errorType": "java.io.IOException",
  "errorMessage": "Connection refused",
  "stackTrace": "...(truncated)...",
  "consumerGroupId": "perception-consumers",
  "sourceService": "perception-api",
  "retryCount": 3,
  "status": "PENDING",
  "failureCategory": "DOWNSTREAM_FAILURE"
}
```

---

## Monitoring DLQs

### Key Metrics

```promql
# DLQ message rate (should be near zero)
rate(dlq_messages_total[5m])

# DLQ backlog size
kafka_consumer_lag{topic=~".*\\.dlq"}

# Retry success rate
rate(dlq_replay_success_total[1h]) / rate(dlq_replay_attempts_total[1h])

# Messages by failure category
sum by (category) (dlq_messages_total)
```

### Alerts

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| DLQBacklogHigh | lag > 1000 | Warning | Investigate and replay |
| DLQRateSpike | rate > 100/min | Critical | Check source service |
| DLQRetryFailures | success_rate < 0.5 | Warning | Check downstream health |

---

## Investigation Procedures

### Step 1: Identify the Problem

```bash
# Check DLQ message count
kubectl exec -n butterfly kafka-0 -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group dlq-processor

# View recent DLQ messages
kubectl exec -n butterfly kafka-0 -- kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic rim.fast-path.dlq \
  --from-beginning \
  --max-messages 10 \
  --property print.timestamp=true \
  --property print.key=true
```

### Step 2: Categorize Failures

```bash
# Group messages by failure category (using jq)
kubectl exec -n butterfly kafka-0 -- kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic rim.fast-path.dlq \
  --from-beginning \
  --max-messages 100 | jq -s 'group_by(.failureCategory) | map({category: .[0].failureCategory, count: length})'
```

### Step 3: Check Root Cause by Category

#### DOWNSTREAM_FAILURE
```bash
# Check downstream service health
kubectl exec -n butterfly -l app=perception -- \
  curl -s http://localhost:8080/actuator/health | jq

# Check circuit breaker status
kubectl exec -n butterfly -l app=perception -- \
  curl -s http://localhost:8080/actuator/circuitbreakers | jq
```

#### PERSISTENCE
```bash
# Check database connectivity
kubectl exec -n butterfly -l app=capsule -- \
  curl -s http://localhost:8080/actuator/health/cassandra | jq

# Check for database errors in logs
kubectl logs -n butterfly -l app=capsule --tail=100 | grep -i "cassandra\|database\|connection"
```

#### DESERIALIZATION / VALIDATION
```bash
# These require fixing at the source - check producer logs
kubectl logs -n butterfly -l app=perception --tail=100 | grep -i "serialization\|schema"

# Check Schema Registry compatibility
kubectl exec -n butterfly schema-registry-0 -- \
  curl -s http://localhost:8081/subjects/rim.fast-path-value/versions | jq
```

---

## Replay Procedures

### Prerequisites

Before replaying DLQ messages:

1. **Verify downstream health** - Ensure the cause of failure is resolved
2. **Check tenant isolation** - Confirm tenant context is preserved
3. **Get approval** - Document the replay in incident ticket
4. **Dry-run first** - Always preview what will be replayed

### Using the DLQ Replay Tool

The `ingestion-dlq-replay` utility handles replay with safety guards:

```bash
cd /home/m/BUTTERFLY/apps/PERCEPTION/ingestion-dlq-replay

# 1. Always dry-run first
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=acquisition.errors.http.dlq \
  --dry-run

# 2. Replay to shadow topic for verification
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=acquisition.errors.http.dlq \
  --target=SHADOW \
  --ticket=INC-12345 \
  --reason="Database recovered after maintenance"

# 3. If shadow replay succeeds, replay to primary
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=acquisition.errors.http.dlq \
  --target=RAW \
  --ticket=INC-12345 \
  --reason="Database recovered after maintenance"
```

### Replay Options

| Option | Description | Example |
|--------|-------------|---------|
| `--topic` | DLQ topic to replay from | `rim.fast-path.dlq` |
| `--target` | Destination: RAW, SHADOW, or specific topic | `--target=SHADOW` |
| `--dry-run` | Preview without sending | `--dry-run` |
| `--filter-tenant` | Only replay for specific tenant | `--filter-tenant=tenant-123` |
| `--filter-category` | Only replay specific failure type | `--filter-category=DOWNSTREAM_FAILURE` |
| `--max-messages` | Limit messages to replay | `--max-messages=1000` |
| `--rate-limit` | Messages per second | `--rate-limit=100` |
| `--ticket` | Required: incident ticket reference | `--ticket=INC-12345` |
| `--reason` | Required: reason for replay | `--reason="Service restored"` |

### Filtering Examples

```bash
# Replay only tenant-specific messages
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=rim.fast-path.dlq \
  --target=RAW \
  --filter-tenant=tenant-123 \
  --ticket=INC-12345

# Replay only DOWNSTREAM_FAILURE messages
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=rim.fast-path.dlq \
  --target=RAW \
  --filter-category=DOWNSTREAM_FAILURE \
  --ticket=INC-12345

# Replay with rate limiting
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=rim.fast-path.dlq \
  --target=RAW \
  --rate-limit=50 \
  --ticket=INC-12345
```

---

## When to Replay vs. Skip

### Replay When:

- ✅ Downstream service was temporarily unavailable (DOWNSTREAM_FAILURE)
- ✅ Database had transient connectivity issues (PERSISTENCE)
- ✅ Network partition has been resolved
- ✅ Circuit breaker has closed and service is healthy

### Skip (Discard) When:

- ❌ Messages have invalid schema (DESERIALIZATION) - fix producer
- ❌ Messages fail business validation (VALIDATION) - fix at source
- ❌ Messages are duplicate and would cause data inconsistency
- ❌ Replay window exceeded (data is stale)
- ❌ Tenant has been deprovisioned

### Mark as Resolved Without Replay

```bash
# Acknowledge messages without replaying
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=rim.fast-path.dlq \
  --acknowledge-only \
  --filter-category=VALIDATION \
  --ticket=INC-12345 \
  --reason="Invalid messages from deprecated API version"
```

---

## Tenant Isolation Verification

DLQ replay **must** preserve tenant isolation. Before replaying:

```bash
# 1. Verify tenant headers are present
kubectl exec -n butterfly kafka-0 -- kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic rim.fast-path.dlq \
  --from-beginning \
  --max-messages 5 \
  --property print.headers=true | grep "X-Tenant-Id"

# 2. If replaying for specific tenant, verify filtering works
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=rim.fast-path.dlq \
  --dry-run \
  --filter-tenant=tenant-123 | grep "tenant-123"
```

---

## Rate Limiting Guidance

Choose rate limits based on downstream capacity:

| Scenario | Recommended Rate | Rationale |
|----------|-----------------|-----------|
| Normal replay | 100/sec | Default safe rate |
| Large backlog (> 10k) | 500/sec | Faster catchup |
| Degraded downstream | 10/sec | Gentle load |
| Business-critical data | 50/sec | Careful processing |

---

## Audit Trail

All DLQ operations are logged for compliance:

```bash
# View replay audit log
kubectl logs -n butterfly -l app=dlq-replay --tail=100

# Audit entries include:
# - Timestamp
# - Operator
# - Ticket reference
# - Messages replayed
# - Source/target topics
# - Filters applied
```

### Required Documentation

For any DLQ replay operation, document:

1. Incident ticket number
2. Root cause of DLQ accumulation
3. Verification of downstream health
4. Number of messages replayed
5. Any messages skipped and why

---

## Troubleshooting

### Replay Fails to Start

```bash
# Check DLQ replay service health
kubectl logs -n butterfly -l app=dlq-replay

# Verify Kafka connectivity
kubectl exec -n butterfly -l app=dlq-replay -- \
  kafka-broker-api-versions.sh --bootstrap-server kafka:9092
```

### Messages Keep Failing After Replay

```bash
# Check if root cause is truly resolved
kubectl exec -n butterfly -l app=perception -- \
  curl -s http://localhost:8080/actuator/health | jq '.components'

# Check for new error patterns in consumer logs
kubectl logs -n butterfly -l app=perception --tail=50 | grep -i error
```

### High DLQ Backlog Won't Clear

```bash
# Check consumer lag
kubectl exec -n butterfly kafka-0 -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group perception-dlq-consumer

# Consider increasing replay rate or parallelism
java -jar target/ingestion-dlq-replay-*.jar \
  --topic=rim.fast-path.dlq \
  --target=RAW \
  --rate-limit=500 \
  --ticket=INC-12345
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [DLQ Strategy ADR](../../adr/0004-dead-letter-queue-strategy.md) | Architecture decision |
| [Ingestion DLQ Replay](../../../PERCEPTION/ingestion-dlq-replay/README.md) | Replay tool docs |
| [Disaster Recovery](disaster-recovery.md) | DR procedures |
| [Incident Response](incident-response.md) | Incident handling |

