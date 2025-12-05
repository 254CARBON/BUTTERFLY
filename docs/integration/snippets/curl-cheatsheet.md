# PERCEPTION API - cURL Cheatsheet

Quick reference for common PERCEPTION API operations using cURL.

## Setup

```bash
# Set environment variables
export PERCEPTION_URL="http://localhost:8080"
export PERCEPTION_API_KEY="your-api-key-here"

# Get JWT token (if using API key auth)
export JWT=$(curl -s -X POST "$PERCEPTION_URL/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d "{\"apiKey\": \"$PERCEPTION_API_KEY\"}" | jq -r '.data.token')

# Verify token
echo "Token: ${JWT:0:20}..."
```

---

## Authentication

### Exchange API Key for JWT

```bash
curl -X POST "$PERCEPTION_URL/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d "{\"apiKey\": \"$PERCEPTION_API_KEY\"}"
```

---

## Acquisition

### List Sources

```bash
curl -X GET "$PERCEPTION_URL/api/v1/acquisition/sources" \
  -H "Authorization: Bearer $JWT" \
  -H "X-Request-ID: $(uuidgen)"
```

### Register RSS Source

```bash
curl -X POST "$PERCEPTION_URL/api/v1/acquisition/sources" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: $(uuidgen)" \
  -d '{
    "name": "Tech News Feed",
    "type": "RSS_FEED",
    "endpoint": "https://example.com/rss/tech.xml",
    "schedule": "0 */30 * * * *",
    "tenantId": "global",
    "rateLimit": 60
  }'
```

### Trigger Manual Acquisition

```bash
curl -X POST "$PERCEPTION_URL/api/v1/acquisition/sources/{source_id}/trigger" \
  -H "Authorization: Bearer $JWT" \
  -H "X-Request-ID: $(uuidgen)"
```

### List Ingestion Routes

```bash
curl -X GET "$PERCEPTION_URL/api/v1/acquisition/routes" \
  -H "Authorization: Bearer $JWT"
```

---

## Intelligence

### List Events

```bash
curl -X GET "$PERCEPTION_URL/api/v1/intelligence/events?limit=20" \
  -H "Authorization: Bearer $JWT"
```

### Get Event Details

```bash
curl -X GET "$PERCEPTION_URL/api/v1/intelligence/events/{event_id}" \
  -H "Authorization: Bearer $JWT"
```

### Get Event Scenarios

```bash
curl -X GET "$PERCEPTION_URL/api/v1/intelligence/events/{event_id}/scenarios" \
  -H "Authorization: Bearer $JWT"
```

### Submit Scenario Feedback

```bash
curl -X POST "$PERCEPTION_URL/api/v1/intelligence/scenarios/{scenario_id}/feedback" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "feedback": "Scenario executed successfully with positive outcomes",
    "outcome": "SUCCESS",
    "notes": "All stakeholders notified within 30 minutes"
  }'
```

---

## Signals

### Get Detected Signals

```bash
curl -X GET "$PERCEPTION_URL/api/v1/signals/detected?hoursBack=24" \
  -H "Authorization: Bearer $JWT"
```

### Track Event Horizon

```bash
curl -X POST "$PERCEPTION_URL/api/v1/signals/horizons?topic=synthetic%20fuel%20alternatives" \
  -H "Authorization: Bearer $JWT"
```

### Get Signal Clusters

```bash
curl -X GET "$PERCEPTION_URL/api/v1/signals/clusters?timeWindowHours=24&minClusterSize=2" \
  -H "Authorization: Bearer $JWT"
```

### Get Co-occurrence Insights

```bash
curl -X GET "$PERCEPTION_URL/api/v1/signals/cooccurrence?topicThreshold=0.5" \
  -H "Authorization: Bearer $JWT"
```

### List Detectors

```bash
curl -X GET "$PERCEPTION_URL/api/v1/signals/detectors" \
  -H "Authorization: Bearer $JWT"
```

---

## RIM (Reality Integration Mesh)

### List RIM Nodes

```bash
curl -X GET "$PERCEPTION_URL/api/v1/rim/nodes?scope=global&window=PT5M" \
  -H "Authorization: Bearer $JWT"
```

### Register RIM Node

```bash
curl -X POST "$PERCEPTION_URL/api/v1/rim/nodes" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "nodeId": "rim:entity:finance:EURUSD",
    "displayName": "EUR/USD Currency Pair",
    "nodeType": "entity",
    "namespace": "finance",
    "region": "global",
    "domain": "fx",
    "status": "ACTIVE"
  }'
```

### Get Mesh Snapshot

```bash
curl -X GET "$PERCEPTION_URL/api/v1/rim/mesh?scope=global&window=PT5M" \
  -H "Authorization: Bearer $JWT"
```

### Get Ignorance Surface

```bash
curl -X GET "$PERCEPTION_URL/api/v1/rim/ignorance-surface" \
  -H "Authorization: Bearer $JWT"
```

### Detect Anomalies

```bash
curl -X GET "$PERCEPTION_URL/api/v1/rim/anomalies?minSeverity=0.5" \
  -H "Authorization: Bearer $JWT"
```

### Get Node Capsules

```bash
curl -X GET "$PERCEPTION_URL/api/v1/rim/nodes/{node_id}/capsules" \
  -H "Authorization: Bearer $JWT"
```

---

## Integrity

### Get Content Trust Score

```bash
curl -X GET "$PERCEPTION_URL/api/v1/integrity/trust/content/{content_id}" \
  -H "Authorization: Bearer $JWT"
```

### Get Source Trust Score

```bash
curl -X GET "$PERCEPTION_URL/api/v1/integrity/trust/source/{source_id}" \
  -H "Authorization: Bearer $JWT"
```

### Get Storyline

```bash
curl -X GET "$PERCEPTION_URL/api/v1/integrity/storylines/{storyline_id}" \
  -H "Authorization: Bearer $JWT"
```

---

## Stream A (Reasoning, Knowledge, Predictive)

### Apply Reasoning Rules

```bash
curl -X POST "$PERCEPTION_URL/api/v1/reasoning/apply?interpretation=Event%20occurred%20before%20its%20cause&initialPlausibility=0.8" \
  -H "Authorization: Bearer $JWT"
```

### Check Plausibility

```bash
curl -X POST "$PERCEPTION_URL/api/v1/reasoning/check-plausibility?interpretation=Market%20rose%20after%20positive%20earnings" \
  -H "Authorization: Bearer $JWT"
```

### Enrich Entity Knowledge

```bash
curl -X POST "$PERCEPTION_URL/api/v1/knowledge/enrich?entityId=ACME&entityType=organization" \
  -H "Authorization: Bearer $JWT"
```

### Generate Prediction

```bash
curl -X POST "$PERCEPTION_URL/api/v1/predictive/predict?target=cpu-utilization&currentValue=0.65&horizonHours=24" \
  -H "Authorization: Bearer $JWT"
```

### Get Early Warnings

```bash
curl -X GET "$PERCEPTION_URL/api/v1/predictive/early-warnings" \
  -H "Authorization: Bearer $JWT"
```

---

## Scenarios

### Create Stackable Scenario

```bash
curl -X POST "$PERCEPTION_URL/api/v1/scenarios/stackable" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Pipeline sabotage recovery",
    "description": "Operational response playbook",
    "confidence": 0.78,
    "impactScore": 0.62
  }'
```

### Compose Scenario

```bash
curl -X GET "$PERCEPTION_URL/api/v1/scenarios/stackable/{scenario_id}/compose" \
  -H "Authorization: Bearer $JWT"
```

### Search Precedents

```bash
curl -X GET "$PERCEPTION_URL/api/v1/scenarios/precedents?domain=energy&minConfidence=0.6" \
  -H "Authorization: Bearer $JWT"
```

---

## Monitoring

### Get Pipeline Health

```bash
curl -X GET "$PERCEPTION_URL/api/v1/monitoring/health" \
  -H "Authorization: Bearer $JWT"
```

### Trigger Audit

```bash
curl -X POST "$PERCEPTION_URL/api/v1/monitoring/audit" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "issueType": "DATA_LAG",
    "from": "2025-01-17T11:45:00Z",
    "to": "2025-01-17T12:00:00Z"
  }'
```

---

## Health Checks

### Health Check (No Auth)

```bash
curl -X GET "$PERCEPTION_URL/actuator/health"
```

### Prometheus Metrics (No Auth)

```bash
curl -X GET "$PERCEPTION_URL/actuator/prometheus"
```

---

## Useful Tips

### Pretty Print JSON

```bash
# Add `| jq .` to any command
curl -s -X GET "$PERCEPTION_URL/api/v1/intelligence/events?limit=5" \
  -H "Authorization: Bearer $JWT" | jq .
```

### Verbose Output

```bash
# Add -v for verbose output (debugging)
curl -v -X GET "$PERCEPTION_URL/api/v1/acquisition/sources" \
  -H "Authorization: Bearer $JWT"
```

### Save Response to File

```bash
curl -X GET "$PERCEPTION_URL/api/v1/intelligence/events?limit=100" \
  -H "Authorization: Bearer $JWT" \
  -o events.json
```

### Time Request

```bash
time curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" \
  -X GET "$PERCEPTION_URL/api/v1/rim/mesh" \
  -H "Authorization: Bearer $JWT"
```

### Loop Through Results

```bash
# Get all event IDs
curl -s "$PERCEPTION_URL/api/v1/intelligence/events?limit=100" \
  -H "Authorization: Bearer $JWT" | \
  jq -r '.data[].id' | \
  while read id; do
    echo "Processing event: $id"
    curl -s "$PERCEPTION_URL/api/v1/intelligence/events/$id/scenarios" \
      -H "Authorization: Bearer $JWT" | jq .
  done
```

---

## Error Response Format

All API errors follow this format:

```json
{
  "status": "ERROR",
  "error": {
    "code": "NOT_FOUND",
    "message": "Event not found: evt-missing"
  },
  "timestamp": "2025-01-17T12:00:00Z",
  "requestId": "req-abc-123"
}
```

Common error codes:
- `NOT_FOUND` - Resource doesn't exist
- `INVALID_REQUEST` - Invalid request parameters
- `UNAUTHORIZED` - Missing or invalid authentication
- `FORBIDDEN` - Insufficient permissions
- `RATE_LIMITED` - Too many requests
- `INTERNAL_ERROR` - Server error

---

## Related Documentation

- [API Documentation](../../PERCEPTION/docs/API_DOCUMENTATION.md)
- [Endpoint Topology](../../PERCEPTION/docs/API_ENDPOINT_TOPOLOGY.md)
- [Shell Script Examples](../../PERCEPTION/scripts/api-examples/)
