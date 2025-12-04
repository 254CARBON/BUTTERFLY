# BUTTERFLY Communication Patterns

> Event-driven, REST, and WebSocket integration patterns

**Last Updated**: 2025-12-03  
**Target Audience**: Developers, integration architects

---

## Overview

BUTTERFLY services communicate through three primary patterns:
1. **Apache Kafka** - Asynchronous event-driven messaging
2. **REST APIs** - Synchronous request/response
3. **WebSocket** - Real-time bidirectional streaming

---

## Pattern 1: Event-Driven (Apache Kafka)

### When to Use

- Asynchronous processing
- Event sourcing and replay
- Decoupled service communication
- High-throughput data streaming
- Audit trail requirements

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Event-Driven Architecture                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Producer Service                                                           │
│       │                                                                     │
│       │  1. Create event                                                   │
│       │  2. Serialize (Avro)                                               │
│       │  3. Publish to topic                                               │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                         Apache Kafka                                  │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                    Schema Registry                           │   │  │
│  │  │  • Avro schema storage                                       │   │  │
│  │  │  • Compatibility checking                                    │   │  │
│  │  │  • Schema evolution                                          │   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                      Topic                                    │   │  │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │   │  │
│  │  │  │Partition│ │Partition│ │Partition│ │Partition│           │   │  │
│  │  │  │    0    │ │    1    │ │    2    │ │   ...   │           │   │  │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  │                                                                       │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                       │
│         ┌────────────────────────┼────────────────────────┐             │
│         │                        │                        │             │
│         ▼                        ▼                        ▼             │
│  Consumer Group A         Consumer Group B         Consumer Group C     │
│  (CAPSULE)                (ODYSSEY)                (Audit)              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Producer Configuration

```java
// From butterfly-common: RimKafkaContracts
Map<String, Object> producerProps = RimKafkaContracts.fastPathProducerConfig(
    "localhost:9092",           // bootstrap servers
    "http://localhost:8081"     // schema registry
);

producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);

KafkaTemplate<String, RimFastEvent> template = 
    new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
```

### Consumer Configuration

```java
@KafkaListener(
    topics = "rim.fast-path",
    groupId = "odyssey-reflex",
    containerFactory = "avroKafkaListenerContainerFactory"
)
public void handleFastPathEvent(RimFastEvent event) {
    // Process event
    reflexService.evaluate(event);
}
```

### Dead Letter Queue Pattern

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Dead Letter Queue Pattern                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   Primary Topic              DLQ                    Recovery        │
│   (perception.events)    (perception.events.dlq)   (Manual/Auto)   │
│                                                                      │
│      ┌───────┐              ┌───────┐              ┌───────┐       │
│      │ Event │              │Failed │              │Replay │       │
│      │       │─────────────▶│ Event │─────────────▶│       │       │
│      └───────┘   Fail       └───────┘   Investigate└───────┘       │
│          │       after           │       and fix       │           │
│          │       retries         │                     │           │
│          ▼                       │                     ▼           │
│      ┌───────┐                   │              ┌───────┐         │
│      │Success│                   │              │Success│         │
│      └───────┘                   │              └───────┘         │
│                                  │                                 │
│   Retry Policy:                  │   DLQ Message includes:        │
│   • 3 retries                    │   • Original message           │
│   • Exponential backoff          │   • Error details              │
│   • Max 30s delay                │   • Retry count                │
│                                  │   • Timestamp                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Message Ordering Guarantees

| Strategy | Use Case | Configuration |
|----------|----------|---------------|
| Per-entity ordering | Entity state updates | Key = RimNodeId |
| Global ordering | Audit events | Single partition |
| Best-effort | General events | Multiple partitions |

---

## Pattern 2: REST APIs

### When to Use

- Synchronous queries
- CRUD operations
- Client-facing APIs
- Request/response patterns
- OpenAPI-documented endpoints

### API Design Principles

```
┌─────────────────────────────────────────────────────────────────────┐
│                      REST API Design                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Resource-Oriented URLs                                             │
│  ──────────────────────                                             │
│  GET    /api/v1/capsules              List CAPSULEs                │
│  POST   /api/v1/capsules              Create CAPSULE               │
│  GET    /api/v1/capsules/{id}         Get CAPSULE                  │
│  DELETE /api/v1/capsules/{id}         Delete CAPSULE               │
│                                                                      │
│  GET    /api/v1/history               Query history                │
│  GET    /api/v1/history/{scopeId}     Get entity history           │
│                                                                      │
│  Nested Resources                                                   │
│  ──────────────────                                                 │
│  GET    /api/v1/specs/{id}/artifacts  List Spec's Artifacts        │
│  POST   /api/v1/plans/{id}/execute    Execute a Plan               │
│                                                                      │
│  Query Parameters                                                   │
│  ──────────────────                                                 │
│  GET    /api/v1/capsules?scopeId=...&from=...&to=...              │
│  GET    /api/v1/events?page=0&size=20&sort=timestamp,desc         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Request/Response Format

**Standard Request Headers:**

| Header | Purpose | Example |
|--------|---------|---------|
| `Content-Type` | Request body format | `application/json` |
| `Accept` | Response format | `application/json` |
| `Authorization` | Authentication | `Bearer <token>` |
| `X-Request-ID` | Correlation ID | `req-abc123` |
| `X-Correlation-ID` | Parent trace ID | `trace-xyz789` |

**Standard Response Envelope:**

```json
{
  "data": { ... },
  "meta": {
    "requestId": "req-abc123",
    "timestamp": "2024-12-03T10:00:00Z",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 150,
      "totalPages": 8
    }
  }
}
```

**Error Response:**

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request parameters",
    "details": [
      {
        "field": "scopeId",
        "message": "must match pattern rim:{nodeType}:{namespace}:{localId}"
      }
    ]
  },
  "meta": {
    "requestId": "req-abc123",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Error Codes

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Invalid request parameters |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |
| 403 | `FORBIDDEN` | Insufficient permissions |
| 404 | `NOT_FOUND` | Resource not found |
| 409 | `CONFLICT` | Resource conflict (duplicate) |
| 429 | `RATE_LIMITED` | Too many requests |
| 500 | `INTERNAL_ERROR` | Server error |
| 503 | `SERVICE_UNAVAILABLE` | Service temporarily unavailable |

### Rate Limiting

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Rate Limiting                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Rate Limit Headers                                                 │
│  ──────────────────                                                 │
│  X-RateLimit-Limit: 1000        # Requests allowed per window      │
│  X-RateLimit-Remaining: 950     # Requests remaining               │
│  X-RateLimit-Reset: 1701603600  # Window reset timestamp           │
│                                                                      │
│  Limits by Endpoint Type                                            │
│  ──────────────────────────                                         │
│  Query APIs:   1000 req/min     (high-read scenarios)              │
│  Command APIs:  100 req/min     (writes, mutations)                │
│  Admin APIs:    100 req/min     (administrative operations)        │
│                                                                      │
│  429 Response                                                       │
│  ────────────                                                       │
│  {                                                                  │
│    "error": {                                                       │
│      "code": "RATE_LIMITED",                                       │
│      "message": "Too many requests",                               │
│      "retryAfter": 60                                              │
│    }                                                                │
│  }                                                                  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Pattern 3: WebSocket

### When to Use

- Real-time updates
- Progress streaming
- Live dashboards
- Bidirectional communication

### PLATO Plan Execution Streaming

```
┌─────────────────────────────────────────────────────────────────────┐
│                    WebSocket Plan Streaming                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Client                           PLATO Server                      │
│    │                                   │                            │
│    │  CONNECT ws://host/ws/plans/{id}  │                           │
│    │──────────────────────────────────▶│                           │
│    │                                   │                            │
│    │  Connection Established           │                            │
│    │◀──────────────────────────────────│                           │
│    │                                   │                            │
│    │                                   │  Plan Execution Starts    │
│    │                                   │                            │
│    │  {"type":"STEP_STARTED",          │                           │
│    │   "step":"evaluate-conditions",   │                           │
│    │   "progress":0}                   │                           │
│    │◀──────────────────────────────────│                           │
│    │                                   │                            │
│    │  {"type":"STEP_PROGRESS",         │                           │
│    │   "step":"evaluate-conditions",   │                           │
│    │   "progress":50}                  │                           │
│    │◀──────────────────────────────────│                           │
│    │                                   │                            │
│    │  {"type":"STEP_COMPLETED",        │                           │
│    │   "step":"evaluate-conditions",   │                           │
│    │   "result":{...}}                 │                           │
│    │◀──────────────────────────────────│                           │
│    │                                   │                            │
│    │  {"type":"PLAN_COMPLETED",        │                           │
│    │   "status":"SUCCESS",             │                           │
│    │   "artifacts":[...]}              │                           │
│    │◀──────────────────────────────────│                           │
│    │                                   │                            │
│    │  DISCONNECT                       │                            │
│    │──────────────────────────────────▶│                           │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### WebSocket Message Types

| Type | Direction | Purpose |
|------|-----------|---------|
| `STEP_STARTED` | Server → Client | Step execution started |
| `STEP_PROGRESS` | Server → Client | Progress update |
| `STEP_COMPLETED` | Server → Client | Step finished |
| `STEP_FAILED` | Server → Client | Step error |
| `PLAN_COMPLETED` | Server → Client | Plan finished |
| `PLAN_FAILED` | Server → Client | Plan error |
| `PING` | Client → Server | Keep-alive |
| `PONG` | Server → Client | Keep-alive response |

### JavaScript Client Example

```javascript
const planId = 'plan_123';
const ws = new WebSocket(`ws://localhost:8080/ws/plans/${planId}`);

ws.onopen = () => {
  console.log('Connected to plan execution stream');
  // Start keep-alive
  setInterval(() => ws.send(JSON.stringify({type: 'PING'})), 30000);
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  switch (message.type) {
    case 'STEP_STARTED':
      console.log(`Step ${message.step} started`);
      break;
    case 'STEP_PROGRESS':
      updateProgressBar(message.step, message.progress);
      break;
    case 'STEP_COMPLETED':
      console.log(`Step ${message.step} completed:`, message.result);
      break;
    case 'PLAN_COMPLETED':
      console.log('Plan completed successfully');
      displayArtifacts(message.artifacts);
      break;
    case 'PLAN_FAILED':
      console.error('Plan failed:', message.error);
      break;
  }
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('Connection closed');
};
```

---

## Cross-Cutting Concerns

### Correlation and Tracing

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Distributed Tracing                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Request arrives with or without trace context                      │
│       │                                                              │
│       ▼                                                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Generate/Propagate IDs                                       │   │
│  │                                                               │   │
│  │  X-Request-ID:     Unique per request (client or generated)  │   │
│  │  X-Correlation-ID: Parent trace for related operations       │   │
│  │  traceparent:      W3C trace context (OpenTelemetry)         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│       │                                                              │
│       ▼                                                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Service Processing                                           │   │
│  │                                                               │   │
│  │  • Log request ID in all log entries                         │   │
│  │  • Include in Kafka message headers                          │   │
│  │  • Pass to downstream HTTP calls                             │   │
│  │  • Return in response headers                                │   │
│  └─────────────────────────────────────────────────────────────┘   │
│       │                                                              │
│       ▼                                                              │
│  Response includes X-Request-ID for client correlation              │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Circuit Breaker Pattern

```java
// Resilience4j circuit breaker for upstream service calls
@CircuitBreaker(name = "capsule", fallbackMethod = "fallbackGetHistory")
@Retry(name = "capsule")
@TimeLimiter(name = "capsule")
public Mono<List<Capsule>> getHistory(String scopeId, Instant from, Instant to) {
    return capsuleClient.getHistory(scopeId, from, to);
}

public Mono<List<Capsule>> fallbackGetHistory(
    String scopeId, Instant from, Instant to, Throwable t) {
    log.warn("Fallback for getHistory: {}", t.getMessage());
    return Mono.just(Collections.emptyList());
}
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Data Flow](data-flow.md) | Cross-service data flows |
| [API Catalog](../api/api-catalog.md) | Complete API reference |
| [Kafka Contracts](../integration/kafka-contracts.md) | Event schemas |
| [Authentication](../api/authentication.md) | Auth patterns |
