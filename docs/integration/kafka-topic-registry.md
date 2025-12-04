# BUTTERFLY Kafka Topic Registry

This document provides a comprehensive registry of all Kafka topics used across the BUTTERFLY ecosystem. It serves as the authoritative reference for topic configurations, ownership, and schema contracts.

## Overview

The BUTTERFLY ecosystem uses Apache Kafka for event-driven communication between services. All topics follow a naming convention: `{service}.{domain}.{event-type}`.

## Topic Registry

### PERCEPTION Topics

| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `rim.fast-path` | `RimFastEvent.avsc` | PERCEPTION | 7d | 12 | High-frequency RIM state updates for real-time processing |
| `perception.events.detected` | JSON | PERCEPTION | 14d | 6 | Entity detection events from the perception engine |
| `perception.scenarios.proposed` | JSON | PERCEPTION | 14d | 6 | New scenarios proposed by the scenario engine |
| `perception.rim.state-changes` | JSON | PERCEPTION | 7d | 6 | RIM node state change notifications |

### CAPSULE Topics

| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `capsule.events.created` | JSON | CAPSULE | 30d | 6 | Notifications when new capsules are created |
| `capsule.events.updated` | JSON | CAPSULE | 30d | 6 | Notifications when existing capsules are updated |
| `capsule.counterfactuals.validated` | JSON | CAPSULE | 14d | 6 | Validated counterfactual scenarios |
| `capsule.snapshots` | `CapsuleSnapshot.avsc` | CAPSULE | 30d | 6 | Point-in-time capsule snapshots for archival |

### ODYSSEY Topics

| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `odyssey.paths.generated` | `NarrativePath.avsc` | ODYSSEY | 14d | 6 | Newly generated narrative paths |
| `odyssey.world-state.updated` | JSON | ODYSSEY | 7d | 6 | World state model updates |
| `odyssey.tipping-points.detected` | JSON | ODYSSEY | 14d | 6 | Detected tipping points approaching |
| `odyssey.actors` | JSON | ODYSSEY | 14d | 6 | Actor model updates |

### NEXUS Topics

#### Temporal Intelligence Fabric
| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `nexus.temporal.slices` | `NexusTemporalEvent.avsc` | NEXUS | 7d | 6 | Temporal slice query results with cross-system coherence |
| `nexus.temporal.causal-chains` | `NexusTemporalEvent.avsc` | NEXUS | 14d | 6 | Discovered causal chains from temporal analysis |
| `nexus.temporal.anomalies` | `NexusTemporalEvent.avsc` | NEXUS | 14d | 6 | Detected temporal anomalies across systems |

#### Autonomous Reasoning Engine
| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `nexus.reasoning.inferences` | `NexusReasoningEvent.avsc` | NEXUS | 30d | 6 | Cross-system inferences generated |
| `nexus.reasoning.hypotheses` | `NexusReasoningEvent.avsc` | NEXUS | 30d | 6 | Autonomous hypotheses for investigation |
| `nexus.reasoning.contradictions` | `NexusReasoningEvent.avsc` | NEXUS | 30d | 6 | Detected cross-system contradictions |

#### Predictive Synthesis Core
| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `nexus.synthesis.options` | `NexusSynthesisEvent.avsc` | NEXUS | 14d | 6 | Strategic options synthesized from all sources |
| `nexus.synthesis.ignorance` | `NexusSynthesisEvent.avsc` | NEXUS | 14d | 6 | Aggregated ignorance maps |
| `nexus.synthesis.cascade` | `NexusSynthesisEvent.avsc` | NEXUS | 7d | 6 | Scenario cascade pipeline events |

### PLATO Topics

| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `plato.specs` | `PlatoEvent.avsc` | PLATO | 90d | 6 | Governance specifications and updates |
| `plato.decisions` | `PlatoEvent.avsc` | PLATO | 90d | 6 | Governance decision outcomes |
| `plato.validations` | `PlatoEvent.avsc` | PLATO | 30d | 6 | Validation results from PLATO rules |
| `plato.plan.execution.feedback` | JSON | PLATO | 30d | 6 | Plan execution feedback with conditions, actions, and metrics |

### SYNAPSE Topics

| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `synapse.action.execution.feedback` | JSON | SYNAPSE | 30d | 6 | Action execution feedback with decision context and metrics |

### Policy Learning Topics (Cross-System)

| Topic | Schema | Owner | Retention | Partitions | Description |
|-------|--------|-------|-----------|------------|-------------|
| `nexus.policy.parameterizations` | JSON | NEXUS | 30d | 6 | Learned policy parameter suggestions from NEXUS to PLATO |
| `nexus.experiment.designs` | JSON | NEXUS | 30d | 6 | A/B test design suggestions from NEXUS to PLATO |

## Schema Registry

All Avro schemas are maintained in `butterfly-common/src/main/avro/` and are the single source of truth for inter-service communication.

### Schema Versioning

- **Current Version**: v1.0.0 (frozen for Phase 1)
- **Compatibility Mode**: BACKWARD
- **Schema Registry URL**: `${SCHEMA_REGISTRY_URL:http://localhost:8085}`

See [SCHEMA_VERSIONING.md](../contracts/SCHEMA_VERSIONING.md) for the full versioning policy.

## Dead Letter Queue (DLQ) Configuration

Each topic has a corresponding DLQ topic for failed message handling:

| Original Topic | DLQ Topic | Retention |
|---------------|-----------|-----------|
| `nexus.*` | `nexus.*.dlq` | 30d |
| `perception.*` | `perception.*.dlq` | 30d |
| `capsule.*` | `capsule.*.dlq` | 30d |
| `odyssey.*` | `odyssey.*.dlq` | 30d |
| `plato.*` | `plato.*.dlq` | 30d |

### DLQ Message Format

DLQ messages include error context:
```json
{
  "originalTopic": "nexus.temporal.slices",
  "originalKey": "rim:entity:finance:EURUSD",
  "originalPartition": 3,
  "originalOffset": 12345,
  "errorMessage": "Deserialization failed",
  "errorClass": "org.apache.avro.AvroRuntimeException",
  "failedAt": "2024-01-15T10:30:00Z",
  "retryCount": 3,
  "consumerGroup": "nexus-temporal-consumer",
  "originalPayload": "base64-encoded-payload"
}
```

## Consumer Groups

| Consumer Group | Service | Topics Consumed |
|---------------|---------|-----------------|
| `nexus-perception-consumer` | NEXUS | `perception.*` |
| `nexus-capsule-consumer` | NEXUS | `capsule.*` |
| `nexus-odyssey-consumer` | NEXUS | `odyssey.*` |
| `nexus-plato-execution-feedback-consumer` | NEXUS | `plato.plan.execution.feedback` |
| `nexus-synapse-execution-feedback-consumer` | NEXUS | `synapse.action.execution.feedback` |
| `perception-rim-consumer` | PERCEPTION | `rim.fast-path` |
| `capsule-events-consumer` | CAPSULE | `perception.events.*` |
| `odyssey-paths-consumer` | ODYSSEY | `nexus.temporal.*` |
| `plato-governance-consumer` | PLATO | `nexus.synthesis.options`, `nexus.reasoning.hypotheses` |
| `plato-nexus-parameterizations` | PLATO | `nexus.policy.parameterizations` |
| `plato-nexus-experiments` | PLATO | `nexus.experiment.designs` |

## Idempotency Configuration

### Producer Idempotency

All NEXUS producers are configured with:
```yaml
spring.kafka.producer:
  enable-idempotence: true
  acks: all
  retries: 3
  max-in-flight-requests-per-connection: 5
```

### Consumer Idempotency

Consumers use `IdempotencyKeyGenerator` from `butterfly-common` to deduplicate:
- Keys are generated from: `{entityId}:{eventType}:{timestamp}:{contentHash}`
- Deduplication window: 24 hours (Redis-backed)

## Monitoring

### Metrics Exposed

| Metric | Description |
|--------|-------------|
| `kafka_consumer_records_consumed_total` | Total records consumed per topic |
| `kafka_consumer_lag` | Consumer lag per partition |
| `kafka_producer_record_send_total` | Total records sent per topic |
| `kafka_dlq_messages_total` | Total messages sent to DLQ |
| `kafka_dlq_replay_success_total` | Successful DLQ replays |

### Alerting

See `butterfly-nexus/config/prometheus-alerts.yml` for Kafka-related alerts.

## Topic Creation

Topics are auto-created via Spring Kafka `@Bean` configuration in each service's `KafkaConfig.java`. Default settings:

```java
@Bean
public NewTopic exampleTopic() {
    return TopicBuilder.name("service.domain.event")
        .partitions(6)
        .replicas(3)
        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(14).toMillis()))
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
        .build();
}
```

## Replay Strategy

For replaying messages from DLQ:

1. **Endpoint**: `POST /admin/dlq/replay`
2. **Parameters**:
   - `topic`: Original topic name
   - `fromOffset`: Starting offset (optional)
   - `toOffset`: Ending offset (optional)
   - `maxMessages`: Maximum messages to replay (default: 100)
3. **Process**:
   - Messages are read from DLQ
   - Original payload is extracted and validated
   - Message is republished to original topic with new idempotency key
   - DLQ message is marked as replayed

## Security

- **Authentication**: SASL/PLAIN with service-specific credentials
- **Authorization**: ACLs per service and topic
- **Encryption**: TLS 1.3 for in-transit encryption

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2024-01-15 | Initial registry documentation | BUTTERFLY Team |
| 2024-01-20 | Added NEXUS topics | BUTTERFLY Team |
| 2024-02-01 | Froze v1 schema set | BUTTERFLY Team |

