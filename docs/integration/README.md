# BUTTERFLY Integration Guide

> Guides for integrating with the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: Integration developers, system architects

---

## Overview

This section provides comprehensive guides for integrating with BUTTERFLY, including REST API patterns, Kafka event contracts, webhook configuration, and SDK usage.

## Integration Documentation

| Document | Description |
|----------|-------------|
| [Client Guide](client-guide.md) | Integration patterns and best practices |
| [Kafka Contracts](kafka-contracts.md) | Event schemas and topic catalog |
| [Webhooks](webhooks.md) | Webhook integration patterns |
| **SDKs**: [Java](sdks/java.md) \| [Python](sdks/python.md) \| [TypeScript](sdks/typescript.md) | Language-specific client libraries |

---

## Integration Patterns

### Pattern 1: REST API Integration

For synchronous request/response operations:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       REST API Integration                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Your Application                          BUTTERFLY APIs                  │
│         │                                        │                          │
│         │  HTTPS Request                         │                          │
│         │  (JWT/API Key auth)                    │                          │
│         │───────────────────────────────────────▶│                          │
│         │                                        │                          │
│         │  JSON Response                         │                          │
│         │◀───────────────────────────────────────│                          │
│         │                                        │                          │
│                                                                              │
│   Use Cases:                                                                │
│   • Query historical data                                                   │
│   • CRUD operations on resources                                           │
│   • Synchronous processing requests                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Pattern 2: Event Streaming (Kafka)

For real-time, high-throughput data flows:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Event Streaming Integration                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Your Application                  Kafka               BUTTERFLY           │
│         │                             │                     │               │
│         │                             │                     │               │
│         │    Subscribe to topics      │                     │               │
│         │────────────────────────────▶│                     │               │
│         │                             │                     │               │
│         │                             │◀────────────────────│               │
│         │                             │  Publish events     │               │
│         │                             │                     │               │
│         │◀────────────────────────────│                     │               │
│         │    Receive events           │                     │               │
│                                                                              │
│   Use Cases:                                                                │
│   • Real-time signal processing                                            │
│   • Event-driven workflows                                                 │
│   • High-frequency data ingestion                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Pattern 3: Webhook Push

For event notifications without polling:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Webhook Integration                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   BUTTERFLY                                      Your Application           │
│       │                                               │                     │
│       │  Register webhook                             │                     │
│       │◀──────────────────────────────────────────────│                     │
│       │                                               │                     │
│       │                                               │                     │
│       │  Event occurs                                 │                     │
│       │                                               │                     │
│       │  POST to webhook URL                          │                     │
│       │──────────────────────────────────────────────▶│                     │
│       │                                               │                     │
│       │                               200 OK          │                     │
│       │◀──────────────────────────────────────────────│                     │
│                                                                              │
│   Use Cases:                                                                │
│   • Asynchronous notifications                                             │
│   • Trigger external workflows                                             │
│   • Reduce polling overhead                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Quick Start Examples

### REST API Quick Start

```bash
# Get authentication token (via NEXUS)
TOKEN=$(curl -s -X POST http://localhost:8084/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"apiKey": "your-api-key"}' | jq -r '.access_token')

# Query CAPSULEs
curl http://localhost:8081/api/v1/history?scopeId=rim:entity:finance:EURUSD \
  -H "Authorization: Bearer $TOKEN"
```

### Kafka Consumer Quick Start

```java
// Java Kafka consumer
Properties props = new Properties();
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ConsumerConfig.GROUP_ID_CONFIG, "my-consumer-group");
props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
props.put("schema.registry.url", "http://localhost:8081");

KafkaConsumer<String, RimFastEvent> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("rim.fast-path"));

while (true) {
    ConsumerRecords<String, RimFastEvent> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, RimFastEvent> record : records) {
        processEvent(record.value());
    }
}
```

### Webhook Registration Quick Start

```bash
# Register webhook
curl -X POST http://localhost:8084/api/v1/webhooks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://your-app.com/webhook",
    "events": ["capsule.created", "event.detected"],
    "secret": "your-webhook-secret"
  }'
```

---

## Choosing an Integration Method

| Requirement | REST API | Kafka | Webhook |
|-------------|----------|-------|---------|
| Synchronous response | ✅ | ❌ | ❌ |
| Real-time streaming | ❌ | ✅ | ❌ |
| Event notifications | ❌ | ✅ | ✅ |
| High throughput | Limited | ✅ | Limited |
| Simple setup | ✅ | Moderate | ✅ |
| Reliable delivery | ✅ | ✅ | Moderate |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Documentation](../api/README.md) | REST API reference |
| [Architecture](../architecture/README.md) | System architecture |
| [Data Flow](../architecture/data-flow.md) | How data flows |
