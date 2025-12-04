# BUTTERFLY Technology Stack

> Technology decisions and rationale for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-04  
**Target Audience**: Architects, developers, technical evaluators

---

## Overview

This document outlines the technology choices for the BUTTERFLY ecosystem, including the rationale behind each decision and relevant Architecture Decision Records (ADRs).

---

## Technology Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       BUTTERFLY Technology Stack                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Application Layer                                                          │
│  ─────────────────                                                          │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │   Java 17    │ │ Spring Boot  │ │   WebFlux    │ │     Lombok       │  │
│  │   (LTS)      │ │    3.2.x     │ │  (Reactive)  │ │    MapStruct     │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘  │
│                                                                              │
│  Messaging & Events                                                         │
│  ──────────────────                                                         │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                       │
│  │ Apache Kafka │ │    Avro      │ │   Schema     │                       │
│  │    3.6.x     │ │   1.11.x     │ │  Registry    │                       │
│  └──────────────┘ └──────────────┘ └──────────────┘                       │
│                                                                              │
│  Data Storage                                                               │
│  ────────────────                                                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │  PostgreSQL  │ │  Cassandra   │ │  JanusGraph  │ │      Redis       │  │
│  │    15.x      │ │    4.1.x     │ │    1.0.x     │ │       7.x        │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘  │
│                                                                              │
│  ┌──────────────┐ ┌──────────────┐                                        │
│  │   Iceberg    │ │    Ignite    │                                        │
│  │  + Nessie    │ │    2.16.x    │                                        │
│  └──────────────┘ └──────────────┘                                        │
│                                                                              │
│  Integration                                                                │
│  ────────────────                                                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                       │
│  │ Apache Camel │ │ Resilience4j │ │   Jackson    │                       │
│  │    4.x       │ │    2.x       │ │   2.16.x     │                       │
│  └──────────────┘ └──────────────┘ └──────────────┘                       │
│                                                                              │
│  Observability                                                              │
│  ────────────────                                                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │  Micrometer  │ │  Prometheus  │ │   Grafana    │ │   ClickHouse     │  │
│  │    1.12.x    │ │              │ │              │ │                  │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘  │
│                                                                              │
│  Infrastructure                                                             │
│  ────────────────                                                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                       │
│  │   Docker     │ │  Kubernetes  │ │    Helm      │                       │
│  │   20+        │ │   1.28+      │ │    3.x       │                       │
│  └──────────────┘ └──────────────┘ └──────────────┘                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Core Platform

### Java 17

| Aspect | Details |
|--------|---------|
| **Version** | Java 17 (all services) |
| **Distribution** | Eclipse Temurin (Adoptium) |
| **Rationale** | LTS version, modern language features, strong ecosystem |
| **Features Used** | Records, improved switch/instanceof patterns, text blocks |

### Spring Boot 3.2.x

| Aspect | Details |
|--------|---------|
| **Version** | 3.2.0 |
| **Modules** | WebFlux, Data JPA, Kafka, Actuator, Security |
| **Rationale** | Industry standard, comprehensive ecosystem, reactive support |
| **Related ADR** | [ADR-0002: Spring Boot WebFlux](../adr/0002-spring-boot-webflux.md) (PLATO) |

### Spring WebFlux

| Aspect | Details |
|--------|---------|
| **Pattern** | Reactive, non-blocking |
| **Use Cases** | High-concurrency APIs, streaming, WebSocket |
| **Rationale** | Better resource utilization, backpressure support |
| **Services** | PLATO (primary), NEXUS, ODYSSEY (partial) |

---

## Messaging

### Apache Kafka 3.6.x

| Aspect | Details |
|--------|---------|
| **Version** | 3.6.0 |
| **Use Cases** | Event streaming, service communication, audit |
| **Configuration** | 12 partitions (high-throughput), 6 partitions (standard) |
| **Rationale** | Durability, scalability, replay capability |
| **Related ADR** | [ADR-0001: Event-Driven Architecture](../adr/0001-event-driven-architecture.md) |

**Topic Configuration:**

```yaml
kafka:
  topics:
    rim-fast-path:
      partitions: 12
      replication-factor: 3
      retention-days: 7
    perception-events:
      partitions: 6
      replication-factor: 3
      retention-days: 30
```

### Apache Avro 1.11.x

| Aspect | Details |
|--------|---------|
| **Version** | 1.11.3 |
| **Use Cases** | Kafka message serialization |
| **Rationale** | Compact binary format, schema evolution, language support |
| **Schema Location** | `butterfly-common/src/main/avro/` |

### Confluent Schema Registry

| Aspect | Details |
|--------|---------|
| **Version** | 7.5.0 |
| **Compatibility** | BACKWARD (default) |
| **Use Cases** | Schema versioning, validation, evolution |

---

## Data Storage

### PostgreSQL 15.x

| Aspect | Details |
|--------|---------|
| **Service** | PERCEPTION |
| **Use Cases** | Operational state, source configurations, relational data |
| **Features** | JSONB columns, full-text search, partitioning |
| **Rationale** | ACID compliance, mature tooling, JSON support |

### Apache Cassandra 4.1.x

| Aspect | Details |
|--------|---------|
| **Services** | CAPSULE, PLATO |
| **Use Cases** | Time-series history, high-write workloads |
| **Features** | Wide-column store, tunable consistency, TTL |
| **Rationale** | Write scalability, time-series optimization |
| **Related ADR** | [ADR-0003: Cassandra/JanusGraph](../adr/0003-cassandra-janusgraph.md) (PLATO) |

**Schema Design:**

```cql
CREATE TABLE capsules (
    scope_id text,
    timestamp timestamp,
    resolution int,
    vantage_mode text,
    idempotency_key text,
    configuration text,
    dynamics text,
    agency text,
    counterfactual text,
    meta text,
    PRIMARY KEY ((scope_id), timestamp, resolution)
) WITH CLUSTERING ORDER BY (timestamp DESC);
```

### JanusGraph 1.0.x

| Aspect | Details |
|--------|---------|
| **Service** | ODYSSEY |
| **Use Cases** | Entity graph, relationships, traversals |
| **Backend** | Cassandra (storage), Elasticsearch (index) |
| **Query Language** | Gremlin |
| **Rationale** | Graph traversals, relationship modeling |

### Redis 7.x

| Aspect | Details |
|--------|---------|
| **Services** | All |
| **Use Cases** | Caching, session state, rate limiting |
| **Features** | Pub/sub, Lua scripting, cluster mode |
| **Rationale** | Low-latency caching, distributed locking |

### Apache Ignite 2.16.x

| Aspect | Details |
|--------|---------|
| **Service** | PERCEPTION |
| **Use Cases** | Distributed cache, compute grid |
| **Features** | SQL queries, continuous queries, persistence |
| **Rationale** | Feature store compatibility, distributed caching |

### Apache Iceberg + Nessie

| Aspect | Details |
|--------|---------|
| **Service** | PERCEPTION |
| **Use Cases** | Data lakehouse, versioned data |
| **Features** | Time travel, schema evolution, partition evolution |
| **Rationale** | Analytics workloads, data versioning |

---

## Integration

### Apache Camel 4.x

| Aspect | Details |
|--------|---------|
| **Service** | PERCEPTION |
| **Use Cases** | Enterprise integration patterns, connectors |
| **Patterns** | Routes, processors, aggregators |
| **Rationale** | Extensive connector library, EIP support |

### Resilience4j 2.x

| Aspect | Details |
|--------|---------|
| **Services** | All |
| **Patterns** | Circuit breaker, retry, rate limiter, bulkhead |
| **Rationale** | Native Java, Spring integration, lightweight |

**Configuration Example:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      capsule:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  retry:
    instances:
      capsule:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
```

### Jackson 2.16.x

| Aspect | Details |
|--------|---------|
| **Use Cases** | JSON serialization/deserialization |
| **Modules** | JSR310 (datetime), Kotlin, afterburner |
| **Configuration** | Strict null handling, ISO dates |

---

## Observability

### Micrometer 1.12.x

| Aspect | Details |
|--------|---------|
| **Use Cases** | Application metrics |
| **Registries** | Prometheus, InfluxDB (optional) |
| **Features** | Timers, counters, gauges, distribution summaries |

### Prometheus + Grafana

| Aspect | Details |
|--------|---------|
| **Use Cases** | Metrics collection, alerting, dashboards |
| **Integration** | Spring Boot Actuator → Prometheus scrape |
| **Dashboards** | Service health, Kafka lag, database metrics |

### ClickHouse

| Aspect | Details |
|--------|---------|
| **Service** | PERCEPTION |
| **Use Cases** | Observability analytics, long-term metrics storage |
| **Features** | Columnar storage, real-time analytics |
| **Rationale** | High-performance analytics queries |

---

## Build and Deployment

### Maven 3.9+

| Aspect | Details |
|--------|---------|
| **Version** | 3.9.x |
| **Plugins** | Spring Boot, Avro, JaCoCo, Checkstyle, SpotBugs |
| **Structure** | Multi-module with parent POM |

### Docker

| Aspect | Details |
|--------|---------|
| **Base Image** | Eclipse Temurin (Alpine where possible) |
| **Build** | Multi-stage builds |
| **Registry** | Private registry (production) |

**Dockerfile Pattern:**

```dockerfile
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes

| Aspect | Details |
|--------|---------|
| **Version** | 1.28+ |
| **Deployment** | Helm charts per service |
| **Features** | HPA, PDB, ConfigMaps, Secrets |
| **Monitoring** | Prometheus Operator, Grafana |

---

## Security

### Spring Security 6.x

| Aspect | Details |
|--------|---------|
| **Auth Methods** | JWT, API Key, OAuth2 |
| **Authorization** | RBAC with @PreAuthorize |
| **Features** | WebFlux security, method security |

### JWT Libraries

| Library | Version | Use |
|---------|---------|-----|
| jjwt-api | 0.12.3 | JWT creation and validation |
| jjwt-impl | 0.12.3 | Implementation |
| jjwt-jackson | 0.12.3 | JSON processing |

---

## Testing

### Testing Stack

| Framework | Version | Use |
|-----------|---------|-----|
| JUnit 5 | 5.10.x | Unit testing |
| AssertJ | 3.24.x | Fluent assertions |
| Mockito | 5.7.x | Mocking |
| Testcontainers | 1.19.x | Integration testing |
| WireMock | 3.x | HTTP mocking |

### Code Quality

| Tool | Purpose |
|------|---------|
| JaCoCo | Code coverage |
| Checkstyle | Style enforcement |
| SpotBugs | Bug detection |
| OWASP Dependency Check | Vulnerability scanning |
| SonarQube | Continuous inspection |

---

## Version Matrix

| Component | Version | EOL |
|-----------|---------|-----|
| Java | 17 LTS | Sep 2029 |
| Java | 21 LTS | Sep 2031 |
| Spring Boot | 3.2.x | Nov 2025 |
| Kafka | 3.6.x | - |
| PostgreSQL | 15.x | Nov 2027 |
| Cassandra | 4.1.x | - |
| Redis | 7.x | - |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [ADR Index](../adr/README.md) | Architecture decisions |
| [Ecosystem Overview](ecosystem-overview.md) | System architecture |
| [Parent POM](../../pom.xml) | Version management |
