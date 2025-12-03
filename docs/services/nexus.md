# NEXUS Service

> Unified Integration Layer (Cognitive Cortex) for the BUTTERFLY Ecosystem

**Last Updated**: 2025-12-03  
**Service Port**: 8084  
**Full Documentation**: [NEXUS README](../../butterfly-nexus/README.md)

---

## Overview

NEXUS is the integration layer of BUTTERFLY—the "Cognitive Cortex" that unifies all services into a coherent whole. It provides temporal intelligence fusion, cross-system reasoning, and emergent capabilities that no single service can offer alone.

### Service Type: Background Service (Headless)

NEXUS operates as a **background service** with no dedicated UI. It is designed for programmatic interaction via REST APIs and WebSocket endpoints.

| Aspect | Details |
|--------|---------|
| **UI Strategy** | Reuses CAPSULE UI for management and visualization |
| **Primary Interface** | REST API + WebSocket endpoints |
| **Management Portal** | Access via shared CAPSULE UI |

> **Note**: All user-facing management, monitoring, and configuration for NEXUS is handled through the **CAPSULE UI portal**. NEXUS itself remains a headless background service focused on cross-service integration and temporal intelligence.

## Purpose

> "NEXUS integrates the ecosystem—fusing past, present, and future into unified intelligence."

---

## Four Integration Capabilities

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         NEXUS Capabilities                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                                                                      │    │
│  │  ┌─────────────────────────────┐  ┌─────────────────────────────┐  │    │
│  │  │  Temporal Intelligence      │  │    Autonomous Reasoning     │  │    │
│  │  │         Fabric              │  │          Engine             │  │    │
│  │  │                             │  │                             │  │    │
│  │  │ • Unified temporal queries  │  │ • Cross-system inference    │  │    │
│  │  │   (past + present + future) │  │ • Contradiction detection   │  │    │
│  │  │ • Causal chain discovery    │  │ • Autonomous hypothesis     │  │    │
│  │  │ • Temporal anomaly detect   │  │   generation                │  │    │
│  │  │ • Multi-service correlation │  │ • Belief reconciliation     │  │    │
│  │  │                             │  │                             │  │    │
│  │  │ Target: <100ms latency      │  │ Target: <500ms inference    │  │    │
│  │  └─────────────────────────────┘  └─────────────────────────────┘  │    │
│  │                                                                      │    │
│  │  ┌─────────────────────────────┐  ┌─────────────────────────────┐  │    │
│  │  │  Predictive Synthesis       │  │    Evolution Controller     │  │    │
│  │  │         Core                │  │                             │  │    │
│  │  │                             │  │                             │  │    │
│  │  │ • Strategic option          │  │ • Self-optimizing patterns  │  │    │
│  │  │   generation                │  │ • Closed-loop learning      │  │    │
│  │  │ • Cross-system ignorance    │  │ • Performance tracking      │  │    │
│  │  │   mapping                   │  │ • Auto-tuning               │  │    │
│  │  │ • Governance-aware scoring  │  │ • Adaptive behavior         │  │    │
│  │  │                             │  │                             │  │    │
│  │  │ Target: <5s option gen      │  │ Target: 10% monthly improve │  │    │
│  │  └─────────────────────────────┘  └─────────────────────────────┘  │    │
│  │                                                                      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Technology: Java 17 │ Spring Boot WebFlux │ Resilience4j │ Kafka          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Temporal Intelligence Fabric

The core capability of NEXUS: unified queries across time.

### TemporalSlice

A TemporalSlice fuses data from all time perspectives:

```java
TemporalSlice slice = temporalQueryService.getTemporalSlice(
    "rim:entity:finance:EURUSD",
    TemporalWindow.centered(
        Duration.ofDays(30),  // past window
        Duration.ofDays(7),   // future window
        Duration.ofHours(1)   // resolution
    )
).block();

// Access unified view
List<Capsule> past = slice.getPast();           // From CAPSULE
RimNode present = slice.getPresent();           // From PERCEPTION
List<PathProjection> futures = slice.getFutures(); // From ODYSSEY
```

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Temporal Intelligence Fabric                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│     Request: getTemporalSlice(nodeId, window)                               │
│                      │                                                       │
│                      ▼                                                       │
│              ┌───────────────┐                                              │
│              │    NEXUS      │                                              │
│              │  Orchestrator │                                              │
│              └───────────────┘                                              │
│                      │                                                       │
│     ┌────────────────┼────────────────┐                                    │
│     │                │                │                                    │
│     ▼                ▼                ▼                                    │
│ ┌─────────┐    ┌──────────┐    ┌──────────┐                               │
│ │ CAPSULE │    │PERCEPTION│    │ ODYSSEY  │                               │
│ │ Client  │    │  Client  │    │  Client  │                               │
│ │         │    │          │    │          │                               │
│ │  Past   │    │ Present  │    │  Future  │                               │
│ │ history │    │ RIM state│    │  paths   │                               │
│ └────┬────┘    └────┬─────┘    └────┬─────┘                               │
│      │              │               │                                      │
│      └──────────────┼───────────────┘                                      │
│                     ▼                                                       │
│           ┌─────────────────┐                                              │
│           │ Temporal Fusion │                                              │
│           │  & Coherence    │                                              │
│           │   Analysis      │                                              │
│           └─────────────────┘                                              │
│                     │                                                       │
│                     ▼                                                       │
│           ┌─────────────────┐                                              │
│           │  TemporalSlice  │                                              │
│           │  (unified view) │                                              │
│           └─────────────────┘                                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## API Surface

### Key Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/temporal/slice/{nodeId}` | Get temporal slice |
| POST | `/api/v1/temporal/query` | Complex temporal query |
| GET | `/api/v1/reasoning/infer` | Cross-system inference |
| GET | `/api/v1/synthesis/options/{nodeId}` | Get strategic options |
| GET | `/api/v1/evolution/metrics` | Evolution metrics |
| GET | `/actuator/health` | Service health |

### Example: Get Temporal Slice

```bash
curl "http://localhost:8084/api/v1/temporal/slice/rim:entity:finance:EURUSD?pastDays=30&futureDays=7"
```

**Response:**

```json
{
  "nodeId": "rim:entity:finance:EURUSD",
  "timestamp": "2024-12-03T10:00:00Z",
  "past": [
    {
      "timestamp": "2024-12-02T10:00:00Z",
      "configuration": {...},
      "dynamics": {...}
    }
  ],
  "present": {
    "currentState": {...},
    "trustScore": 0.92
  },
  "futures": [
    {
      "scenario": "bullish-continuation",
      "probability": 0.45,
      "horizon": "7d"
    },
    {
      "scenario": "range-bound",
      "probability": 0.35,
      "horizon": "7d"
    }
  ],
  "coherence": {
    "score": 0.88,
    "anomalies": []
  }
}
```

---

## Service Clients

NEXUS maintains resilient clients to all services:

```java
// Client configuration with Resilience4j
@Configuration
public class NexusClientConfig {
    
    @Bean
    public CapsuleClient capsuleClient(WebClient.Builder builder) {
        return new CapsuleClient(
            builder.baseUrl("http://localhost:8081").build()
        );
    }
    
    @Bean
    public PerceptionClient perceptionClient(WebClient.Builder builder) {
        return new PerceptionClient(
            builder.baseUrl("http://localhost:8080").build()
        );
    }
    
    @Bean
    public OdysseyClient odysseyClient(WebClient.Builder builder) {
        return new OdysseyClient(
            builder.baseUrl("http://localhost:8082").build()
        );
    }
    
    @Bean
    public PlatoClient platoClient(WebClient.Builder builder) {
        return new PlatoClient(
            builder.baseUrl("http://localhost:8083").build()
        );
    }
}
```

---

## Resilience Patterns

NEXUS implements comprehensive resilience:

### Circuit Breaker

```yaml
resilience4j:
  circuitbreaker:
    instances:
      capsule:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      perception:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

### Retry with Backoff

```yaml
resilience4j:
  retry:
    instances:
      capsule:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
```

### Fallback Behavior

When services are unavailable:
- Return cached data if fresh enough
- Return partial results with degradation indicators
- Trigger alerts for extended outages

---

## Performance Targets

| Capability | Target |
|------------|--------|
| Temporal slice query | < 100ms |
| Cross-system inference | < 500ms |
| Strategic option generation | < 5 seconds |
| Monthly improvement | 10% |

---

## Configuration

### Key Properties

```yaml
nexus:
  clients:
    capsule:
      base-url: http://localhost:8081
      timeout-ms: 5000
    perception:
      base-url: http://localhost:8080
      timeout-ms: 5000
    odyssey:
      base-url: http://localhost:8082
      timeout-ms: 10000
    plato:
      base-url: http://localhost:8083
      timeout-ms: 10000
  temporal:
    max-past-days: 365
    max-future-days: 90
    cache-ttl-seconds: 60
  evolution:
    learning-enabled: true
    metric-window-days: 30
```

---

## Quick Start

```bash
# Ensure dependent services are running
# (CAPSULE, PERCEPTION, ODYSSEY, PLATO)

# Build
cd butterfly-nexus
mvn clean install

# Run
mvn spring-boot:run
```

---

## Health and Monitoring

### Health Check

```bash
curl http://localhost:8084/actuator/health
```

### Key Metrics

- Temporal query latency
- Service client success rate
- Circuit breaker state
- Cache hit ratio
- Evolution improvement rate

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Full README](../../butterfly-nexus/README.md) | Complete documentation |
| [Data Flow](../architecture/data-flow.md) | Cross-service data flows |
| [Communication Patterns](../architecture/communication-patterns.md) | Integration patterns |

