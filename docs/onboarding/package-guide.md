# PLATO Package Guide

> Deep dive into PLATO's engine/, governance/, integration/, and security/ packages

**Last Updated**: 2025-12-03

---

## Overview

PLATO is organized into several top-level packages, each with a specific responsibility:

```
com.z254.butterfly.plato/
├── api/           # REST controllers and WebSocket handlers
├── cache/         # Caching infrastructure
├── config/        # Spring configuration
├── domain/        # Domain models and repositories
├── engine/        # Six PLATO engines
├── event/         # Event publishing and broadcasting
├── execution/     # Plan execution framework
├── governance/    # Control plane and policies
├── health/        # Health indicators
├── identity/      # ID types (PlatoObjectId)
├── infrastructure/# Cassandra, Kafka, JanusGraph adapters
├── integration/   # External service clients
├── observability/ # Metrics, tracing, logging
├── security/      # Authentication and authorization
└── service/       # Business logic services
```

---

## Engine Package (`engine/`)

The `engine/` package contains PLATO's six specialized engines:

### SynthPlane (`engine/synthplane/`)

**Purpose**: Program synthesis - generates transforms from specifications.

| Class | Description |
|-------|-------------|
| `SynthPlaneService` | Service interface for synthesis operations |
| `SqlSynthesizer` | SQL transform generation from TransformSpecs |
| `SchemaDriftDetector` | Detects schema changes requiring adaptation |
| `SqlSynthesisProofGenerator` | Generates proofs for synthesized SQL |

**Key operations:**
- `synthesize(specId, options)` → Generates SQL/WASM transforms
- `adapt(artifactId, newSchemaSpecId)` → Adapts to schema drift

### FeatureForge (`engine/featureforge/`)

**Purpose**: Feature engineering - discovers and evaluates ML features.

| Class | Description |
|-------|-------------|
| `FeatureForgeService` | Service interface for feature operations |
| `FeatureDiscoveryService` | Generates candidate features |
| `NsgaIiOptimizer` | Multi-objective feature selection |
| `FairnessAuditor` | Demographic parity, equalized odds checks |
| `FeatureStoreAdapter` | Abstract adapter for external stores |

**Key operations:**
- `generateFeatures(taskSpecId, datasetSpecId)` → Discovers features
- `evaluateFeature(featureId, evaluationSpecId)` → Produces fairness proofs

### LawMiner (`engine/lawminer/`)

**Purpose**: Equation discovery - finds mathematical relationships in data.

| Class | Description |
|-------|-------------|
| `LawMinerService` | Service interface for equation discovery |
| `LawMinerOrchestrator` | Pipeline coordination with metrics |
| `SymbolicRegressor` | Interface for regression algorithms |
| `GeneticProgrammingRegressor` | GP-based symbolic regression |
| `SindySparseRegressor` | SINDy for dynamical systems |
| `DimensionalAnalyzer` | Unit consistency checking |
| `InvariantMiner` | Conservation law discovery |

**Key models:**
- `LawExpression` - Sealed interface-based expression tree
- `DiscoveredEquation` - Equation with quality metrics
- `PhysicalUnit` - SI-based dimensional analysis

### SpecMiner (`engine/specminer/`)

**Purpose**: Specification mining - extracts contracts from existing systems.

| Class | Description |
|-------|-------------|
| `SpecMinerService` | Service interface for spec mining |
| `DataContractMiner` | Mines data contracts from schemas |
| `TemporalRuleMiner` | Discovers temporal patterns |
| `SpecHardener` | Strengthens specs with invariants |

### ResearchOS (`engine/researchos/`)

**Purpose**: Research orchestration - manages hypothesis-to-paper lifecycle.

| Class | Description |
|-------|-------------|
| `ResearchOSService` | Service interface for study operations |
| `ResearchOSEngine` | Study lifecycle implementation |
| `StudyLifecycleService` | State machine management |
| `HypothesisSpecView` | Typed view over hypothesis content |

**Study lifecycle states:**
`PROPOSED → DESIGNED → REGISTERED → EXECUTING → ANALYZED → COMPLETED`

### EvidencePlanner (`engine/evidenceplanner/`)

**Purpose**: Evidence planning - optimal experiment design using Bayesian methods.

| Subpackage | Description |
|------------|-------------|
| `belief/` | Belief state and probability distributions |
| `information/` | Expected information gain calculation |
| `optimization/` | Experiment selection and budget allocation |
| `tracking/` | Decision quality and regret analysis |

**Key classes:**
- `BeliefState` - Immutable belief state with Bayesian updating
- `InformationGainCalculator` - Monte Carlo EIG computation
- `SequentialDesigner` - Adaptive experiment planning
- `BudgetAllocator` - Resource-constrained selection

---

## Governance Package (`governance/`)

The `governance/` package implements the Control Plane for policy enforcement.

### DSL Subpackage (`governance/dsl/`)

**Purpose**: Meta-Spec Definition Language for declarative policies.

| Class | Description |
|-------|-------------|
| `MetaSpecDSL` | Builder API for policy definition |
| `PolicyExpression` | Sealed interface for expression tree |
| `PolicyDefinition` | Immutable validated policy |
| `PolicyScope` | Defines applicable objects/namespaces |
| `Conditions` | Condition builders (field, status, proof) |
| `Constraints` | Constraint builders (require, limit, approval) |
| `Enforcement` | Modes: BLOCK, WARN, AUDIT |

**Example usage:**
```java
PolicyDefinition policy = MetaSpecDSL.policy("require-proof")
    .description("All deployed artifacts must have proofs")
    .scope(PolicyScope.forType(ObjectType.ARTIFACT))
    .when(Conditions.statusEquals(ArtifactStatus.DEPLOYED))
    .require(Constraints.hasProof())
    .enforcement(Enforcement.BLOCK)
    .build();
```

### Engine Subpackage (`governance/engine/`)

**Purpose**: Policy evaluation with caching and explanations.

| Class | Description |
|-------|-------------|
| `PolicyEngine` | Interface for policy evaluation |
| `PolicyEngineImpl` | Rule matching with LRU cache |
| `EvaluationResult` | Pass/fail with violations and explanations |
| `EvaluationContext` | Context for rule evaluation |
| `PolicyExpressionEvaluator` | Expression tree evaluator |
| `PolicyCache` | LRU cache for compiled policies |

### Violation Subpackage (`governance/violation/`)

**Purpose**: Violation detection and persistence.

| Class | Description |
|-------|-------------|
| `ViolationDetectorImpl` | Real-time detection with Cassandra |
| `ViolationEntity` | Cassandra entity for violations |
| `ViolationByObjectEntity` | Secondary index entity |

### Alert Subpackage (`governance/alert/`)

**Purpose**: Multi-channel violation notifications.

| Class | Description |
|-------|-------------|
| `ViolationAlertService` | Interface for alert delivery |
| `ViolationAlertServiceImpl` | Multi-channel implementation |
| `AlertChannel` | Interface for channels |
| `KafkaAlertChannel` | Kafka-based alerts |
| `WebhookAlertChannel` | Webhook-based alerts |
| `LogAlertChannel` | Log-based alerts |
| `AlertThrottler` | Rate limiting |

### Constitutional Subpackage (`governance/constitutional/`)

**Purpose**: Pre-built safety, fairness, resource, and audit constraints.

| Class | Description |
|-------|-------------|
| `ConstitutionalConstraints` | Factory for standard constraints |
| `SafetyConstraints` | PII protection, banned operations |
| `FairnessConstraints` | Disparate impact, equalized odds |
| `ResourceConstraints` | Compute budgets, step limits |
| `AuditConstraints` | Require approvals, audit trail |

### Remediation Subpackage (`governance/remediation/`)

**Purpose**: Auto-remediation framework.

| Class | Description |
|-------|-------------|
| `RemediationService` | Interface for remediation |
| `RemediationServiceImpl` | Orchestrates actions |
| `RemediationAction` | Sealed interface (Quarantine, Notify, Block, Rollback, Escalate) |
| `AutoRemediationPolicy` | Maps violations to actions |
| `RemediationResult` | Outcome tracking with audit |

---

## Integration Package (`integration/`)

The `integration/` package contains clients for external BUTTERFLY services.

| Class | Description |
|-------|-------------|
| `PerceptionClient` | Interface for PERCEPTION integration (RIM, evidence, data profiles) |
| `PerceptionClientImpl` | WebClient-based client with attachments, evidence, profiling APIs |
| `CapsuleClient` | Interface for CAPSULE integration (history, scopes, lineage, proofs) |
| `CapsuleClientImpl` | WebClient-based client for typed history, aggregation, atomic storage |
| `OdysseyClient` | Interface for ODYSSEY integration (world state, projections, experiments) |
| `OdysseyClientImpl` | WebClient-based client with cached fallbacks for strategic context |
| `SynapseClient` | Interface for SYNAPSE integration (tool registry and inference) |
| `SynapseClientImpl` | WebClient-based client for tool registration and invocation reporting |

**Resilience and observability patterns:**
- Resilience4j circuit breakers, retries, time limiters, and bulkheads (via `ResilienceConfig` / `ResilienceOperators`)
- WebClient with trace propagation (`TracePropagatingWebClient`) and standard headers
- Micrometer metrics (`plato.client.*`) for duration, success/error counters

---

## Security Package (`security/`)

The `security/` package implements authentication and authorization.

| Class | Description |
|-------|-------------|
| `SecurityConfig` | Spring Security configuration |
| `PlatoJwtAuthenticationConverter` | OAuth2 JWT to PlatoUserDetails |
| `JwtTokenProvider` | Legacy JWT token handling |
| `JwtAuthenticationFilter` | WebFlux auth filter |
| `ApiKeyService` | API key validation with caching |
| `SecretProvider` | Interface for secret management |
| `InMemorySecretProvider` | Default in-memory provider |
| `OAuth2SecurityProperties` | Configuration properties |
| `PlatoUserDetails` | User principal with roles |
| `SecurityContextService` | Namespace-scoped permissions |

**Authentication methods:**
1. OAuth2 JWT (preferred for production)
2. Legacy JWT (internal services)
3. API Key (programmatic access)

**Authorization model:**
- Role-based (ADMIN, ENGINEER, VIEWER)
- Namespace-scoped permissions
- `@PreAuthorize` annotations on controllers

---

## Execution Package (`execution/`)

The `execution/` package implements the plan execution framework.

| Class | Description |
|-------|-------------|
| `PlanExecutionService` | Interface for plan execution |
| `PlanExecutionServiceImpl` | Orchestrates step execution |
| `StepExecutor` | Interface for step executors |
| `SynthesizeStepExecutor` | Synthesis step handler |
| `ValidateStepExecutor` | Validation step handler |
| `MineStepExecutor` | Mining step handler |
| `DeployStepExecutor` | Deployment step handler |
| `ApproveStepExecutor` | Approval step handler |
| `RollbackStepExecutor` | Rollback step handler |
| `TestStepExecutor` | Test step handler |
| `ExecutionProgressTracker` | Progress and ETA calculation |

---

## Event Package (`event/`)

The `event/` package handles event publishing and broadcasting.

| Class | Description |
|-------|-------------|
| `PlanExecutionEventBroadcaster` | WebSocket event broadcasting |
| `KafkaEventPublisher` | Kafka event publishing |
| `EventMapper` | Domain to Avro conversion |

---

## API Package (`api/`)

### v1 Controllers

| Controller | Endpoints |
|------------|-----------|
| `SpecController` | `/api/v1/specs/*` |
| `ArtifactController` | `/api/v1/artifacts/*` |
| `ProofController` | `/api/v1/proofs/*` |
| `PlanController` | `/api/v1/plans/*` |
| `GovernanceController` | `/api/v1/governance/*` |
| `LineageController` | `/api/v1/lineage/*` |
| `SynthPlaneController` | `/api/v1/synthplane/*` |
| `FeatureForgeController` | `/api/v1/featureforge/*` |
| `LawMinerController` | `/api/v1/lawminer/*` |
| `SpecMinerController` | `/api/v1/specminer/*` |
| `ResearchOSController` | `/api/v1/researchos/*` |
| `EvidencePlannerController` | `/api/v1/evidenceplanner/*` |

### WebSocket Handlers

| Handler | Path | Purpose |
|---------|------|---------|
| `PlanExecutionWebSocketHandler` | `/ws/plans/{planId}` | Plan execution progress |
| `WebSocketSecurityInterceptor` | - | Auth for WebSocket connections |

---

## Next Steps

- Review [Common Workflows](common-workflows.md) for development patterns
- Explore the [PLATO Architecture Docs](../../PLATO/docs/architecture/) for design details
- Check [Troubleshooting](troubleshooting.md) if you encounter issues
