# BUTTERFLY API Catalog

> Unified catalog of all API endpoints across the ecosystem

**Last Updated**: 2025-12-04  
**Target Audience**: API consumers, developers, integrators

---

## Overview

This document provides a consolidated view of all REST API endpoints across BUTTERFLY services. For detailed schemas and request/response formats, see the OpenAPI specifications linked in each section.

## Quick Reference

| Service | Default Port | OpenAPI Spec | Swagger UI |
|---------|--------------|--------------|------------|
| PERCEPTION | 8080 | `/v3/api-docs` | `/swagger-ui.html` |
| CAPSULE | 8081 | `/v3/api-docs` | `/swagger-ui.html` |
| ODYSSEY | 8082 | [`odyssey-v1.yaml`](../../ODYSSEY/openapi/odyssey-v1.yaml) | `/swagger-ui.html` |
| PLATO | 8080 | [`plato-v1.yaml`](../../PLATO/openapi/plato-v1.yaml) | `/swagger-ui.html` |
| NEXUS | 8084 | `/v3/api-docs` | `/swagger-ui.html` |

---

## PERCEPTION API (Port 8080)

**OpenAPI Spec**: Available at runtime via `/v3/api-docs`  
**SDK Clients**: [Python](../../PERCEPTION/clients/python/README.md) | [TypeScript](../../PERCEPTION/clients/typescript/README.md)

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
| GET | `/api/v1/integrity/storylines/content/{contentId}` | Get storylines by content ID |
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
| GET | `/api/v1/signals/detected` | Get detected weak signals |
| GET | `/api/v1/signals/detectors` | List active signal detectors |
| GET | `/api/v1/signals/volatility` | Get volatile sources |
| GET | `/api/v1/signals/horizons` | Get event horizons |
| GET | `/api/v1/signals/clusters` | Get signal clusters |
| GET | `/api/v1/signals/clusters/{clusterId}` | Get cluster details |

### Scenarios (Stackable)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/scenarios/stackable` | List stackable scenarios |
| POST | `/api/v1/scenarios/stackable` | Create base scenario |
| GET | `/api/v1/scenarios/stackable/{scenarioId}` | Get scenario by ID |
| DELETE | `/api/v1/scenarios/stackable/{scenarioId}` | Remove scenario layer |
| POST | `/api/v1/scenarios/stackable/{baseId}/layer` | Layer scenario onto base |
| GET | `/api/v1/scenarios/stackable/{scenarioId}/compose` | Compose scenario with all layers |
| GET | `/api/v1/scenarios/precedents` | List historical precedents |
| POST | `/api/v1/scenarios/precedents` | Add precedent snapshot |
| GET | `/api/v1/scenarios/precedents/{id}` | Get precedent by ID |
| GET | `/api/v1/scenarios/precedents/similar/{scenarioId}` | Find similar precedents |

### Feedback

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/feedback/scenarios/{scenarioId}` | Submit feedback |
| GET | `/api/v1/feedback/scenarios/{scenarioId}` | Get feedback history |
| GET | `/api/v1/feedback/scenarios/{scenarioId}/aggregates` | Get feedback aggregates |

### Analysis (Stream C)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/analysis/events/{eventId}` | Get comprehensive event analysis |

### RIM (Reality Integration Mesh)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/rim/nodes` | List RIM nodes |
| GET | `/api/v1/rim/nodes/{nodeId}` | Get RIM node by ID |
| GET | `/api/v1/rim/mesh/snapshot` | Get mesh snapshot |
| GET | `/api/v1/rim/ignorance-surface` | Get ignorance surface |
| GET | `/api/v1/rim/anomalies` | Detect RIM anomalies |

### Monitoring

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/monitoring/health` | Get pipeline health score |
| POST | `/api/v1/monitoring/audit` | Run self-audit |
| GET | `/api/v1/monitoring/telemetry` | Get telemetry data |

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/token` | Exchange API key for JWT |

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

**OpenAPI Spec**: [`ODYSSEY/openapi/odyssey-v1.yaml`](../../ODYSSEY/openapi/odyssey-v1.yaml)

### World Field

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/world/entities` | List entities |
| GET | `/api/v1/world/entities/{id}` | Get entity by ID |
| POST | `/api/v1/world/entities` | Create entity |
| PUT | `/api/v1/world/entities/{id}` | Update entity |
| GET | `/api/v1/world/stress` | Get stress metrics |

### Paths (Narrative)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/paths` | List narrative paths |
| GET | `/api/v1/paths/{id}` | Get path by ID |
| POST | `/api/v1/paths` | Create path |
| PUT | `/api/v1/paths/{id}` | Update path |
| GET | `/api/v1/paths/{id}/branches` | Get branch points |
| GET | `/api/v1/paths/{id}/tipping` | Get tipping conditions |

### Actors

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/actors` | List actor models |
| GET | `/api/v1/actors/{actorId}` | Get actor model |
| POST | `/api/v1/actors` | Create actor |
| PUT | `/api/v1/actors/{actorId}` | Update actor |
| GET | `/api/v1/actors/{actorId}/objectives` | Get actor objectives |
| GET | `/api/v1/actors/{actorId}/constraints` | Get actor constraints |

### Strategy

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/strategy/posture` | Get strategic posture |
| POST | `/api/v1/strategy/synthesize` | Synthesize strategy |
| GET | `/api/v1/strategy/horizons` | Get horizon packs |

### Gameboard Editing

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/gameboard/interventions` | List all interventions |
| POST | `/api/v1/gameboard/interventions` | Create intervention |
| GET | `/api/v1/gameboard/interventions/{id}` | Get intervention |
| PUT | `/api/v1/gameboard/interventions/{id}` | Update intervention |
| DELETE | `/api/v1/gameboard/interventions/{id}` | Delete intervention |
| GET | `/api/v1/gameboard/interventions/active` | List active interventions |
| GET | `/api/v1/gameboard/interventions/overdue` | List overdue interventions |
| POST | `/api/v1/gameboard/interventions/{id}/evaluate` | Start evaluation |
| POST | `/api/v1/gameboard/interventions/{id}/approve` | Approve intervention |
| POST | `/api/v1/gameboard/interventions/{id}/start` | Start implementation |
| POST | `/api/v1/gameboard/interventions/{id}/complete` | Complete intervention |

### Experiments

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/experiments` | Create experiment |
| GET | `/api/v1/experiments/{id}` | Get experiment |
| POST | `/api/v1/experiments/{id}/run` | Run experiment |
| GET | `/api/v1/experiments/{id}/results` | Get results |

### Story Market Radar

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/radar/mispricings` | Get narrative mispricings |
| GET | `/api/v1/radar/trades` | Get trade ideas |

### Advanced Features

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/advanced/temporal/coherence` | Temporal coherence analysis |
| GET | `/api/v1/advanced/regime/precursors` | Regime precursor detection |
| GET | `/api/v1/advanced/cognitive/load/{actorId}` | Actor cognitive load |
| GET | `/api/v1/advanced/narrative/immunity` | Narrative immunity analysis |
| GET | `/api/v1/advanced/dark-matter` | Hidden actor detection |
| GET | `/api/v1/advanced/commitment/topology` | Commitment topology |

### GraphQL

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/graphql` | GraphQL queries |
| GET | `/graphiql` | GraphiQL interface |

---

## PLATO API (Port 8080)

**OpenAPI Spec**: [`PLATO/openapi/plato-v1.yaml`](../../PLATO/openapi/plato-v1.yaml)

### Specs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/specs` | List specifications (with filters) |
| POST | `/api/v1/specs` | Create specification |
| GET | `/api/v1/specs/{specId}` | Get specification |
| PUT | `/api/v1/specs/{specId}` | Update specification |
| DELETE | `/api/v1/specs/{specId}` | Delete specification |
| POST | `/api/v1/specs/{specId}/publish` | Publish specification |
| GET | `/api/v1/specs/{specId}/versions` | Get version history |
| GET | `/api/v1/specs/{specId}/lineage` | Get spec lineage |

### Artifacts

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/artifacts` | List artifacts |
| POST | `/api/v1/artifacts` | Create artifact |
| GET | `/api/v1/artifacts/{artifactId}` | Get artifact |
| PUT | `/api/v1/artifacts/{artifactId}` | Update artifact |
| DELETE | `/api/v1/artifacts/{artifactId}` | Delete artifact |
| GET | `/api/v1/artifacts/{artifactId}/download` | Download artifact |

### Proofs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/proofs` | List proofs |
| POST | `/api/v1/proofs` | Create proof |
| GET | `/api/v1/proofs/{proofId}` | Get proof |
| PUT | `/api/v1/proofs/{proofId}` | Update proof |
| DELETE | `/api/v1/proofs/{proofId}` | Delete proof |
| POST | `/api/v1/proofs/{proofId}/verify` | Verify proof |

### Plans

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/plans` | List plans |
| POST | `/api/v1/plans` | Create plan |
| GET | `/api/v1/plans/{planId}` | Get plan |
| PUT | `/api/v1/plans/{planId}` | Update plan |
| DELETE | `/api/v1/plans/{planId}` | Delete plan |
| POST | `/api/v1/plans/{planId}/execute` | Execute plan |
| GET | `/api/v1/plans/{planId}/status` | Get execution status |
| POST | `/api/v1/plans/{planId}/cancel` | Cancel execution |

### Search (Semantic)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/search/specs` | Semantic search for specs |
| GET | `/api/v1/search/artifacts` | Semantic search for artifacts |
| GET | `/api/v1/search/proofs` | Semantic search for proofs |
| GET | `/api/v1/search/plans` | Semantic search for plans |
| GET | `/api/v1/search/all` | Unified search across all types |
| GET | `/api/v1/search/similar/specs/{specId}` | Find similar specs |
| GET | `/api/v1/search/similar/artifacts/{artifactId}` | Find similar artifacts |
| GET | `/api/v1/search/similar/proofs/{proofId}` | Find similar proofs |

### Intelligence (NLP)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/intelligence/generate-spec` | Generate spec from natural language |
| POST | `/api/v1/intelligence/explain-spec` | Explain a spec |
| POST | `/api/v1/intelligence/summarize-spec` | Summarize a spec |
| POST | `/api/v1/intelligence/explain-diff` | Explain spec differences |

### Governance

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/governance/evaluate` | Evaluate policy |
| GET | `/api/v1/governance/policies` | List policies |
| GET | `/api/v1/governance/policies/{id}` | Get policy |
| GET | `/api/v1/governance/violations` | List violations |
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

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/admin/search/reindex` | Reindex all objects |
| POST | `/api/v1/admin/search/reindex/{objectType}` | Reindex by type |
| GET | `/api/v1/admin/search/stats` | Get index statistics |
| GET | `/api/v1/admin/search/health` | Check vector store health |

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
| GET | `/actuator/health/liveness` | Liveness probe (k8s) |
| GET | `/actuator/health/readiness` | Readiness probe (k8s) |
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

## Authentication

All services support the following authentication methods:

### API Key (Header)
```
X-API-Key: your-api-key
```

### Bearer Token (JWT)
```
Authorization: Bearer your-jwt-token
```

To exchange an API key for a JWT token:
```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"apiKey": "your-api-key"}'
```

---

## Common Query Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `page` | integer | Page number (0-indexed) |
| `size` | integer | Items per page (default: 20) |
| `sort` | string | Sort field and direction (e.g., `createdAt,desc`) |
| `X-Correlation-Id` | string (header) | Request correlation ID for tracing |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Overview](README.md) | API section overview |
| [Authentication](authentication.md) | Detailed auth patterns |
| [Error Handling](error-handling.md) | Error codes and handling |
| [Rate Limiting](rate-limiting.md) | Rate limit policies |
| [First Steps](../getting-started/first-steps.md) | Getting started with APIs |
