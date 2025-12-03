# Connector Failures Runbook

## Overview

This runbook covers troubleshooting and remediation for data connector failures in the PERCEPTION ingestion pipeline. Connectors are responsible for acquiring data from various sources (RSS, APIs, webhooks, databases) and routing it through the processing pipeline.

## Triggered By

- `ConnectorFailureRate` - High failure rate on specific connector
- `TenantDlqRateWarning` - DLQ rate exceeds 1% for tenant
- `TenantDlqRateCritical` - DLQ rate exceeds 5% for tenant
- `SourceHealthDegraded` - Source-level health degradation
- `ConnectorCircuitBreakerOpen` - Circuit breaker triggered

## Symptoms

- Messages accumulating in DLQ
- Missing or delayed data in capsules
- Alerts on connector success/failure rates
- Tenant-specific data gaps

## Diagnostic Steps

### 1. Identify Affected Connectors

```bash
# Check connector success rates by route
curl -s "http://prometheus:9090/api/v1/query?query=sum%20by%20(routeId,routeType)%20(rate(ingestion_connector_success_total[5m]))"

# Check connector failure rates
curl -s "http://prometheus:9090/api/v1/query?query=sum%20by%20(routeId,routeType)%20(rate(ingestion_connector_failure_total[5m]))"

# Check DLQ rates by tenant
curl -s "http://prometheus:9090/api/v1/query?query=sum%20by%20(tenantId)%20(rate(ingestion_dlq_messages_total[5m]))"
```

### 2. Check Connector Health

```bash
# Get connector status from PERCEPTION API
curl -s http://perception:8081/api/v1/connectors/status

# Check specific route status
curl -s http://perception:8081/api/v1/routes/{routeId}/health
```

### 3. Review DLQ Messages

```bash
# List recent DLQ messages
curl -s http://perception:8081/api/v1/dlq/messages?limit=10

# Get DLQ message details
curl -s http://perception:8081/api/v1/dlq/messages/{messageId}

# Get failure reasons
curl -s "http://perception:8081/api/v1/dlq/messages?groupBy=errorType"
```

### 4. Check Source Availability

```bash
# For RSS sources
curl -I https://example.com/feed.xml

# For API sources
curl -s https://api.example.com/health

# DNS resolution
nslookup api.example.com
```

### 5. Review Logs

```bash
# Connector-specific logs
curl -s "http://loki:3100/loki/api/v1/query_range" \
  -d 'query={service="perception-acquisition", routeId="route-123"}' \
  -d 'limit=100'

# Errors only
curl -s "http://loki:3100/loki/api/v1/query_range" \
  -d 'query={service="perception-acquisition", routeId="route-123"} |= "ERROR"'
```

## Common Issues and Solutions

### 1. Source Unavailable

**Symptoms:** Timeout errors, connection refused

**Solution:**
```bash
# Check if source is reachable
curl -v https://source.example.com/api

# If external service down, no action needed - connector will retry
# If permanent, disable the route
curl -X PATCH http://perception:8081/api/v1/routes/{routeId} \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

### 2. Authentication Failures

**Symptoms:** 401/403 errors, "Unauthorized" in logs

**Solution:**
```bash
# Check credential status
curl -s http://perception:8081/api/v1/routes/{routeId}/credentials/status

# Rotate credentials
curl -X POST http://perception:8081/api/v1/routes/{routeId}/credentials/rotate \
  -H "Content-Type: application/json" \
  -d '{"apiKey": "new-key"}'
```

### 3. Rate Limiting

**Symptoms:** 429 errors, throttling messages in logs

**Solution:**
```bash
# Check current rate configuration
curl -s http://perception:8081/api/v1/routes/{routeId}/config | jq '.rateLimit'

# Reduce polling frequency
curl -X PATCH http://perception:8081/api/v1/routes/{routeId} \
  -H "Content-Type: application/json" \
  -d '{"pollingIntervalMs": 60000}'
```

### 4. Schema/Format Changes

**Symptoms:** Parse errors, validation failures

**Solution:**
```bash
# Check recent DLQ messages for schema errors
curl -s "http://perception:8081/api/v1/dlq/messages?errorType=PARSE_ERROR&limit=5"

# Get sample payload for analysis
curl -s http://perception:8081/api/v1/dlq/messages/{messageId}/payload

# Update route schema if source changed
curl -X PATCH http://perception:8081/api/v1/routes/{routeId}/schema \
  -H "Content-Type: application/json" \
  -d @new-schema.json
```

### 5. High DLQ Volume

**Symptoms:** DLQ growing faster than processing

**Solution:**
```bash
# Check DLQ depth
curl -s http://perception:8081/api/v1/dlq/stats

# Bulk replay after fixing issue
curl -X POST http://perception:8081/api/v1/dlq/replay \
  -H "Content-Type: application/json" \
  -d '{"routeId": "route-123", "limit": 1000}'

# If messages are unrecoverable, purge
curl -X DELETE http://perception:8081/api/v1/dlq/messages \
  -H "Content-Type: application/json" \
  -d '{"routeId": "route-123", "olderThan": "7d"}'
```

## Circuit Breaker Management

### Check Circuit Breaker State
```bash
curl -s http://perception:8081/actuator/circuitbreakers | \
  jq '.circuitBreakers | to_entries[] | select(.value.state != "CLOSED")'
```

### Force Reset Circuit Breaker
```bash
# Only if you're sure the issue is resolved
curl -X POST http://perception:8081/actuator/circuitbreakers/{name}/reset
```

## Tenant-Specific Issues

### Isolate Tenant Data
```bash
# Check tenant-specific metrics
curl -s "http://prometheus:9090/api/v1/query?query=sum%20by%20(routeId)%20(rate(ingestion_connector_failure_total{tenantId=\"tenant-123\"}[5m]))"

# Get tenant routes
curl -s http://perception:8081/api/v1/tenants/{tenantId}/routes
```

### Tenant Data Recovery
```bash
# Replay DLQ for specific tenant
curl -X POST http://perception:8081/api/v1/dlq/replay \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "tenant-123"}'

# Backfill missing data
curl -X POST http://perception:8081/api/v1/routes/{routeId}/backfill \
  -H "Content-Type: application/json" \
  -d '{"startTime": "2024-01-01T00:00:00Z", "endTime": "2024-01-02T00:00:00Z"}'
```

## Related Dashboards

- [Ingestion Tenant Health](https://grafana/d/ingestion-tenant-health)
- [Pipeline Health](https://grafana/d/pipeline-health)
- [DLQ Replay Dashboard](https://grafana/d/ingestion-dlq-replay)
- [Connector Overview](https://grafana/d/ingestion-overview)

## Escalation

| Issue Type | First Responder | Escalation |
|-----------|-----------------|------------|
| Single connector failure | On-call engineer | Data platform team |
| Tenant-wide failures | On-call engineer | Tenant success + Data platform |
| Multi-tenant failures | On-call engineer | Engineering lead + Data platform |

## Related Runbooks

- [DLQ Replay](./dlq-replay.md)
- [Common Issues](./common-issues.md)
- [SLO Breach](./slo-breach.md)
- [Incident Response](./incident-response.md)

