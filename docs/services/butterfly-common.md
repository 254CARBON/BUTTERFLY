# butterfly-common Library

> Shared Domain Models, Identity Primitives, and Event Contracts

**Last Updated**: 2025-12-03  
**Full Documentation**: [butterfly-common README](../../butterfly-common/README.md)

---

## Overview

`butterfly-common` is the shared library that provides consistent domain models, identity primitives, and event contracts across all BUTTERFLY services. It ensures type safety, schema compatibility, and semantic consistency ecosystem-wide.

## Purpose

> "butterfly-common is the shared foundation—ensuring all services speak the same language."

---

## Key Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         butterfly-common                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                     Identity Primitives                             │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────────────────────────────┐  │    │
│  │  │    RimNodeId    │  │        IdempotencyKeyGenerator          │  │    │
│  │  │                 │  │                                         │  │    │
│  │  │ Canonical       │  │ Generate unique keys for deduplication: │  │    │
│  │  │ identity:       │  │                                         │  │    │
│  │  │ rim:{type}:     │  │ forSnapshot(nodeId, timestamp,          │  │    │
│  │  │   {ns}:{id}     │  │            resolution, vantage)         │  │    │
│  │  └─────────────────┘  └─────────────────────────────────────────┘  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                      Event Contracts (Avro)                         │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────────────────────────────┐  │    │
│  │  │  RimFastEvent   │  │         RimKafkaContracts               │  │    │
│  │  │                 │  │                                         │  │    │
│  │  │ High-frequency  │  │ Kafka producer/consumer configuration:  │  │    │
│  │  │ signal schema   │  │                                         │  │    │
│  │  │ for rim.fast-   │  │ • fastPathProducerConfig()              │  │    │
│  │  │ path topic      │  │ • fastPathConsumerConfig()              │  │    │
│  │  └─────────────────┘  └─────────────────────────────────────────┘  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                      Domain Models                                  │    │
│  │                                                                      │    │
│  │  CAPSULE components: Configuration, Dynamics, Agency,              │    │
│  │                      Counterfactual, Meta                          │    │
│  │                                                                      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Version: 0.2.1 │ Java 17 │ Avro 1.11.x                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## RimNodeId

The canonical identity format used across all services.

### Format

```
rim:{nodeType}:{namespace}:{localId}
```

### Usage

```java
import com.z254.butterfly.common.identity.RimNodeId;

// Parse from string
RimNodeId nodeId = RimNodeId.parse("rim:entity:finance:EURUSD");

// Construct programmatically
RimNodeId nodeId = RimNodeId.of("entity", "finance", "EURUSD");

// Access components
String nodeType = nodeId.getNodeType();    // "entity"
String namespace = nodeId.getNamespace();  // "finance"
String localId = nodeId.getLocalId();      // "EURUSD"

// Validation
boolean valid = RimNodeId.isValid("rim:entity:finance:EURUSD");  // true
```

### Node Types

| Type | Description |
|------|-------------|
| `entity` | Business entities, assets |
| `actor` | Agents, organizations |
| `event` | Occurrences, happenings |
| `scope` | Groupings, scenarios |
| `relation` | Connections |
| `metric` | Measurable quantities |
| `signal` | Detected patterns |

---

## IdempotencyKeyGenerator

Generate deterministic keys for deduplication.

### Usage

```java
import com.z254.butterfly.common.identity.IdempotencyKeyGenerator;

String idempotencyKey = IdempotencyKeyGenerator.forSnapshot(
    nodeId,
    Instant.parse("2024-12-03T10:00:00Z"),
    60,           // resolution in seconds
    "omniscient"  // vantage mode
);
// Returns: SHA256 hash of composite key
```

---

## Avro Schemas

### RimFastEvent

High-frequency event schema for `rim.fast-path` topic:

```avro
{
  "namespace": "com.z254.butterfly.hft",
  "type": "record",
  "name": "RimFastEvent",
  "fields": [
    {"name": "rim_node_id", "type": "string"},
    {"name": "event_id", "type": ["null", "string"], "default": null},
    {"name": "timestamp", "type": "long"},
    {"name": "critical_metrics", "type": {"type": "map", "values": "double"}},
    {"name": "price", "type": ["null", "double"], "default": null},
    {"name": "volume", "type": ["null", "double"], "default": null},
    {"name": "stress", "type": ["null", "double"], "default": null},
    {"name": "status", "type": "string"},
    {"name": "source_topic", "type": ["null", "string"], "default": null}
  ]
}
```

### Schema Location

```
butterfly-common/
├── src/main/avro/
│   └── rim_fast_event.avsc
└── target/generated-sources/avro/
    └── com/z254/butterfly/hft/RimFastEvent.java
```

---

## Kafka Configuration

### RimKafkaContracts

Helper methods for Kafka configuration:

```java
import com.z254.butterfly.common.kafka.RimKafkaContracts;

// Producer configuration
Map<String, Object> producerProps = RimKafkaContracts.fastPathProducerConfig(
    "localhost:9092",           // bootstrap servers
    "http://localhost:8081"     // schema registry URL
);

// Consumer configuration
Map<String, Object> consumerProps = RimKafkaContracts.fastPathConsumerConfig(
    "localhost:9092",
    "http://localhost:8081",
    "my-consumer-group"
);
```

### Configuration Properties

| Property | Value | Purpose |
|----------|-------|---------|
| `acks` | `all` | Durability guarantee |
| `enable.idempotence` | `true` | Exactly-once semantics |
| `retries` | `3` | Retry on failure |
| `key.serializer` | `StringSerializer` | String keys |
| `value.serializer` | `KafkaAvroSerializer` | Avro values |

---

## Maven Dependency

Add to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>butterfly-common</artifactId>
    <version>${butterfly-common.version}</version>
</dependency>
```

Current version: `0.2.1`

---

## Building

```bash
cd butterfly-common
mvn clean install
```

This will:
1. Generate Avro classes from schemas
2. Compile Java sources
3. Run tests
4. Install to local Maven repository

---

## Module Structure

```
butterfly-common/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── avro/
│   │   │   └── rim_fast_event.avsc
│   │   └── java/
│   │       └── com/z254/butterfly/common/
│   │           ├── identity/
│   │           │   ├── RimNodeId.java
│   │           │   └── IdempotencyKeyGenerator.java
│   │           ├── kafka/
│   │           │   └── RimKafkaContracts.java
│   │           └── model/
│   │               └── ... (domain models)
│   └── test/
│       └── java/
│           └── ... (tests)
└── target/
    └── generated-sources/avro/
        └── ... (generated Avro classes)
```

---

## Best Practices

### 1. Always Use RimNodeId

```java
// Do this
RimNodeId nodeId = RimNodeId.parse(scopeIdString);

// Not this
String nodeId = scopeIdString;  // No validation
```

### 2. Use Idempotency Keys for Deduplication

```java
String key = IdempotencyKeyGenerator.forSnapshot(...);
// Use this key as Kafka message key or database unique constraint
```

### 3. Let Maven Generate Avro Classes

Don't modify generated Avro classes. If schema changes are needed:
1. Modify the `.avsc` schema file
2. Rebuild with `mvn clean install`
3. Update dependent services

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Full README](../../butterfly-common/README.md) | Complete documentation |
| [Identity Model](../architecture/identity-model.md) | RimNodeId specification |
| [Kafka Contracts](../integration/kafka-contracts.md) | Event schemas |
| [Parent POM](../../pom.xml) | Version management |

