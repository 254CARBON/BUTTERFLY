# ADR 0007: Governance Pluralism and Multi-School Policy Evaluation

## Status

Accepted

## Date

2025-12-04

## Context

BUTTERFLY's governance system (PLATO) currently evaluates policies from a single perspective. As the ecosystem scales and handles more complex decisions, we need the ability to evaluate policies from multiple "governance schools" that represent different philosophical approaches to governance (e.g., risk-averse vs. innovation-focused).

This is part of **Phase 6: Cognitive Pluralism at Ecosystem Scale**, which aims to:

1. Enable multiple governance perspectives to evaluate the same policy/decision
2. Surface disagreements between schools for human review
3. Compute consensus across schools with configurable thresholds
4. Track divergence patterns over time

### Key Requirements

- **Feature-flagged**: Pluralism must be disabled by default and have zero impact on existing behavior
- **Backward compatible**: Existing `EvaluationResult` consumers must work unchanged
- **Extensible**: Support custom governance schools beyond built-in archetypes
- **Observable**: Track consensus, divergence, and school performance metrics
- **Safe**: High divergence should trigger human-in-the-loop review

## Decision

We will implement governance pluralism as an **opt-in extension** to the existing policy engine with the following design:

### 1. GovernanceSchool Model

A `GovernanceSchool` record encapsulates a governance philosophy:

```java
public record GovernanceSchool(
    UUID schoolId,
    String schoolCode,           // e.g., "CONSERVATIVE", "PROGRESSIVE"
    String name,
    SchoolArchetype archetype,   // CONSERVATIVE, PROGRESSIVE, BALANCED, DOMAIN_SPECIFIC, CUSTOM
    GovernancePhilosophy philosophy,  // risk tolerance, innovation bias, etc.
    PolicyWeights policyWeights,      // weight distribution across policy categories
    double trustScore,           // confidence/weight in consensus
    boolean active,
    // ... metadata, timestamps
)
```

Built-in archetypes with pre-configured philosophies:
- **CONSERVATIVE**: Low risk tolerance (0.2), high compliance strictness (0.9)
- **PROGRESSIVE**: High innovation bias (0.9), flexible compliance (0.5)
- **BALANCED**: Equal weights across all dimensions

### 2. Repository Abstraction

Pluggable persistence via `GovernanceSchoolRepository`:
- `InMemoryGovernanceSchoolRepository`: Default for development/testing
- `CassandraGovernanceSchoolRepository`: Production persistence

### 3. EvaluationResult Extension

New optional fields in `EvaluationResult`:

```java
// Null when pluralism is disabled
List<SchoolEvaluation> schoolEvaluations,
PluralismSummary pluralismSummary
```

Where:
- `SchoolEvaluation`: Per-school pass/fail, score, violations
- `PluralismSummary`: Consensus score, divergence score, outcome, divergence points

### 4. PolicyEngine Integration

New methods with default fallback to standard evaluation:

```java
// Evaluates under all active schools when pluralism enabled
Mono<EvaluationResult> evaluateWithPluralism(EvaluationContext context);
boolean isPluralismEnabled();
```

### 5. Feature Flag Configuration

```yaml
plato:
  governance-pluralism:
    enabled: false  # Master switch, default OFF
    default-schools: [CONSERVATIVE, BALANCED, PROGRESSIVE]
    consensus:
      threshold: 0.7
    divergence:
      critical-threshold: 0.4
    human-in-the-loop:
      enabled: true
      divergence-approval-threshold: 0.4
```

### 6. Consensus Algorithm

Weighted average consensus with configurable thresholds:

```
consensusScore = Σ(schoolScore × trustScore) / Σ(trustScore)
divergenceScore = standardDeviation(schoolScores)
```

Consensus outcomes: `UNANIMOUS_APPROVE`, `MAJORITY_APPROVE`, `SPLIT_DECISION`, `MAJORITY_REJECT`, `UNANIMOUS_REJECT`, `NO_CONSENSUS`

### 7. NEXUS Integration

`PlatoGovernanceHooks` extended with:
- `validateOptionWithPluralism()`: Multi-school validation
- `getSchoolDisagreements()`: Surface disagreements for synthesis

## Consequences

### Positive

1. **Zero regression risk**: Completely feature-flagged, disabled by default
2. **Gradual adoption**: Teams can enable pluralism per-namespace or environment
3. **Rich insights**: Divergence tracking reveals policy ambiguities
4. **Extensibility**: Custom schools for domain-specific governance
5. **Human oversight**: High-divergence decisions require review

### Negative

1. **Complexity**: Additional code paths and configuration
2. **Performance**: Multi-school evaluation is N× slower (mitigated by parallel execution)
3. **Storage**: Per-school results increase `EvaluationResult` size

### Neutral

1. **Learning curve**: Teams need to understand governance schools concept
2. **Tuning required**: Consensus thresholds need calibration per use case

## Implementation

### Files Created

| File | Purpose |
|------|---------|
| `GovernanceSchool.java` | Core model with archetypes and philosophy |
| `GovernanceSchoolRepository.java` | Repository interface |
| `InMemoryGovernanceSchoolRepository.java` | In-memory implementation |
| `CassandraGovernanceSchoolRepository.java` | Cassandra implementation |
| `SchoolIdentityStrategy.java` | Identity and validation utilities |
| `PluralismPolicyEvaluator.java` | Multi-school evaluation logic |
| `GovernancePluralismAutoConfiguration.java` | Spring auto-config |
| `governance_schools_schema.cql` | Cassandra schema |

### Files Modified

| File | Changes |
|------|---------|
| `EvaluationResult.java` | Added schoolEvaluations, pluralismSummary, helper methods |
| `PolicyEngine.java` | Added evaluateWithPluralism(), isPluralismEnabled() |
| `PolicyEngineImpl.java` | Integrated PluralismPolicyEvaluator |
| `GovernancePluralismController.java` | Enhanced endpoints |
| `PlatoGovernanceHooks.java` (NEXUS) | Added pluralism validation |
| `application.yml` | Added governance-pluralism config section |

### API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/governance/pluralism/status` | GET | Check pluralism status |
| `/api/v1/governance/pluralism/schools` | GET/POST | List/create schools |
| `/api/v1/governance/pluralism/evaluate-object` | POST | Multi-school evaluation |
| `/api/v1/governance/pluralism/divergences` | GET | Active divergences |

## Testing

- `GovernanceSchoolTest`: Model and factory methods
- `SchoolIdentityStrategyTest`: Code validation and URN generation
- `InMemoryGovernanceSchoolRepositoryTest`: Repository operations
- `PolicyEngineImplDefaultBehaviorTest`: **Critical regression tests**
- `EvaluationResultPluralismTest`: Pluralism features

## Alternatives Considered

### 1. Separate Pluralism Service

**Rejected**: Would require significant API changes and wouldn't integrate cleanly with existing PolicyEngine consumers.

### 2. Always-On Pluralism

**Rejected**: Would break backward compatibility and impact performance for users who don't need multi-school evaluation.

### 3. Annotation-Based School Selection

**Rejected**: Less flexible than runtime configuration; harder to experiment with different school combinations.

## References

- [Phase 6 Cognitive Pluralism Specification](../architecture/ecosystem-overview.md)
- [PLATO Governance Architecture](../../PLATO/docs/architecture/governance.md)
- [PolicyConsensusService Interface](../../PLATO/src/main/java/com/z254/butterfly/plato/governance/pluralism/PolicyConsensusService.java)

