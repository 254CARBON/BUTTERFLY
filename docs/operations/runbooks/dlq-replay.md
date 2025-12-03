# DLQ Replay Runbook

## Overview

This runbook provides procedures for investigating and replaying messages from the Dead Letter Queue (DLQ) in the PERCEPTION ingestion pipeline. The DLQ captures messages that failed processing for retry after the root cause is resolved.

## Triggered By

- `TenantDlqRateWarning` - DLQ rate exceeds 1% for tenant
- `TenantDlqRateCritical` - DLQ rate exceeds 5% for tenant
- `DlqDepthHigh` - DLQ depth exceeds threshold
- Manual investigation of data gaps

## Prerequisites

- Access to PERCEPTION API
- Access to Kafka consumer tools (optional, for low-level debugging)
- Grafana dashboard access

## Diagnostic Steps

### 1. Check DLQ Status

```bash
# Get overall DLQ statistics
curl -s http://perception:8081/api/v1/dlq/stats

# Expected response:
# {
#   "totalMessages": 1234,
#   "oldestMessage": "2024-01-15T10:30:00Z",
#   "byTenant": { "tenant-1": 500, "tenant-2": 734 },
#   "byRoute": { "route-a": 800, "route-b": 434 },
#   "byErrorType": { "PARSE_ERROR": 600, "TIMEOUT": 400, "VALIDATION": 234 }
# }
```

### 2. Analyze Failure Patterns

```bash
# Get recent failures grouped by error type
curl -s "http://perception:8081/api/v1/dlq/messages?groupBy=errorType&limit=100"

# Get failures for specific tenant
curl -s "http://perception:8081/api/v1/dlq/messages?tenantId=tenant-123&limit=50"

# Get failures for specific route
curl -s "http://perception:8081/api/v1/dlq/messages?routeId=route-456&limit=50"

# Get failures in time range
curl -s "http://perception:8081/api/v1/dlq/messages?startTime=2024-01-15T00:00:00Z&endTime=2024-01-15T12:00:00Z"
```

### 3. Inspect Specific Message

```bash
# Get message details
curl -s http://perception:8081/api/v1/dlq/messages/{messageId}

# Get message payload (for debugging)
curl -s http://perception:8081/api/v1/dlq/messages/{messageId}/payload

# Get processing history
curl -s http://perception:8081/api/v1/dlq/messages/{messageId}/history
```

## Common Error Types and Resolutions

### PARSE_ERROR
**Cause:** Source schema changed or malformed data

**Resolution:**
1. Compare payload with expected schema
2. Update route schema if source legitimately changed
3. Mark messages as unrecoverable if data is corrupt

### TIMEOUT
**Cause:** Downstream service or external dependency slow/unavailable

**Resolution:**
1. Check downstream service health
2. Wait for recovery, then replay
3. Consider increasing timeout thresholds

### VALIDATION
**Cause:** Business rule validation failure

**Resolution:**
1. Review validation rules
2. Check if rules are too strict for edge cases
3. Update rules or mark messages as business-invalid

### RATE_LIMITED
**Cause:** Downstream rate limit exceeded

**Resolution:**
1. Wait for rate limit window to reset
2. Replay in smaller batches
3. Adjust source polling frequency

### DUPLICATE
**Cause:** Message already processed (idempotency check)

**Resolution:**
1. Verify message was actually processed
2. If duplicate is legitimate, discard
3. If false positive, check idempotency key generation

## Replay Procedures

### Single Message Replay

```bash
# Replay single message
curl -X POST http://perception:8081/api/v1/dlq/messages/{messageId}/replay

# Replay with force (skip some validations)
curl -X POST http://perception:8081/api/v1/dlq/messages/{messageId}/replay?force=true
```

### Batch Replay

```bash
# Replay all messages for a route (after fixing issue)
curl -X POST http://perception:8081/api/v1/dlq/replay \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "route-123",
    "limit": 1000,
    "maxRetries": 3
  }'

# Replay for specific tenant
curl -X POST http://perception:8081/api/v1/dlq/replay \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-456",
    "limit": 500
  }'

# Replay by error type (after fix deployed)
curl -X POST http://perception:8081/api/v1/dlq/replay \
  -H "Content-Type: application/json" \
  -d '{
    "errorType": "TIMEOUT",
    "startTime": "2024-01-15T00:00:00Z",
    "endTime": "2024-01-15T06:00:00Z"
  }'
```

### Scheduled/Throttled Replay

For large volumes, use throttled replay to avoid overwhelming downstream services:

```bash
# Start throttled replay job
curl -X POST http://perception:8081/api/v1/dlq/replay/scheduled \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "route-123",
    "messagesPerMinute": 100,
    "maxDuration": "PT2H"
  }'

# Check replay job status
curl -s http://perception:8081/api/v1/dlq/replay/jobs/{jobId}

# Cancel replay job
curl -X DELETE http://perception:8081/api/v1/dlq/replay/jobs/{jobId}
```

## Purging Unrecoverable Messages

### Mark as Unrecoverable

```bash
# Mark single message as unrecoverable
curl -X PATCH http://perception:8081/api/v1/dlq/messages/{messageId} \
  -H "Content-Type: application/json" \
  -d '{"status": "UNRECOVERABLE", "reason": "Corrupt data from source"}'
```

### Bulk Purge

```bash
# Purge old messages (after review)
curl -X DELETE http://perception:8081/api/v1/dlq/messages \
  -H "Content-Type: application/json" \
  -d '{
    "olderThan": "30d",
    "status": "UNRECOVERABLE"
  }'

# Purge by route (deprecated route)
curl -X DELETE http://perception:8081/api/v1/dlq/messages \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "deprecated-route",
    "confirm": true
  }'
```

## Monitoring Replay Progress

### Via API

```bash
# Get active replay jobs
curl -s http://perception:8081/api/v1/dlq/replay/jobs?status=RUNNING

# Get job details
curl -s http://perception:8081/api/v1/dlq/replay/jobs/{jobId}
```

### Via Metrics

```promql
# Replay rate
rate(ingestion_dlq_replay_total[5m])

# Replay success rate
sum(rate(ingestion_dlq_replay_total{outcome="success"}[5m])) /
sum(rate(ingestion_dlq_replay_total[5m]))

# DLQ depth trend
ingestion_dlq_depth_total
```

## Best Practices

### Before Replay
1. **Identify root cause** - Don't replay until the issue is fixed
2. **Test with single message** - Replay one message first to verify fix
3. **Notify affected tenants** - If significant delay, communicate

### During Replay
1. **Monitor success rate** - Stop if failures continue
2. **Watch downstream load** - Use throttling for large volumes
3. **Track progress** - Monitor DLQ depth decreasing

### After Replay
1. **Verify data integrity** - Spot check processed data
2. **Update runbook** - Document new failure mode if applicable
3. **Consider automation** - Auto-replay for transient errors

## Related Dashboards

- [DLQ Replay Dashboard](https://grafana/d/ingestion-dlq-replay)
- [Ingestion Tenant Health](https://grafana/d/ingestion-tenant-health)
- [Pipeline Health](https://grafana/d/pipeline-health)

## Escalation

| DLQ Depth | Action |
|-----------|--------|
| < 1000 | On-call handles |
| 1000-10000 | Escalate to data platform |
| > 10000 | Data platform + engineering lead |

## Related Runbooks

- [Connector Failures](./connector-failures.md)
- [Common Issues](./common-issues.md)
- [SLO Breach](./slo-breach.md)

