# BUTTERFLY Glossary

> Terminology and definitions for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03

---

## A

### Actor
An entity that can take actions within the BUTTERFLY ecosystem. Actors are tracked by ODYSSEY and can include regulators, market participants, organizations, or individuals.

### ADR (Architecture Decision Record)
A document that captures an important architectural decision made along with its context and consequences.

### API Gateway
See [NEXUS](#nexus).

### Avro
A data serialization system used by BUTTERFLY for Kafka message schemas. Provides schema evolution and compact binary encoding.

---

## B

### Backpressure
A flow control mechanism that allows consumers to signal to producers when they cannot keep up with the message rate.

### BUTTERFLY
The complete ecosystem of services for 4D temporal analysis, strategic cognition, sensory processing, and governance. Named for the butterfly effect - small changes leading to significant outcomes.

---

## C

### CAPSULE
The 4D Atomic History Service. Provides immutable, time-indexed storage of entity state with time-travel capabilities. Named for its ability to encapsulate moments in time.

### Circuit Breaker
A pattern used in NEXUS to prevent cascading failures. When a service fails repeatedly, the circuit breaker "opens" to prevent further calls.

### ClickHouse
An open-source column-oriented database used by PERCEPTION for analytics and time-series data storage.

### Consumer Group
A group of Kafka consumers that share the work of reading from topic partitions. Each partition is consumed by exactly one consumer in the group.

### Consumer Lag
The difference between the latest message offset and the consumer's current position. High lag indicates the consumer is falling behind.

---

## D

### Dead Letter Queue (DLQ)
A Kafka topic that stores messages that could not be processed successfully after multiple retries.

### DRY (Don't Repeat Yourself)
A software development principle stating that every piece of knowledge should have a single, unambiguous representation.

---

## E

### Entity
A uniquely identifiable object tracked by BUTTERFLY, such as a financial instrument, organization, or market. Entities are identified by RimNodeId.

### Event
An immutable record of something that happened at a specific point in time. Events are detected by PERCEPTION and stored in CAPSULE.

### EvidencePlanner
A PLATO engine that generates execution plans with provable verification steps.

---

## F

### FeatureForge
A PLATO engine for feature extraction and transformation.

### Flink
Apache Flink - a stream processing framework used by PERCEPTION for real-time event processing.

---

## G

### Graph Database
A database that uses graph structures (nodes and edges) for semantic queries. ODYSSEY uses JanusGraph for relationship modeling.

### Gremlin
The graph traversal language used to query JanusGraph in ODYSSEY.

---

## H

### HFT (High-Frequency Trading)
Trading strategies that use high-speed data analysis. BUTTERFLY's fast-path events (`RimFastEvent`) support HFT workloads.

### Husky
A tool for managing Git hooks, used in BUTTERFLY to enforce commit message format and run pre-commit checks.

---

## I

### Iceberg
Apache Iceberg - a table format for large analytic datasets. Used by PERCEPTION for data lake storage.

### Idempotency
The property where an operation produces the same result regardless of how many times it's executed. Critical for reliable message processing.

---

## J

### JanusGraph
A distributed graph database used by ODYSSEY for storing and querying entity relationships.

### JWT (JSON Web Token)
A compact, URL-safe means of representing claims between two parties. Used for authentication in BUTTERFLY.

---

## K

### Kafka
Apache Kafka - a distributed event streaming platform used as BUTTERFLY's message backbone.

### Keyspace
In Cassandra, a keyspace is a container for tables, similar to a database schema in relational databases.

---

## L

### LawMiner
A PLATO engine for extracting legal and regulatory requirements from documents.

---

## M

### mTLS (Mutual TLS)
A security protocol where both client and server authenticate each other using certificates. Used for service-to-service communication.

### Multi-tenancy
The ability to serve multiple customers (tenants) from a single instance while keeping their data isolated.

---

## N

### Nessie
Project Nessie - a Git-like version control system for data lakes. Used by PERCEPTION for data versioning.

### NEXUS
The Unified Integration Layer / Cognitive Cortex. Acts as the API gateway and orchestrates requests across BUTTERFLY services.

---

## O

### ODYSSEY
The Strategic Cognition Engine. Provides graph-based analysis of entity relationships, actors, and influence paths.

### Omniscient Vantage
A CAPSULE query mode that returns all available data, including future events relative to the query timestamp.

### OWASP ASVS
OWASP Application Security Verification Standard - a framework for testing web application security.

---

## P

### Partition
A unit of parallelism in Kafka. Topics are divided into partitions that can be processed independently.

### PERCEPTION
The Sensory and Interpretation Layer. Ingests, processes, and interprets signals from multiple data sources.

### PLATO
The Governance and Intelligence Service. Provides six specialized engines for analysis, planning, and compliance.

---

## R

### RBAC (Role-Based Access Control)
An access control model where permissions are assigned to roles, and users are assigned roles.

### Resilience4j
A fault tolerance library used in NEXUS for circuit breakers, rate limiters, and bulkheads.

### ResearchOS
A PLATO engine for orchestrating research workflows and knowledge synthesis.

### RimFastEvent
The high-frequency event schema used for real-time signal processing. Defined in `butterfly-common`.

### RimNodeId
The canonical identity format used across BUTTERFLY: `rim:<type>:<namespace>:<localId>`. Provides consistent entity identification.

### Runbook
A documented procedure for handling operational tasks or incidents.

---

## S

### Schema Registry
A service (Confluent Schema Registry) that stores and manages Avro schemas for Kafka topics.

### SLI (Service Level Indicator)
A measurement of service behavior, such as request latency or error rate.

### SLO (Service Level Objective)
A target value for an SLI, such as "99.9% of requests complete in under 500ms".

### SOC2
Service Organization Control 2 - a compliance framework for service organizations.

### SpecMiner
A PLATO engine for extracting specifications and requirements from technical documents.

### SynthPlane
A PLATO engine for synthetic data generation and scenario modeling.

---

## T

### Tenant
A customer or organizational unit in a multi-tenant deployment. Each tenant's data is isolated.

### Time Travel
The ability to query CAPSULE data as it existed at a specific point in time, regardless of subsequent changes.

### Topic
A category or feed name to which records are published in Kafka.

### Trust Score
A metric (0.0 to 1.0) indicating the reliability of an event or signal detected by PERCEPTION.

---

## V

### Vantage Mode
The perspective from which CAPSULE data is queried:
- **Current**: Data known at the query timestamp
- **Omniscient**: All data including future events

---

## W

### WebFlux
Spring WebFlux - a reactive web framework used by NEXUS and PLATO for non-blocking request handling.

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Reference Index](README.md) | Reference overview |
| [Identity Model](../architecture/identity-model.md) | RimNodeId details |
| [FAQ](faq.md) | Common questions |

