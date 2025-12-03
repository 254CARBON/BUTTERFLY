# Strategic Scenario: EURUSD Market Shock

> Golden path reference implementation for the BUTTERFLY cognitive pipeline

**Last Updated**: 2025-12-03  
**Status**: Sprint 4 Implementation  
**Target Audience**: Developers, architects, QA

---

## Executive Summary

This document defines a **single, end-to-end strategic scenario** that exercises all major BUTTERFLY services—PERCEPTION → CAPSULE → ODYSSEY → PLATO → NEXUS—demonstrating the intended cognitive pipeline and serving as the canonical golden path for integration testing.

**Scenario**: A **EURUSD liquidity/volatility shock** triggered by a surprise central bank decision, flowing through the system to produce strategic options for risk mitigation.

---

## Scenario Narrative

### The External Stimulus

A surprise policy decision by the European Central Bank (ECB) triggers immediate market reaction:

1. **Event**: ECB announces an unexpected 50bp rate cut during an unscheduled press conference
2. **Market Impact**: EURUSD experiences sudden volatility spike with spread blow-out
3. **Observable Signals**:
   - Bid-ask spread widens from 0.0002 to 0.0015
   - Volume spikes 5x above hourly average
   - Stress indicator jumps to 0.92
   - Order book imbalance reaches critical threshold

### RIM Node Identity

The primary entity tracked throughout this scenario:

```
rimNodeId: rim:entity:finance:EURUSD
```

Components:
- **nodeType**: `entity` (a trackable thing in the world model)
- **namespace**: `finance` (financial markets domain)
- **localId**: `EURUSD` (EUR/USD currency pair)

---

## Service Responsibilities

### Stage 1: PERCEPTION — Sensory Layer

**Responsibility**: Detect and emit the market shock event with trust scoring.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PERCEPTION Stage                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: Real-time market data feed (price, volume, spread)                  │
│                                                                              │
│  Processing:                                                                 │
│  1. RIM Fast-Path detects stress threshold breach (stress >= 0.8)          │
│  2. Intelligence layer classifies event as MARKET_SHOCK                    │
│  3. Trust score computed based on source reliability                        │
│  4. Impact estimate generated: { magnitude: 0.85, direction: "DOWN" }      │
│                                                                              │
│  Output:                                                                     │
│  • RimFastEvent → rim.fast-path (for ODYSSEY reflex)                       │
│  • PerceptionEvent → perception.events (for downstream consumers)          │
│                                                                              │
│  Key Fields:                                                                 │
│  • correlationId: corr-strategic-fx-shock-{uuid}                           │
│  • rimNodeId: rim:entity:finance:EURUSD                                    │
│  • eventType: MARKET                                                        │
│  • trustScore: 0.94                                                         │
│  • stress: 0.92                                                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Kafka Messages Produced**:

| Topic | Schema | Key | Purpose |
|-------|--------|-----|---------|
| `rim.fast-path` | RimFastEvent | `rim:entity:finance:EURUSD` | Low-latency signal for ODYSSEY reflex |
| `perception.events` | PerceptionEvent | `{correlationId}` | Full event for CAPSULE, ODYSSEY, PLATO |

**RimFastEvent Payload** (for `rim.fast-path`):

```json
{
  "rim_node_id": "rim:entity:finance:EURUSD",
  "event_id": "evt-fx-shock-20251203-001",
  "timestamp": 1733234400000,
  "critical_metrics": {
    "bid": 1.0755,
    "ask": 1.0770,
    "spread": 0.0015,
    "volume_ratio": 5.2
  },
  "price": 1.0762,
  "volume": 8500000.0,
  "stress": 0.92,
  "status": "ALERT",
  "source_topic": "market-data.fx.spot"
}
```

**PerceptionEvent Payload** (for `perception.events`):

```json
{
  "event_id": "evt-fx-shock-20251203-001",
  "event_type": "MARKET",
  "timestamp": 1733234400000,
  "detected_at": 1733234400050,
  "title": "ECB Emergency Rate Cut - EURUSD Volatility Spike",
  "summary": "Surprise 50bp rate cut announced by ECB triggers immediate EURUSD volatility. Spread widened 7.5x, volume 5x above average.",
  "entities": ["rim:entity:finance:EURUSD", "rim:actor:institution:ECB"],
  "trust_score": 0.94,
  "impact_estimate": {
    "magnitude": 0.85,
    "direction": "DOWN",
    "confidence": 0.88
  },
  "sources": ["reuters", "bloomberg", "ecb-press"],
  "metadata": {
    "correlationId": "corr-strategic-fx-shock-{uuid}",
    "tenantId": "butterfly-core",
    "eventCategory": "CENTRAL_BANK_ACTION"
  }
}
```

---

### Stage 2: CAPSULE — Memory Layer

**Responsibility**: Persist 4D history snapshot capturing the state transition.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CAPSULE Stage                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: PerceptionEvent from perception.events topic                        │
│                                                                              │
│  Processing:                                                                 │
│  1. Receive event for rim:entity:finance:EURUSD                            │
│  2. Construct CAPSULE with 4D structure (C/D/A/M)                          │
│  3. Generate idempotency key: SHA256(nodeId+ts+resolution+vantage)         │
│  4. Persist to Cassandra (capsules_by_scope_time table)                    │
│  5. Emit notification on capsule.snapshots                                  │
│                                                                              │
│  Output:                                                                     │
│  • CAPSULE stored with scope_id = rim:entity:finance:EURUSD                │
│  • CapsuleSnapshot event → capsule.snapshots                               │
│                                                                              │
│  4D Structure:                                                               │
│  ┌─────────────────┬─────────────────┬─────────────────┬─────────────────┐ │
│  │  Configuration  │    Dynamics     │     Agency      │      Meta       │ │
│  │─────────────────│─────────────────│─────────────────│─────────────────│ │
│  │ • price: 1.0762 │ • velocity: -45 │ • ECB: seller   │ • trust: 0.94   │ │
│  │ • spread: 15pips│ • stress: 0.92  │ • intent: dovish│ • source: PERC  │ │
│  │ • volume: 8.5M  │ • regime: CRISIS│                 │ • corrId: ...   │ │
│  └─────────────────┴─────────────────┴─────────────────┴─────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**CAPSULE Structure** (stored in Cassandra):

```json
{
  "capsule_id": "550e8400-e29b-41d4-a716-446655440000",
  "scope_id": "rim:entity:finance:EURUSD",
  "center_ts_utc": "2025-12-03T14:00:00Z",
  "vantage_mode": "REAL",
  "observer_id": "perception-service",
  "tags": ["market-shock", "central-bank", "fx-major"],
  "lineage_key": "sha256:abc123...",
  "configuration": {
    "price": 1.0762,
    "bid": 1.0755,
    "ask": 1.0770,
    "spread_pips": 15,
    "volume": 8500000,
    "market_status": "ACTIVE"
  },
  "dynamics": {
    "price_velocity": -45.0,
    "stress": 0.92,
    "volatility": 0.034,
    "regime": "CRISIS",
    "momentum": -0.78
  },
  "agency": {
    "primary_actor": "rim:actor:institution:ECB",
    "actor_stance": "DOVISH",
    "market_sentiment": "RISK_OFF",
    "order_flow_bias": "SELL"
  },
  "meta": {
    "trust_score": 0.94,
    "source_service": "PERCEPTION",
    "correlation_id": "corr-strategic-fx-shock-{uuid}",
    "provenance": ["reuters", "bloomberg", "ecb-press"],
    "quality_flags": []
  }
}
```

**CapsuleSnapshot Event** (for `capsule.snapshots`):

```json
{
  "capsule_id": "550e8400-e29b-41d4-a716-446655440000",
  "scope_id": "rim:entity:finance:EURUSD",
  "timestamp": 1733234400000,
  "resolution": 60,
  "vantage_mode": "REAL",
  "idempotency_key": "sha256:abc123...",
  "created_at": 1733234400100
}
```

**Query Surface for NEXUS**:

```
GET /api/v1/history?scopeId=rim:entity:finance:EURUSD&from=2025-12-03T13:00:00Z&to=2025-12-03T15:00:00Z
```

---

### Stage 3: ODYSSEY — Cognition Layer

**Responsibility**: Update world-story and generate path projections.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ODYSSEY Stage                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input:                                                                      │
│  • RimFastEvent from rim.fast-path (reflex loop)                           │
│  • CapsuleSnapshot from capsule.snapshots (state update)                   │
│                                                                              │
│  Processing:                                                                 │
│  1. Reflex Service triggers on high-stress event (stress >= 0.8)           │
│  2. World-Story State updated:                                              │
│     - Entity stress level: CRITICAL                                         │
│     - Regime transition: NORMAL → CRISIS                                    │
│     - Buffer capacity: DEPLETED                                             │
│  3. Path Engine generates narrative futures:                                │
│     - volatility_breakout (P=0.55)                                         │
│     - range_bound (P=0.25)                                                  │
│     - trend_continuation (P=0.20)                                          │
│  4. Actor Engine updates ECB player model                                   │
│                                                                              │
│  Output:                                                                     │
│  • PathUpdate → odyssey.paths                                               │
│  • ActorUpdate → odyssey.actors                                             │
│  • HTTP: GET /api/v1/paths/{nodeId} available for NEXUS                    │
│                                                                              │
│  Reflex Action:                                                              │
│  • Generate PREDICTIVE CAPSULE for t+1h, t+4h, t+24h                       │
│  • Emit ReflexActionEvent for monitoring                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**PathUpdate Event** (for `odyssey.paths`):

```json
{
  "node_id": "rim:entity:finance:EURUSD",
  "timestamp": 1733234400200,
  "correlation_id": "corr-strategic-fx-shock-{uuid}",
  "paths": [
    {
      "scenario_id": "volatility_breakout",
      "probability": 0.55,
      "horizon_hours": 24,
      "description": "Continued volatility with potential test of 1.0650 support",
      "key_drivers": ["ECB policy divergence", "risk-off sentiment"],
      "confidence": 0.82
    },
    {
      "scenario_id": "range_bound",
      "probability": 0.25,
      "horizon_hours": 24,
      "description": "Market stabilizes within 1.0720-1.0820 range after initial shock",
      "key_drivers": ["profit taking", "technical support"],
      "confidence": 0.71
    },
    {
      "scenario_id": "trend_continuation",
      "probability": 0.20,
      "horizon_hours": 24,
      "description": "Sustained move lower toward 1.0500 on follow-through selling",
      "key_drivers": ["institutional repositioning", "momentum algorithms"],
      "confidence": 0.65
    }
  ],
  "world_story_state": {
    "entity_stress": "CRITICAL",
    "regime": "CRISIS",
    "buffer_status": "DEPLETED",
    "ignorance_surface": {
      "low_data_regions": ["interbank_liquidity", "options_gamma"],
      "confidence_floor": 0.60
    }
  }
}
```

**HTTP Endpoint for NEXUS**:

```
GET /api/v1/paths/rim:entity:finance:EURUSD

Response:
{
  "nodeId": "rim:entity:finance:EURUSD",
  "timestamp": "2025-12-03T14:00:00.200Z",
  "paths": [...],
  "worldStoryState": {...}
}
```

---

### Stage 4: PLATO — Governance Layer

**Responsibility**: Evaluate governance constraints and generate executable plan.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             PLATO Stage                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: Context from NEXUS containing:                                       │
│  • CAPSULE history (past state)                                             │
│  • ODYSSEY paths (future projections)                                       │
│  • Governance spec ID: plato:spec:governance:fx-risk-limit                 │
│                                                                              │
│  Processing:                                                                 │
│  1. Load Spec: fx-risk-limit (FX exposure thresholds and policies)         │
│  2. Evaluate context against spec constraints:                              │
│     - Stress threshold: 0.92 > 0.80 limit → VIOLATION                      │
│     - Exposure limit: current exposure assessed                             │
│  3. Generate Plan: plato:plan:fx:eurusd-shock-mitigation                   │
│  4. Return governance-aware recommendations                                  │
│                                                                              │
│  Output:                                                                     │
│  • Plan evaluation result with recommended actions                          │
│  • GovernanceEvent → plato.governance (audit)                              │
│  • PlanEvent → plato.plans (audit)                                         │
│                                                                              │
│  Spec: plato:spec:governance:fx-risk-limit                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ • stress_threshold: 0.80                                             │   │
│  │ • max_drawdown_pct: 5.0                                              │   │
│  │ • volatility_limit: 0.025                                            │   │
│  │ • required_actions: [reduce_exposure, increase_hedges]               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Governance Spec** (seeded in registry):

```json
{
  "specId": "plato:spec:governance:fx-risk-limit",
  "name": "FX Risk Limit Policy",
  "version": "1.0.0",
  "specType": "GOVERNANCE",
  "status": "ACTIVE",
  "namespace": "governance",
  "body": {
    "constraints": {
      "stress_threshold": 0.80,
      "max_drawdown_pct": 5.0,
      "volatility_limit": 0.025,
      "concentration_limit_pct": 25.0
    },
    "actions_on_breach": [
      {
        "condition": "stress > stress_threshold",
        "action": "REDUCE_EXPOSURE",
        "priority": "HIGH"
      },
      {
        "condition": "volatility > volatility_limit",
        "action": "INCREASE_HEDGES",
        "priority": "HIGH"
      }
    ]
  }
}
```

**Plan** (generated for mitigation):

```json
{
  "planId": "plato:plan:fx:eurusd-shock-mitigation",
  "name": "EURUSD Shock Mitigation Plan",
  "status": "EVALUATED",
  "implementsSpec": "plato:spec:governance:fx-risk-limit",
  "triggerContext": {
    "rimNodeId": "rim:entity:finance:EURUSD",
    "correlationId": "corr-strategic-fx-shock-{uuid}",
    "currentStress": 0.92,
    "violatedConstraints": ["stress_threshold"]
  },
  "steps": [
    {
      "stepId": "step-1",
      "action": "REDUCE_EXPOSURE",
      "description": "Reduce net EURUSD exposure by 30%",
      "priority": "HIGH",
      "estimatedImpact": {
        "stress_reduction": 0.15,
        "cost_estimate_bps": 5
      }
    },
    {
      "stepId": "step-2",
      "action": "INCREASE_HEDGES",
      "description": "Purchase protective puts at 1.0650 strike",
      "priority": "MEDIUM",
      "estimatedImpact": {
        "downside_protection_pct": 2.0,
        "premium_cost_bps": 12
      }
    }
  ],
  "evaluation": {
    "decision": "RECOMMEND_ACTION",
    "confidence": 0.89,
    "proofArtifacts": ["proof-fx-stress-001"]
  }
}
```

**API Endpoint** (called by NEXUS):

```
POST /api/v1/plans/evaluate-strategic-context

Request:
{
  "specId": "plato:spec:governance:fx-risk-limit",
  "context": {
    "rimNodeId": "rim:entity:finance:EURUSD",
    "correlationId": "corr-strategic-fx-shock-{uuid}",
    "currentState": { /* from CAPSULE */ },
    "futureProjections": { /* from ODYSSEY */ }
  }
}

Response:
{
  "planId": "plato:plan:fx:eurusd-shock-mitigation",
  "evaluation": {...},
  "steps": [...]
}
```

---

### Stage 5: NEXUS — Integration Layer

**Responsibility**: Orchestrate cross-service queries and synthesize strategic options.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             NEXUS Stage                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Input: HTTP request for strategic options                                   │
│  GET /api/v1/synthesis/options/rim:entity:finance:EURUSD                   │
│                                                                              │
│  Processing:                                                                 │
│  1. Generate/propagate correlationId                                        │
│  2. Parallel data fetching via service clients:                             │
│     ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│     │CapsuleClient │  │OdysseyClient │  │PerceptionCl. │                   │
│     │ (Past)       │  │ (Future)     │  │ (Present)    │                   │
│     └──────────────┘  └──────────────┘  └──────────────┘                   │
│  3. Build TemporalSlice with coherence analysis                             │
│  4. Call PLATO for governance evaluation                                    │
│  5. Synthesize strategic options                                            │
│                                                                              │
│  Output:                                                                     │
│  • StrategicOptionsResponse with 1-3 actionable options                    │
│  • Each option tagged with governance status and confidence                 │
│                                                                              │
│  Resilience:                                                                 │
│  • Circuit breakers per upstream (capsule, odyssey, perception, plato)     │
│  • Retry with exponential backoff (3 attempts, 500ms base)                 │
│  • Timeout per client: 5s                                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Strategic Options Response**:

```json
{
  "nodeId": "rim:entity:finance:EURUSD",
  "correlationId": "corr-strategic-fx-shock-{uuid}",
  "timestamp": "2025-12-03T14:00:01Z",
  "temporalSlice": {
    "past": {
      "capsuleCount": 24,
      "timeRange": {
        "from": "2025-12-03T13:00:00Z",
        "to": "2025-12-03T14:00:00Z"
      },
      "trendSummary": "Stable until 13:58, then rapid deterioration"
    },
    "present": {
      "stress": 0.92,
      "regime": "CRISIS",
      "trustScore": 0.94
    },
    "futures": [
      {"scenario": "volatility_breakout", "probability": 0.55},
      {"scenario": "range_bound", "probability": 0.25},
      {"scenario": "trend_continuation", "probability": 0.20}
    ]
  },
  "governanceStatus": {
    "specId": "plato:spec:governance:fx-risk-limit",
    "violations": ["stress_threshold"],
    "decision": "RECOMMEND_ACTION"
  },
  "strategicOptions": [
    {
      "optionId": "opt-1-reduce-exposure",
      "title": "Reduce EURUSD Exposure",
      "description": "Immediately reduce net EURUSD position by 30% to bring stress within policy limits",
      "priority": "HIGH",
      "expectedOutcome": {
        "stress_after": 0.77,
        "cost_bps": 5
      },
      "governanceAligned": true,
      "confidence": 0.89,
      "linkedPlanId": "plato:plan:fx:eurusd-shock-mitigation",
      "sourceServices": ["CAPSULE", "ODYSSEY", "PLATO"]
    },
    {
      "optionId": "opt-2-hedge-downside",
      "title": "Purchase Downside Protection",
      "description": "Buy protective puts at 1.0650 strike to limit further downside exposure",
      "priority": "MEDIUM",
      "expectedOutcome": {
        "max_loss_limited_to_pct": 1.0,
        "premium_cost_bps": 12
      },
      "governanceAligned": true,
      "confidence": 0.82,
      "linkedPlanId": "plato:plan:fx:eurusd-shock-mitigation",
      "sourceServices": ["ODYSSEY", "PLATO"]
    },
    {
      "optionId": "opt-3-wait-and-monitor",
      "title": "Monitor for Stabilization",
      "description": "Hold current positions while monitoring for signs of market stabilization in next 2-4 hours",
      "priority": "LOW",
      "expectedOutcome": {
        "probability_of_recovery": 0.25,
        "risk_of_further_loss_pct": 2.0
      },
      "governanceAligned": false,
      "governanceNote": "Exceeds stress threshold policy - requires explicit override",
      "confidence": 0.65,
      "linkedPlanId": null,
      "sourceServices": ["ODYSSEY"]
    }
  ],
  "coherence": {
    "score": 0.88,
    "anomalies": [],
    "dataFreshness": {
      "capsule_lag_ms": 100,
      "odyssey_lag_ms": 200,
      "perception_lag_ms": 50
    }
  },
  "meta": {
    "processingTime_ms": 450,
    "servicesQueried": ["CAPSULE", "PERCEPTION", "ODYSSEY", "PLATO"],
    "circuitBreakerStatus": {
      "capsule": "CLOSED",
      "perception": "CLOSED",
      "odyssey": "CLOSED",
      "plato": "CLOSED"
    }
  }
}
```

---

## Correlation ID Flow

All services must propagate the correlation ID for end-to-end tracing:

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                       Correlation ID Propagation                               │
├───────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  Format: corr-strategic-fx-shock-{uuid}                                       │
│  Example: corr-strategic-fx-shock-550e8400-e29b-41d4-a716-446655440000        │
│                                                                                │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐│
│  │PERCEPTION│───▶│ CAPSULE  │───▶│ ODYSSEY  │───▶│  PLATO   │───▶│  NEXUS   ││
│  │          │    │          │    │          │    │          │    │          ││
│  │ Generate │    │ Forward  │    │ Forward  │    │ Forward  │    │ Return   ││
│  │ corrId   │    │ in event │    │ in event │    │ in resp  │    │ in resp  ││
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘│
│                                                                                │
│  Kafka Headers:                                                                │
│  • correlationId: corr-strategic-fx-shock-{uuid}                              │
│  • traceparent: 00-{traceId}-{spanId}-01 (W3C Trace Context)                 │
│                                                                                │
│  MDC Fields (per OBSERVABILITY_GUIDE.md):                                     │
│  • correlationId, traceId, spanId, tenantId, service                         │
│                                                                                │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## Resilience Expectations

### Circuit Breakers

| Client | Failure Threshold | Wait Duration | Purpose |
|--------|-------------------|---------------|---------|
| capsule | 50% of 10 calls | 30s | Protect from CAPSULE outages |
| perception | 50% of 10 calls | 30s | Protect from PERCEPTION outages |
| odyssey | 50% of 10 calls | 30s | Protect from ODYSSEY outages |
| plato | 50% of 10 calls | 30s | Protect from PLATO outages |

### Retry Policy

| Client | Max Attempts | Base Delay | Multiplier | Max Delay |
|--------|--------------|------------|------------|-----------|
| All | 3 | 500ms | 2x | 5s |

### DLQ Topics

| Source Topic | DLQ Topic | Retention |
|--------------|-----------|-----------|
| `rim.fast-path` | `rim.fast-path.dlq` | 90 days |
| `perception.events` | `perception.events.dlq` | 90 days |
| `capsule.snapshots` | `capsule.snapshots.dlq` | 90 days |

### Expected Failure Modes

| Failure | Detection | Mitigation |
|---------|-----------|------------|
| CAPSULE unavailable | Circuit breaker opens | Return partial slice (present+future only) |
| ODYSSEY timeout | Retry with backoff | Degrade to historical-only options |
| PLATO 503 | Retry x3 | Return options without governance scoring |
| Kafka partition unavailable | Message lands on DLQ | Replay from DLQ after recovery |

---

## Testing Strategy

### Golden Path Test

The end-to-end test (`StrategicScenarioGoldenPathIntegrationTest`) validates:

1. **Event Ingestion**: Publish `strategic-fx-shock` event to `perception.events` or `rim.fast-path`
2. **CAPSULE Persistence**: Poll for CAPSULE snapshot at `rim:entity:finance:EURUSD`
3. **ODYSSEY Projection**: Verify `odyssey.paths` record exists for the node
4. **NEXUS Synthesis**: Call `GET /api/v1/synthesis/options/{nodeId}` and assert:
   - At least one `StrategicOption` returned
   - `governanceStatus` includes evaluation from PLATO
   - `correlationId` matches original event

### Resilience Test

Lightweight assertions incorporated into golden path:

- Inject transient 503 from PLATO (1x), verify NEXUS retries and succeeds
- Verify correlation ID flows through all service logs

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Ecosystem Overview](../architecture/ecosystem-overview.md) | Full system architecture |
| [Data Flow](../architecture/data-flow.md) | Cross-service data flows |
| [Kafka Contracts](kafka-contracts.md) | Topic and schema catalog |
| [Identity Model](../architecture/identity-model.md) | RimNodeId specification |
| [Client Guide](client-guide.md) | Integration patterns |
| [Observability Guide](../OBSERVABILITY_GUIDE.md) | Tracing and metrics |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2025-12-03 | Sprint 4 | Initial scenario definition |

