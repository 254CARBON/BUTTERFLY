# NEXUS Service

> Unified Integration Layer (Cognitive Cortex) for the BUTTERFLY Ecosystem

**Last Updated**: 2025-12-04  
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
│  │  │ • Calibrated uncertainty    │  │ • ML-enhanced scoring       │  │    │
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

New in Phase 5: every `TemporalSlice` now ships with an `uncertaintySummary` (calibrated confidence, uncertainty envelope, and contributing signals) computed by the TemporalUncertaintyService. Temporal anomalies inherit the same envelope so downstream planners can reason about severity and confidence together.

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

## Phase 5 Controls

| Property | Purpose |
|----------|---------|
| `nexus.reasoning.ml.*` | Toggles MlEnhancedInferenceService, model type, calibrator, and LearningSignal lookback |
| `nexus.reasoning.adaptive-rules.*` | Enables the LearningSignal-driven rule/priority adjustment loop |
| `nexus.temporal.uncertainty.*` | Configures the TemporalUncertaintyService calibrator and lookback window |
| `nexus.cache.adaptive.*` | Controls adaptive TTL multipliers derived from cache access tiers |

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

NEXUS maintains resilient, **WebClient-based** clients to all upstream services:

- `WebClientCapsuleClient` → `CapsuleClient`
- `WebClientPerceptionClient` → `PerceptionClient`
- `WebClientOdysseyClient` → `OdysseyClient`
- `WebClientPlatoClient` → `PlatoClient`

All clients share a common foundation:
- `TracePropagatingWebClientFactory` for W3C trace context and correlation ID propagation
- `NexusClientProperties` (`nexus.clients.*`) for URLs and timeouts
- Resilience4j circuit breakers, retries, bulkheads, and time limiters
- Micrometer timers and counters (`nexus.client.*`) for latency and outcome metrics

Example (Perception client):

```java
@Component
@Slf4j
@EnableConfigurationProperties(NexusClientProperties.class)
public class WebClientPerceptionClient implements PerceptionClient {

    private static final String CIRCUIT_BREAKER_NAME = "perception-client";

    private final WebClient webClient;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Duration defaultTimeout;
    private final Duration quickTimeout;

    public WebClientPerceptionClient(
        TracePropagatingWebClientFactory webClientFactory,
        NexusClientProperties properties,
        MeterRegistry meterRegistry,
        CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        NexusClientProperties.ClientConfig config = properties.perception();
        this.webClient = webClientFactory.createClientBuilder(
            config.url(),
            config.connectTimeout(),
            config.readTimeout()
        ).build();

        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.defaultTimeout = config.readTimeout();
        this.quickTimeout = properties.defaults().quickTimeout();
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getRimNodeStateFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public Mono<RimNodeState> getRimNodeState(String rimNodeId) {
        Mono<RimNodeState> call = webClient.get()
            .uri("/api/v1/rim/nodes/{rimNodeId}/state", rimNodeId)
            .retrieve()
            .bodyToMono(RimNodeState.class)
            .timeout(defaultTimeout);
        return instrumentMono("getRimNodeState", call);
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
    configs:
      internal-service:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 15s
        failureRateThreshold: 50
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 1s
    instances:
      capsule-client:
        baseConfig: internal-service
        slowCallDurationThreshold: 3s
      perception-client:
        baseConfig: internal-service
        slowCallDurationThreshold: 3s
      odyssey-client:
        baseConfig: internal-service
        slowCallDurationThreshold: 5s
      plato-client:
        baseConfig: internal-service
        slowCallDurationThreshold: 3s
```

### Retry with Backoff

```yaml
resilience4j:
  retry:
    configs:
      internal-service:
        maxAttempts: 3
        waitDuration: 200ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        exponentialMaxWaitDuration: 2s
    instances:
      capsule-client:
        baseConfig: internal-service
      perception-client:
        baseConfig: internal-service
      odyssey-client:
        baseConfig: internal-service
      plato-client:
        baseConfig: internal-service
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
    # Default timeout settings applied unless overridden per-client
    defaults:
      connect-timeout: 5s
      read-timeout: 30s
      quick-timeout: 5s
      long-timeout: 60s

    # CAPSULE client – historical data
    capsule:
      url: ${CAPSULE_URL:http://localhost:8081}
      connect-timeout: 5s
      read-timeout: 30s
      long-timeout: 60s

    # PERCEPTION client – current state & signals
    perception:
      url: ${PERCEPTION_URL:http://localhost:8082}
      connect-timeout: 5s
      read-timeout: 30s
      long-timeout: 30s

    # ODYSSEY client – projections and futures
    odyssey:
      url: ${ODYSSEY_URL:http://localhost:8083}
      connect-timeout: 5s
      read-timeout: 30s
      long-timeout: 30s

    # PLATO client – governance and plans
    plato:
      url: ${PLATO_URL:http://localhost:8085}
      connect-timeout: 5s
      read-timeout: 30s
      long-timeout: 30s

  temporal:
    default-lookback: P30D
    default-lookahead: P7D
    default-resolution: PT1H
    cache-ttl: PT5M

  evolution:
    learning-rate: 0.01
    optimization-interval: PT1H
    min-samples: 100
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
