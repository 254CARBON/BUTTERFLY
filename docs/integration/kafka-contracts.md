# BUTTERFLY Kafka Event Contracts

> Event schemas and topic catalog for Kafka integration

**Last Updated**: 2025-12-03  
**Target Audience**: Integration developers, data engineers

---

## Overview

BUTTERFLY uses Apache Kafka as its event backbone. This document catalogs all Kafka topics, their schemas, and usage patterns.

---

## Topic Catalog

### High-Frequency Topics

| Topic | Producer | Consumers | Schema | Partitions | Retention |
|-------|----------|-----------|--------|------------|-----------|
| `rim.fast-path` | PERCEPTION | ODYSSEY | RimFastEvent | 12 | 7 days |

### Domain Event Topics

| Topic | Producer | Consumers | Schema | Partitions | Retention |
|-------|----------|-----------|--------|------------|-----------|
| `perception.events` | PERCEPTION | CAPSULE, ODYSSEY, PLATO | PerceptionEvent | 6 | 30 days |
| `perception.signals` | PERCEPTION | ODYSSEY | SignalEvent | 6 | 14 days |
| `capsule.snapshots` | CAPSULE | ODYSSEY, NEXUS | CapsuleSnapshot | 6 | 30 days |
| `odyssey.paths` | ODYSSEY | NEXUS | PathUpdate | 6 | 14 days |
| `odyssey.actors` | ODYSSEY | NEXUS | ActorUpdate | 3 | 14 days |
| `plato.governance` | PLATO | Audit | GovernanceEvent | 3 | 90 days |
| `plato.plans` | PLATO | Audit | PlanEvent | 3 | 90 days |

### Dead Letter Queues

| Topic | Source | Retention |
|-------|--------|-----------|
| `rim.fast-path.dlq` | rim.fast-path | 90 days |
| `perception.events.dlq` | perception.events | 90 days |
| `capsule.snapshots.dlq` | capsule.snapshots | 90 days |

---

## Schema Registry

### Configuration

```yaml
schema.registry:
  url: http://localhost:8081
  auto.register.schemas: true
  compatibility: BACKWARD
```

### Schema Evolution

| Compatibility | Allowed Changes |
|---------------|-----------------|
| BACKWARD (default) | Add optional fields, remove fields |
| FORWARD | Add fields, remove optional fields |
| FULL | Add/remove optional fields |
| NONE | Any changes |

---

## Event Schemas

### RimFastEvent

High-frequency signal events for real-time processing.

```avro
{
  "namespace": "com.z254.butterfly.hft",
  "type": "record",
  "name": "RimFastEvent",
  "fields": [
    {
      "name": "rim_node_id",
      "type": "string",
      "doc": "Canonical RimNodeId (rim:{type}:{namespace}:{localId})"
    },
    {
      "name": "event_id",
      "type": ["null", "string"],
      "default": null,
      "doc": "Optional unique event identifier"
    },
    {
      "name": "timestamp",
      "type": "long",
      "logicalType": "timestamp-millis",
      "doc": "Event timestamp in milliseconds since epoch"
    },
    {
      "name": "critical_metrics",
      "type": {
        "type": "map",
        "values": "double"
      },
      "doc": "Key metrics (price, volume, etc.)"
    },
    {
      "name": "price",
      "type": ["null", "double"],
      "default": null,
      "doc": "Price value if applicable"
    },
    {
      "name": "volume",
      "type": ["null", "double"],
      "default": null,
      "doc": "Volume value if applicable"
    },
    {
      "name": "stress",
      "type": ["null", "double"],
      "default": null,
      "doc": "Stress indicator (0.0 to 1.0)"
    },
    {
      "name": "status",
      "type": "string",
      "doc": "Event status (ACTIVE, STALE, etc.)"
    },
    {
      "name": "source_topic",
      "type": ["null", "string"],
      "default": null,
      "doc": "Original source topic if forwarded"
    }
  ]
}
```

**Usage Example:**

```java
RimFastEvent event = RimFastEvent.newBuilder()
    .setRimNodeId("rim:entity:finance:EURUSD")
    .setTimestamp(System.currentTimeMillis())
    .setCriticalMetrics(Map.of("bid", 1.0855, "ask", 1.0857))
    .setPrice(1.0856)
    .setVolume(1000000.0)
    .setStress(0.3)
    .setStatus("ACTIVE")
    .build();

producer.send(new ProducerRecord<>("rim.fast-path", 
    event.getRimNodeId(), event));
```

### PerceptionEvent

Events detected by PERCEPTION's intelligence layer.

```avro
{
  "namespace": "com.z254.butterfly.perception",
  "type": "record",
  "name": "PerceptionEvent",
  "fields": [
    {
      "name": "event_id",
      "type": "string"
    },
    {
      "name": "event_type",
      "type": {
        "type": "enum",
        "name": "EventType",
        "symbols": ["MARKET", "REGULATORY", "CORPORATE", "GEOPOLITICAL", "ECONOMIC"]
      }
    },
    {
      "name": "timestamp",
      "type": "long",
      "logicalType": "timestamp-millis"
    },
    {
      "name": "detected_at",
      "type": "long",
      "logicalType": "timestamp-millis"
    },
    {
      "name": "title",
      "type": "string"
    },
    {
      "name": "summary",
      "type": ["null", "string"],
      "default": null
    },
    {
      "name": "entities",
      "type": {
        "type": "array",
        "items": "string"
      },
      "doc": "Affected RimNodeIds"
    },
    {
      "name": "trust_score",
      "type": "double",
      "doc": "Trust score (0.0 to 1.0)"
    },
    {
      "name": "impact_estimate",
      "type": ["null", {
        "type": "record",
        "name": "ImpactEstimate",
        "fields": [
          {"name": "magnitude", "type": "double"},
          {"name": "direction", "type": "string"},
          {"name": "confidence", "type": "double"}
        ]
      }],
      "default": null
    },
    {
      "name": "sources",
      "type": {
        "type": "array",
        "items": "string"
      }
    },
    {
      "name": "metadata",
      "type": {
        "type": "map",
        "values": "string"
      }
    }
  ]
}
```

### CapsuleSnapshot

Notifications for new CAPSULE creation.

```avro
{
  "namespace": "com.z254.butterfly.capsule",
  "type": "record",
  "name": "CapsuleSnapshot",
  "fields": [
    {
      "name": "capsule_id",
      "type": "string"
    },
    {
      "name": "scope_id",
      "type": "string",
      "doc": "RimNodeId of the entity"
    },
    {
      "name": "timestamp",
      "type": "long",
      "logicalType": "timestamp-millis"
    },
    {
      "name": "resolution",
      "type": "int",
      "doc": "Time resolution in seconds"
    },
    {
      "name": "vantage_mode",
      "type": "string"
    },
    {
      "name": "idempotency_key",
      "type": "string"
    },
    {
      "name": "created_at",
      "type": "long",
      "logicalType": "timestamp-millis"
    }
  ]
}
```

---

## Producer Configuration

### From butterfly-common

```java
import com.z254.butterfly.common.kafka.RimKafkaContracts;

// Get producer configuration
Map<String, Object> producerConfig = RimKafkaContracts.fastPathProducerConfig(
    "localhost:9092",
    "http://localhost:8081"
);

// Additional recommended settings
producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
producerConfig.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
producerConfig.put(ProducerConfig.RETRIES_CONFIG, 3);
producerConfig.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
```

### Spring Boot Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      acks: all
      properties:
        enable.idempotence: true
        schema.registry.url: http://localhost:8081
```

---

## Consumer Configuration

### Java Consumer

```java
Map<String, Object> consumerConfig = RimKafkaContracts.fastPathConsumerConfig(
    "localhost:9092",
    "http://localhost:8081",
    "my-consumer-group"
);

// Additional settings
consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
```

### Spring Boot Consumer

```java
@KafkaListener(
    topics = "rim.fast-path",
    groupId = "my-consumer-group",
    containerFactory = "avroKafkaListenerContainerFactory"
)
public void handleFastPathEvent(RimFastEvent event, Acknowledgment ack) {
    try {
        processEvent(event);
        ack.acknowledge();
    } catch (Exception e) {
        // Handle error, potentially send to DLQ
        handleError(event, e);
    }
}
```

---

## Message Key Strategies

### Key by Entity

Ensures all events for an entity go to the same partition:

```java
String key = event.getRimNodeId();  // e.g., "rim:entity:finance:EURUSD"
producer.send(new ProducerRecord<>(topic, key, event));
```

### Key by Correlation

For event chains:

```java
String key = correlationId;
producer.send(new ProducerRecord<>(topic, key, event));
```

---

## Error Handling

### Dead Letter Queue Pattern

```java
@Component
public class DlqHandler {
    
    @KafkaListener(topics = "rim.fast-path")
    @RetryableTopic(
        attempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".dlq"
    )
    public void handle(RimFastEvent event) {
        processEvent(event);
    }
    
    @DltHandler
    public void handleDlt(RimFastEvent event, Exception exception) {
        log.error("Failed to process event after retries: {}", 
            event.getRimNodeId(), exception);
        // Alert, store for manual review, etc.
    }
}
```

---

## Monitoring

### Consumer Lag

Monitor consumer group lag using:

```bash
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group my-consumer-group \
  --describe
```

### Key Metrics

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `kafka_consumer_lag` | Messages behind | > 10000 |
| `kafka_producer_record_error_total` | Producer errors | > 0 |
| `kafka_consumer_fetch_latency_avg` | Fetch latency | > 1000ms |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Integration Guide](README.md) | Integration overview |
| [Communication Patterns](../architecture/communication-patterns.md) | Messaging patterns |
| [Data Flow](../architecture/data-flow.md) | How data flows |
| [butterfly-common](../services/butterfly-common.md) | Shared library |

