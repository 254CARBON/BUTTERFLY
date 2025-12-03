# BUTTERFLY Deployment Guide

> Deployment strategies and configurations for BUTTERFLY services

**Last Updated**: 2025-12-03  
**Target Audience**: DevOps engineers, Platform teams

---

## Overview

This guide covers the various deployment options for the BUTTERFLY ecosystem.

---

## Deployment Options

| Option | Best For | Complexity |
|--------|----------|------------|
| [Docker Compose](docker.md) | Development, testing | Low |
| [Kubernetes](kubernetes.md) | Production | High |
| Manual | Learning, debugging | Medium |

---

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Production Deployment Architecture                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                        Load Balancer (L7)                                ││
│  └────────────────────────────────┬────────────────────────────────────────┘│
│                                   │                                          │
│  ┌────────────────────────────────┴────────────────────────────────────────┐│
│  │                           NEXUS Gateway                                  ││
│  │                    (3 replicas, horizontal scaling)                      ││
│  └────────────────────────────────┬────────────────────────────────────────┘│
│                                   │                                          │
│  ┌────────────────────────────────┴────────────────────────────────────────┐│
│  │                       Internal Service Mesh                              ││
│  │                                                                          ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐                 ││
│  │  │ CAPSULE  │  │ ODYSSEY  │  │PERCEPTION│  │  PLATO   │                 ││
│  │  │ (2 pods) │  │ (2 pods) │  │ (3 pods) │  │ (2 pods) │                 ││
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘                 ││
│  │       │             │             │             │                        ││
│  └───────┼─────────────┼─────────────┼─────────────┼────────────────────────┘│
│          │             │             │             │                         │
│  ┌───────┴─────────────┴─────────────┴─────────────┴────────────────────────┐│
│  │                          Data Layer                                       ││
│  │                                                                           ││
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   ││
│  │  │Cassandra │  │PostgreSQL│  │  Redis   │  │JanusGraph│  │ClickHouse│   ││
│  │  │(3 nodes) │  │ (HA)     │  │(Sentinel)│  │ (3 nodes)│  │ (cluster)│   ││
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘   ││
│  │                                                                           ││
│  │  ┌────────────────────────────────────────────────────────────────────┐  ││
│  │  │                    Kafka Cluster (3 brokers)                        │  ││
│  │  └────────────────────────────────────────────────────────────────────┘  ││
│  │                                                                           ││
│  └───────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Environment Configuration

### Required Environment Variables

```bash
# Database Configuration
CASSANDRA_CONTACT_POINTS=cassandra-0:9042,cassandra-1:9042,cassandra-2:9042
CASSANDRA_KEYSPACE=butterfly
CASSANDRA_LOCAL_DC=datacenter1
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=perception
POSTGRES_USER=butterfly
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka-0:9092,kafka-1:9092,kafka-2:9092
SCHEMA_REGISTRY_URL=http://schema-registry:8081

# Redis Configuration
REDIS_HOST=redis-sentinel
REDIS_PORT=26379
REDIS_MASTER_NAME=mymaster

# JanusGraph Configuration
JANUSGRAPH_HOST=janusgraph
JANUSGRAPH_PORT=8182

# ClickHouse Configuration
CLICKHOUSE_HOST=clickhouse
CLICKHOUSE_PORT=8123

# Security
JWT_SECRET=${JWT_SECRET}
API_KEY_HASH_SALT=${API_KEY_HASH_SALT}

# Observability
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

---

## Service Dependencies

### Startup Order

```
1. Databases (Cassandra, PostgreSQL, Redis, JanusGraph, ClickHouse)
2. Message Broker (Kafka, Schema Registry)
3. Backend Services (CAPSULE, ODYSSEY, PERCEPTION, PLATO)
4. Gateway (NEXUS)
```

### Health Check Dependencies

| Service | Depends On | Health Check |
|---------|------------|--------------|
| CAPSULE | Cassandra, Redis, Kafka | `/actuator/health` |
| ODYSSEY | JanusGraph, Kafka | `/actuator/health` |
| PERCEPTION | PostgreSQL, Kafka, ClickHouse | `/actuator/health` |
| PLATO | Cassandra, Kafka | `/actuator/health` |
| NEXUS | All services | `/actuator/health` |

---

## Deployment Guides

- [Docker Deployment](docker.md) - Local and development deployments
- [Kubernetes Deployment](kubernetes.md) - Production-grade orchestration
- [Production Checklist](production-checklist.md) - Pre-launch validation

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Operations Overview](../README.md) | Operations index |
| [Monitoring](../monitoring/README.md) | Observability setup |
| [Capacity Planning](../capacity-planning.md) | Resource planning |

