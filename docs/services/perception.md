# PERCEPTION Service

> Sensory and Interpretation Layer for the BUTTERFLY Ecosystem

**Last Updated**: 2025-12-03  
**Service Port**: 8080  
**Full Documentation**: [PERCEPTION README](../../PERCEPTION/README.md)

---

## Overview

PERCEPTION is the sensory layer of BUTTERFLY, responsible for ingesting external information, normalizing it, scoring its trustworthiness, detecting events, and maintaining the Reality Integration Mesh (RIM).

### Service Type: Full-Stack with Dedicated UI

PERCEPTION is a **full-stack service** with its own dedicated user interface for the PERCEPTION multiservice platform.

| Aspect | Details |
|--------|---------|
| **UI Strategy** | Dedicated PERCEPTION UI (not shared) |
| **Primary Interface** | REST API + Dedicated Web UI |
| **Management Portal** | Self-contained PERCEPTION UI |

> **Note**: Unlike NEXUS, ODYSSEY, and PLATO (which are headless background services using the CAPSULE UI), PERCEPTION has its own dedicated UI tailored for multiservice operations including acquisition management, trust score dashboards, event monitoring, signal visualization, RIM graph exploration, and source configuration.

## Purpose

> "PERCEPTION senses the world and transforms raw information into trusted, structured signals."

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PERCEPTION                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         Core Domains                                  │   │
│  │                                                                       │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐  │   │
│  │  │ Acquisition │  │  Integrity  │  │      Intelligence           │  │   │
│  │  │             │  │             │  │                             │  │   │
│  │  │ • Web crawl │  │ • Trust     │  │ • Event detection          │  │   │
│  │  │ • API feeds │  │   scoring   │  │ • Scenario generation      │  │   │
│  │  │ • File      │  │ • Dedup     │  │ • Impact estimation        │  │   │
│  │  │   ingest    │  │ • Cluster   │  │                             │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐  │   │
│  │  │   Signals   │  │  Monitoring │  │    Reality Integration      │  │   │
│  │  │             │  │             │  │         Mesh (RIM)          │  │   │
│  │  │ • Weak sig  │  │ • Health    │  │                             │  │   │
│  │  │ • Volatility│  │ • Telemetry │  │ • World model graph         │  │   │
│  │  │ • Horizons  │  │ • Self-audit│  │ • CAPSULE sync              │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────────┘  │   │
│  │                                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Technology: Java 17 │ Spring Boot │ Kafka │ PostgreSQL │ Ignite │ Iceberg │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Capabilities

### 1. Content Acquisition

Ingest content from multiple sources:
- Web crawling (Apache Nutch)
- API integrations
- File and feed processing (Apache Camel)
- Custom connectors

### 2. Integrity Processing

Build trust in information:
- **Trust Scoring**: Multi-factor trust assessment
- **Deduplication**: Content fingerprinting and clustering
- **Storyline Building**: Tracking narrative evolution

### 3. Event Intelligence

Detect and analyze events:
- **Event Detection**: Identify significant occurrences
- **Scenario Generation**: Project potential outcomes
- **Impact Estimation**: Assess effect on entities

### 4. Signal Processing

Identify patterns and anomalies:
- **Weak Signal Detection**: Early indicator identification
- **Volatility Analysis**: Change rate monitoring
- **Horizon Scanning**: Future-oriented scanning

### 5. Reality Integration Mesh (RIM)

Maintain a coherent world model:
- **World Model Graph**: Entity and relationship mapping
- **CAPSULE Synchronization**: State persistence
- **Ignorance Surface**: Track knowledge gaps

---

## Module Structure

| Module | Responsibility |
|--------|----------------|
| `perception-api` | REST API endpoints |
| `perception-acquisition` | Content ingestion |
| `perception-integrity` | Trust and deduplication |
| `perception-intelligence` | Event detection |
| `perception-signals` | Weak signal processing |
| `perception-rim` | Reality mesh maintenance |
| `perception-monitoring` | Health and telemetry |

---

## API Surface

### Key Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/acquisition/content` | Submit content for processing |
| GET | `/api/v1/acquisition/content/{id}/status` | Check processing status |
| GET | `/api/v1/integrity/trust-scores` | Get trust scores |
| GET | `/api/v1/intelligence/events` | List detected events |
| GET | `/api/v1/signals/weak` | Get weak signals |
| GET | `/api/v1/rim/nodes/{nodeId}` | Get RIM node |
| GET | `/actuator/health` | Service health |

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `perception.events` | Detected events |
| `perception.signals` | Weak signals |
| `perception.rim` | RIM updates |
| `rim.fast-path` | High-frequency signals |

---

## Configuration

### Key Properties

```yaml
perception:
  acquisition:
    batch-size: 100
    timeout-seconds: 30
  integrity:
    trust-threshold: 0.7
    dedup-window-hours: 24
  signals:
    weak-signal-threshold: 0.3
    horizon-days: 90
  rim:
    sync-interval-seconds: 60
```

### Infrastructure Dependencies

| Component | Purpose |
|-----------|---------|
| PostgreSQL | Operational state |
| Apache Kafka | Event streaming |
| Apache Ignite | Distributed cache |
| Apache Iceberg + Nessie | Data lakehouse |
| ClickHouse | Analytics |

---

## Multi-Tenancy

PERCEPTION supports multi-tenant deployments:
- Tenant isolation at data and processing level
- Tenant-specific configurations
- Resource quota management

---

## Quick Start

```bash
# Start infrastructure
cd PERCEPTION
docker compose up -d

# Build
mvn clean install

# Run API
cd perception-api
mvn spring-boot:run
```

---

## Health and Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Metrics

- Processing throughput
- Trust score distribution
- Event detection rate
- Signal count by type

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Full README](../../PERCEPTION/README.md) | Complete documentation |
| [API Reference](../../PERCEPTION/docs/api/) | API documentation |
| [Operations Guide](../../PERCEPTION/docs/operations/) | Operational guides |
| [Architecture](../../PERCEPTION/docs/architecture/) | Technical architecture |

