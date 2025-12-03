# BUTTERFLY API Catalog

> Unified catalog of all API endpoints across the ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: API consumers, developers

---

## Overview

This document provides a consolidated view of all REST API endpoints across BUTTERFLY services.

---

## PERCEPTION API (Port 8080)

### Acquisition

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/acquisition/content` | Submit content for processing |
| GET | `/api/v1/acquisition/content/{id}` | Get content by ID |
| GET | `/api/v1/acquisition/content/{id}/status` | Check processing status |
| DELETE | `/api/v1/acquisition/content/{id}` | Delete content |
| GET | `/api/v1/acquisition/sources` | List configured sources |
| POST | `/api/v1/acquisition/sources` | Add new source |

### Integrity

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/integrity/trust-scores` | Get trust scores |
| GET | `/api/v1/integrity/trust-scores/{contentId}` | Get trust score for content |
| GET | `/api/v1/integrity/storylines` | List storylines |
| GET | `/api/v1/integrity/storylines/{id}` | Get storyline by ID |
| GET | `/api/v1/integrity/dedup/clusters` | Get deduplication clusters |

### Intelligence

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/intelligence/events` | List detected events |
| GET | `/api/v1/intelligence/events/{id}` | Get event by ID |
| GET | `/api/v1/intelligence/scenarios` | List scenarios |
| GET | `/api/v1/intelligence/scenarios/{id}` | Get scenario by ID |
| POST | `/api/v1/intelligence/scenarios` | Create scenario |

### Signals

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/signals/weak` | List weak signals |
| GET | `/api/v1/signals/weak/{id}` | Get weak signal by ID |
| GET | `/api/v1/signals/volatility` | Get volatility metrics |
| GET | `/api/v1/signals/horizon` | Get horizon scan results |

### RIM (Reality Integration Mesh)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/rim/nodes` | List RIM nodes |
| GET | `/api/v1/rim/nodes/{nodeId}` | Get RIM node by ID |
| GET | `/api/v1/rim/relations` | List relations |
| GET | `/api/v1/rim/ignorance` | Get ignorance surface |

### Monitoring

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/monitoring/health` | Get pipeline health |
| GET | `/api/v1/monitoring/telemetry` | Get telemetry data |
| GET | `/api/v1/monitoring/audit` | Get audit log |

---

## CAPSULE API (Port 8081)

### CAPSULEs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/capsules` | Create a CAPSULE |
| GET | `/api/v1/capsules/{id}` | Get CAPSULE by ID |
| DELETE | `/api/v1/capsules/{id}` | Delete CAPSULE |
| GET | `/api/v1/capsules` | List CAPSULEs (with filters) |

### History

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/history` | Query history (scopeId, from, to) |
| GET | `/api/v1/history/{scopeId}` | Get entity history |
| GET | `/api/v1/history/{scopeId}/latest` | Get latest CAPSULE |
| GET | `/api/v1/history/{scopeId}/range` | Get time range |

### Lineage

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/lineage/{capsuleId}` | Get CAPSULE lineage |
| GET | `/api/v1/lineage/{capsuleId}/ancestors` | Get ancestors |
| GET | `/api/v1/lineage/{capsuleId}/descendants` | Get descendants |

---

## ODYSSEY API (Port 8082)

### World State

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/state/{nodeId}` | Get entity state |
| GET | `/api/v1/state/{nodeId}/relations` | Get entity relations |
| GET | `/api/v1/state/regime/{regime}` | Get entities in regime |
| GET | `/api/v1/state/stress` | Get stress indicators |

### Paths

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/paths/{nodeId}` | Get path projections |
| GET | `/api/v1/paths/{nodeId}/scenarios` | Get scenarios |
| POST | `/api/v1/paths/query` | Complex path query |
| GET | `/api/v1/paths/branches` | Get branch points |

### Actors

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/actors` | List actor models |
| GET | `/api/v1/actors/{actorId}` | Get actor model |
| GET | `/api/v1/actors/{actorId}/objectives` | Get actor objectives |
| GET | `/api/v1/actors/{actorId}/strategies` | Get actor strategies |

### Experiments

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/experiments` | Create experiment |
| GET | `/api/v1/experiments/{id}` | Get experiment |
| POST | `/api/v1/experiments/{id}/run` | Run experiment |
| GET | `/api/v1/experiments/{id}/results` | Get results |

### Ignorance

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/ignorance` | Get ignorance surface |
| GET | `/api/v1/ignorance/low-data` | Get low-data regions |
| GET | `/api/v1/ignorance/weak-models` | Get weak models |

### GraphQL

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/graphql` | GraphQL queries |
| GET | `/graphiql` | GraphiQL interface |

---

## PLATO API (Port 8083)

### Specs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/specs` | Create specification |
| GET | `/api/v1/specs/{id}` | Get specification |
| PUT | `/api/v1/specs/{id}` | Update specification |
| DELETE | `/api/v1/specs/{id}` | Delete specification |
| GET | `/api/v1/specs` | List specifications |
| POST | `/api/v1/specs/{id}/publish` | Publish specification |

### Artifacts

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/artifacts` | Create artifact |
| GET | `/api/v1/artifacts/{id}` | Get artifact |
| GET | `/api/v1/artifacts` | List artifacts |
| GET | `/api/v1/artifacts/{id}/download` | Download artifact |

### Proofs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/proofs` | Create proof |
| GET | `/api/v1/proofs/{id}` | Get proof |
| GET | `/api/v1/proofs` | List proofs |
| POST | `/api/v1/proofs/{id}/verify` | Verify proof |

### Plans

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/plans` | Create plan |
| GET | `/api/v1/plans/{id}` | Get plan |
| PUT | `/api/v1/plans/{id}` | Update plan |
| DELETE | `/api/v1/plans/{id}` | Delete plan |
| POST | `/api/v1/plans/{id}/execute` | Execute plan |
| GET | `/api/v1/plans/{id}/status` | Get execution status |
| POST | `/api/v1/plans/{id}/cancel` | Cancel execution |

### Governance

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/governance/evaluate` | Evaluate policy |
| GET | `/api/v1/governance/policies` | List policies |
| GET | `/api/v1/governance/policies/{id}` | Get policy |
| GET | `/api/v1/governance/audit` | Get audit log |

### Engines

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/engines/synthplane/synthesize` | Program synthesis |
| POST | `/api/v1/engines/featureforge/engineer` | Feature engineering |
| POST | `/api/v1/engines/lawminer/discover` | Equation discovery |
| POST | `/api/v1/engines/specminer/mine` | Spec mining |
| POST | `/api/v1/engines/researchos/research` | Research query |
| POST | `/api/v1/engines/evidenceplanner/plan` | Evidence planning |

### WebSocket

| Endpoint | Description |
|----------|-------------|
| `ws://.../ws/plans/{planId}` | Plan execution streaming |

---

## NEXUS API (Port 8084)

### Temporal

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/temporal/slice/{nodeId}` | Get temporal slice |
| POST | `/api/v1/temporal/query` | Complex temporal query |
| GET | `/api/v1/temporal/causal/{nodeId}` | Get causal chains |
| GET | `/api/v1/temporal/anomalies` | Get temporal anomalies |

### Reasoning

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/reasoning/infer` | Cross-system inference |
| GET | `/api/v1/reasoning/contradictions` | Get contradictions |
| POST | `/api/v1/reasoning/reconcile` | Reconcile beliefs |

### Synthesis

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/synthesis/options/{nodeId}` | Get strategic options |
| GET | `/api/v1/synthesis/ignorance` | Cross-system ignorance |
| POST | `/api/v1/synthesis/score` | Score options |

### Evolution

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/evolution/metrics` | Get evolution metrics |
| GET | `/api/v1/evolution/patterns` | Get learned patterns |
| GET | `/api/v1/evolution/improvements` | Get improvements |

---

## Common Endpoints (All Services)

### Health and Monitoring

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Service health |
| GET | `/actuator/health/liveness` | Liveness probe |
| GET | `/actuator/health/readiness` | Readiness probe |
| GET | `/actuator/info` | Service info |
| GET | `/actuator/metrics` | Metrics index |
| GET | `/actuator/prometheus` | Prometheus metrics |

### Documentation

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/swagger-ui.html` | Swagger UI |
| GET | `/v3/api-docs` | OpenAPI spec (JSON) |
| GET | `/v3/api-docs.yaml` | OpenAPI spec (YAML) |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Overview](README.md) | API section overview |
| [Authentication](authentication.md) | Auth patterns |
| [Error Handling](error-handling.md) | Error codes |
| [Rate Limiting](rate-limiting.md) | Rate limits |

