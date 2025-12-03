# First Steps with BUTTERFLY

> A guided walkthrough of your first BUTTERFLY integration

**Last Updated**: 2025-12-03  
**Target Audience**: New developers, API integrators  
**Prerequisites**: [Installation Guide](installation.md) completed, services running

---

## Overview

This guide walks you through making your first API calls to BUTTERFLY services. By the end, you'll understand the basic patterns for interacting with each service.

## Before You Begin

Ensure services are running:

```bash
# Check service health
curl http://localhost:8080/actuator/health  # PERCEPTION
curl http://localhost:8081/actuator/health  # CAPSULE
curl http://localhost:8083/actuator/health  # PLATO

# All should return: {"status":"UP"}
```

---

## Step 1: Understanding RimNodeId

All entities in BUTTERFLY use a canonical identifier format called **RimNodeId**:

```
rim:{nodeType}:{namespace}:{localId}

Format:
  rim:        - Required prefix
  nodeType:   - Type of entity (entity, actor, event, scope, etc.)
  namespace:  - Domain namespace (finance, market, regulator, etc.)
  localId:    - Unique identifier within namespace
```

**Examples:**

| RimNodeId | Description |
|-----------|-------------|
| `rim:entity:finance:EURUSD` | Currency pair entity |
| `rim:actor:regulator:SEC` | SEC regulator actor |
| `rim:event:market:fed-rate-2024` | Market event |
| `rim:scope:macro:recession-scenario` | Macro scenario scope |

---

## Step 2: Create Your First CAPSULE

CAPSULEs are 4D atomic units of history. Let's create one:

### Create a CAPSULE

```bash
curl -X POST http://localhost:8081/api/v1/capsules \
  -H "Content-Type: application/json" \
  -d '{
    "scopeId": "rim:entity:finance:EURUSD",
    "resolution": 60,
    "vantageMode": "omniscient",
    "timestamp": "2024-12-03T10:00:00Z",
    "configuration": {
      "type": "currency_pair",
      "base": "EUR",
      "quote": "USD",
      "attributes": {
        "exchange": "FOREX",
        "liquidity": "high"
      }
    },
    "dynamics": {
      "priceChange": 0.0012,
      "volatility": 0.08,
      "momentum": "bullish"
    },
    "agency": {
      "actors": [
        {
          "id": "rim:actor:institution:central-banks",
          "intent": "monetary-policy",
          "confidence": 0.85
        }
      ]
    },
    "counterfactual": {
      "alternativePaths": [
        {
          "scenario": "rate-hike",
          "probability": 0.65,
          "impact": "strengthening-usd"
        }
      ]
    },
    "meta": {
      "confidence": 0.92,
      "sources": ["reuters", "bloomberg"],
      "quality": "high"
    }
  }'
```

**Response:**

```json
{
  "id": "cap_abc123xyz",
  "scopeId": "rim:entity:finance:EURUSD",
  "timestamp": "2024-12-03T10:00:00Z",
  "resolution": 60,
  "status": "created",
  "idempotencyKey": "rim:entity:finance:EURUSD:2024-12-03T10:00:00Z:60:omniscient"
}
```

### Retrieve a CAPSULE

```bash
curl http://localhost:8081/api/v1/capsules/cap_abc123xyz
```

### Query CAPSULEs

```bash
# Get history for an entity
curl "http://localhost:8081/api/v1/history?scopeId=rim:entity:finance:EURUSD&from=2024-12-01T00:00:00Z&to=2024-12-03T23:59:59Z"
```

---

## Step 3: Publish an Event to PERCEPTION

PERCEPTION ingests and processes external information. Let's trigger an event:

### Submit Content for Analysis

```bash
curl -X POST http://localhost:8080/api/v1/acquisition/content \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: req-001" \
  -d '{
    "sourceId": "manual-input",
    "content": {
      "title": "Federal Reserve Announces Rate Decision",
      "body": "The Federal Reserve announced a 25 basis point rate increase...",
      "publishedAt": "2024-12-03T14:00:00Z",
      "source": "reuters.com",
      "url": "https://reuters.com/markets/fed-rate-decision"
    },
    "metadata": {
      "category": "monetary-policy",
      "region": "north-america",
      "importance": "high"
    }
  }'
```

**Response:**

```json
{
  "contentId": "cnt_xyz789",
  "status": "accepted",
  "correlationId": "req-001",
  "processingStage": "acquisition"
}
```

### Check Processing Status

```bash
curl http://localhost:8080/api/v1/acquisition/content/cnt_xyz789/status
```

### Get Trust Score

```bash
curl http://localhost:8080/api/v1/integrity/trust-scores?contentId=cnt_xyz789
```

---

## Step 4: Query PLATO Governance

PLATO provides governance and intelligence capabilities. Let's interact with it:

### Create a Spec

```bash
curl -X POST http://localhost:8083/api/v1/specs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "name": "market-volatility-alert",
    "type": "RULE",
    "version": "1.0.0",
    "description": "Alert when market volatility exceeds threshold",
    "definition": {
      "condition": "volatility > 0.15",
      "action": "trigger_alert",
      "parameters": {
        "threshold": 0.15,
        "window": "1h"
      }
    },
    "tags": ["market", "volatility", "alert"]
  }'
```

**Response:**

```json
{
  "id": "spec_vol_alert_001",
  "name": "market-volatility-alert",
  "version": "1.0.0",
  "status": "DRAFT",
  "createdAt": "2024-12-03T10:30:00Z"
}
```

### Evaluate a Policy

```bash
curl -X POST http://localhost:8083/api/v1/governance/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "specId": "spec_vol_alert_001",
    "context": {
      "entityId": "rim:entity:finance:EURUSD",
      "metrics": {
        "volatility": 0.18,
        "timestamp": "2024-12-03T10:00:00Z"
      }
    }
  }'
```

**Response:**

```json
{
  "evaluationId": "eval_123",
  "specId": "spec_vol_alert_001",
  "result": "TRIGGERED",
  "confidence": 0.95,
  "explanation": "Volatility 0.18 exceeds threshold 0.15",
  "actions": ["trigger_alert"],
  "evaluatedAt": "2024-12-03T10:31:00Z"
}
```

### Create a Plan

```bash
curl -X POST http://localhost:8083/api/v1/plans \
  -H "Content-Type: application/json" \
  -d '{
    "name": "volatility-response-plan",
    "description": "Automated response to high volatility",
    "steps": [
      {
        "name": "evaluate-conditions",
        "type": "EVALUATE",
        "specId": "spec_vol_alert_001"
      },
      {
        "name": "generate-report",
        "type": "SYNTHESIZE",
        "engine": "ResearchOS",
        "dependsOn": ["evaluate-conditions"]
      }
    ],
    "trigger": {
      "type": "EVENT",
      "source": "perception.events",
      "filter": "category = 'market-volatility'"
    }
  }'
```

---

## Step 5: Real-Time Updates via WebSocket

PLATO supports WebSocket for real-time plan execution updates:

### Connect to WebSocket

```javascript
// JavaScript example
const ws = new WebSocket('ws://localhost:8083/ws/plans/plan_123');

ws.onopen = () => {
  console.log('Connected to plan execution stream');
};

ws.onmessage = (event) => {
  const update = JSON.parse(event.data);
  console.log('Plan update:', update);
  // Example: { stepName: 'evaluate-conditions', status: 'COMPLETED', progress: 50 }
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};
```

### Using curl (for testing)

```bash
# Install websocat if needed: cargo install websocat
websocat ws://localhost:8083/ws/plans/plan_123
```

---

## Step 6: Publish to Kafka (Advanced)

For high-frequency data, publish directly to Kafka:

### Using the CLI Tool

```bash
# Publish a test event
./scripts/fastpath-cli.sh publish \
  --scenario golden-path \
  --bootstrap localhost:9092
```

### Using kafka-console-producer

```bash
# Publish a RimFastEvent
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic rim.fast-path \
  --property "parse.key=true" \
  --property "key.separator=:"

# Enter: rim:entity:finance:EURUSD:{"value": 1.0856, "timestamp": "2024-12-03T10:00:00Z"}
```

---

## Step 7: Explore Swagger UI

Each service provides interactive API documentation:

| Service | Swagger UI | Description |
|---------|------------|-------------|
| PERCEPTION | http://localhost:8080/swagger-ui.html | Signal acquisition and processing |
| CAPSULE | http://localhost:8081/swagger-ui.html | Historical storage API |
| PLATO | http://localhost:8083/swagger-ui.html | Governance and intelligence |

### Try It Out

1. Open Swagger UI in your browser
2. Click on an endpoint to expand it
3. Click "Try it out"
4. Fill in parameters
5. Click "Execute"
6. View the response

---

## Common Patterns

### Correlation IDs

Always include correlation IDs for traceability:

```bash
curl -X POST http://localhost:8080/api/v1/acquisition/content \
  -H "X-Request-ID: my-unique-correlation-id" \
  -H "X-Correlation-ID: parent-trace-id" \
  ...
```

### Pagination

Use pagination for large result sets:

```bash
curl "http://localhost:8081/api/v1/capsules?page=0&size=20&sort=timestamp,desc"
```

### Error Handling

API responses follow a consistent error format:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid RimNodeId format",
    "details": {
      "field": "scopeId",
      "value": "invalid-id",
      "expected": "rim:{nodeType}:{namespace}:{localId}"
    }
  },
  "timestamp": "2024-12-03T10:30:00Z",
  "requestId": "req-001"
}
```

### Retry with Exponential Backoff

For resilient integrations:

```python
import time
import requests

def api_call_with_retry(url, max_retries=3, base_delay=1):
    for attempt in range(max_retries):
        try:
            response = requests.get(url)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            if attempt == max_retries - 1:
                raise
            delay = base_delay * (2 ** attempt)
            time.sleep(delay)
```

---

## What's Next?

| Goal | Next Step |
|------|-----------|
| Learn more about APIs | [API Documentation](../api/README.md) |
| Build integrations | [Integration Guide](../integration/README.md) |
| Understand architecture | [Architecture Overview](architecture-overview.md) |
| Deploy to production | [Operations Guide](../operations/README.md) |
| Contribute code | [Development Guide](../development/README.md) |

---

## Troubleshooting

### 401 Unauthorized

```bash
# Get an API token (if authentication is enabled)
curl -X POST http://localhost:8083/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"apiKey": "your-api-key"}'
```

### 404 Not Found

- Verify the service is running: `curl http://localhost:8080/actuator/health`
- Check the endpoint path in Swagger UI
- Ensure the resource exists

### 429 Too Many Requests

- Wait and retry with backoff
- Check rate limit headers: `X-RateLimit-Remaining`
- Consider using batch APIs for bulk operations

### 500 Internal Server Error

- Check service logs: `docker compose logs -f <service>`
- Review request payload for valid JSON
- File an issue if the error persists

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Catalog](../api/api-catalog.md) | Complete API reference |
| [Authentication](../api/authentication.md) | Auth patterns and tokens |
| [Error Handling](../api/error-handling.md) | Error codes and handling |
| [Client Guide](../integration/client-guide.md) | SDK and integration patterns |

