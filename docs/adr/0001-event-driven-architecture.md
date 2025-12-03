# ADR-0001: Event-Driven Architecture with Apache Kafka

## Status

Accepted

## Date

2024-11-01

## Context

BUTTERFLY is a multi-service platform for world-scale strategic intelligence. The services (PERCEPTION, CAPSULE, ODYSSEY, PLATO) need to communicate in a way that:

1. Supports high-frequency data flows (HFT signals from PERCEPTION)
2. Decouples services for independent deployment and scaling
3. Provides durability and replay capabilities for audit and recovery
4. Handles varying load patterns without tight coupling
5. Supports multiple consumers for the same events

Traditional request-response patterns would create tight coupling and would not scale for high-frequency signal processing.

## Decision

We will use **Apache Kafka** as the primary event backbone for inter-service communication in BUTTERFLY.

Key design decisions:

1. **Topic naming**: `{domain}.{event-type}` pattern (e.g., `rim.fast-path`)
2. **Schema management**: Confluent Schema Registry with Avro serialization
3. **Compatibility**: BACKWARD compatibility strategy for schema evolution
4. **Partitioning**: By `rim_node_id` for ordering guarantees per entity
5. **Consumer groups**: Service-specific groups for independent consumption

### Event Flow

```
PERCEPTION → rim.fast-path → ODYSSEY (reflex processing)
                          → CAPSULE (history persistence)
                          → Analytics (optional)
```

## Consequences

### Positive

- **Decoupling**: Services can evolve independently
- **Scalability**: Horizontal scaling via partitions and consumer groups
- **Durability**: Events are persisted for replay and audit
- **Real-time**: Sub-second latency for HFT signal processing
- **Observability**: Topic metrics, lag monitoring, dead letter queues

### Negative

- **Operational complexity**: Kafka cluster management required
- **Eventual consistency**: Services must handle out-of-order/duplicate events
- **Learning curve**: Team needs Kafka expertise
- **Infrastructure cost**: Kafka cluster is a significant resource

### Neutral

- Schema Registry becomes a critical dependency
- Need for idempotency keys to handle reprocessing

## Alternatives Considered

### Alternative 1: REST APIs

Direct HTTP calls between services. Rejected because:
- Tight coupling
- No replay capability
- Poor fit for high-frequency data

### Alternative 2: RabbitMQ

Message broker with different semantics. Rejected because:
- Less suited for high-throughput streaming
- No built-in schema registry
- Weaker ordering guarantees

### Alternative 3: AWS Kinesis / Google Pub/Sub

Cloud-native streaming. Rejected because:
- Vendor lock-in
- Different operational model
- Less control over infrastructure

## References

- [butterfly-common IDENTITY_AND_CONTRACTS.md](../../butterfly-common/IDENTITY_AND_CONTRACTS.md)
- [Confluent Schema Registry Documentation](https://docs.confluent.io/platform/current/schema-registry/)
- [Kafka Streams Architecture](https://kafka.apache.org/documentation/streams/)

