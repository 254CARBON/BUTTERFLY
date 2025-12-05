# PLATO-PERCEPTION Intelligence Integration

This guide documents the relationship between PLATO governance decisions and PERCEPTION intelligence outputs, including how governance stubs are used for testing.

## Overview

PLATO and PERCEPTION form a bidirectional intelligence loop:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│    ┌──────────────┐         Scenarios          ┌───────────────┐       │
│    │  PERCEPTION  │ ─────────────────────────▶ │     PLATO     │       │
│    │              │                            │               │       │
│    │  - Scenarios │         Governance         │  - Schools    │       │
│    │  - Signals   │ ◀───────────────────────── │  - Policies   │       │
│    │  - RIM       │          Decisions         │  - Plans      │       │
│    └──────────────┘                            └───────────────┘       │
│           │                                           │                 │
│           │                                           │                 │
│           ▼                                           ▼                 │
│    ┌──────────────┐                            ┌───────────────┐       │
│    │   Feedback   │                            │    Action     │       │
│    │   Learning   │ ◀────────────────────────  │   Outcomes    │       │
│    └──────────────┘                            └───────────────┘       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Integration Points

### 1. PERCEPTION → PLATO: Scenario Feed

PERCEPTION generates scenarios that PLATO evaluates for governance decisions:

```java
// PERCEPTION generates scenario
StackableScenario scenario = StackableScenario.builder()
    .id("scenario-123")
    .title("Supply Chain Disruption Response")
    .confidence(0.85)
    .impactScore(0.72)
    .metadata(Map.of(
        "domain", "supply_chain",
        "urgency", "HIGH"
    ))
    .build();

// Published to Kafka: perception.scenarios.generated
```

### 2. PLATO → PERCEPTION: Governance Signals

PLATO governance decisions flow back to PERCEPTION's RIM:

```java
// From GovernanceOutcomeConsumer.java
GovernanceOutcomeEvent event = GovernanceOutcomeEvent.builder()
    .eventId(eventId)
    .nodeId(nodeId)
    .planId(planId)       // PLATO plan ID
    .stepId(stepId)       // PLATO step ID
    .approvalId(approvalId) // PLATO approval ID
    .policyId(policyId)   // Governing policy
    .status(status)       // APPROVED, REJECTED, PENDING
    .riskScore(riskScore)
    .build();
```

### 3. RIM Governance Timeline

PERCEPTION maintains a timeline of governance events linked to RIM nodes:

```java
// From RimGovernanceEventEntity.java
@Entity
@Table(name = "rim_governance_events")
public class RimGovernanceEventEntity {
    
    @Column(name = "plato_plan_id")
    private String platoPlanId;
    
    @Column(name = "plato_step_id")
    private String platoStepId;
    
    @Column(name = "plato_approval_id")
    private String platoApprovalId;
    
    @Column(name = "plato_policy_id")
    private String platoPolicyId;
    
    @Column(name = "governance_status")
    @Enumerated(EnumType.STRING)
    private GovernanceStatus status;
    
    @Column(name = "risk_score")
    private Double riskScore;
}
```

---

## Governance Schools

PLATO uses "schools" to represent different governance philosophies that influence scenario scoring:

| School | Description | Impact on PERCEPTION |
|--------|-------------|---------------------|
| **Consequentialist** | Judges by outcomes | Scenarios scored by expected impact |
| **Deontological** | Rules-based ethics | Hard constraints on scenario types |
| **Virtue-based** | Character/intention focus | Source trust weighting |
| **Pluralist** | Balances multiple views | Weighted scenario ensemble |

### School Influence on Scenario Scoring

```java
// Example: PLATO adjusts scenario confidence based on school
public class PlatoGovernanceAdjuster {
    
    public ScenarioScoreAdjustment adjust(
            StackableScenario scenario,
            GovernanceSchool school,
            PolicyContext policyContext) {
        
        double adjustment = 0.0;
        
        switch (school) {
            case CONSEQUENTIALIST -> {
                // Boost high-impact scenarios
                if (scenario.getImpactScore() > 0.7) {
                    adjustment += 0.1;
                }
            }
            case DEONTOLOGICAL -> {
                // Penalize scenarios violating rules
                if (violatesPolicy(scenario, policyContext)) {
                    adjustment -= 0.5;
                }
            }
            case VIRTUE_BASED -> {
                // Weight by source trustworthiness
                adjustment = calculateTrustAdjustment(scenario);
            }
            case PLURALIST -> {
                // Weighted ensemble of all approaches
                adjustment = calculateEnsembleAdjustment(scenario, policyContext);
            }
        }
        
        return ScenarioScoreAdjustment.builder()
            .schoolApplied(school)
            .adjustmentValue(adjustment)
            .reasoning(buildReasoning(school, scenario))
            .build();
    }
}
```

---

## Governance Stubs for Testing

### Purpose

Governance stubs allow PERCEPTION to be tested independently of PLATO:

1. **Unit Testing** - Test scenario generation without PLATO
2. **Integration Testing** - Verify governance signal handling
3. **E2E Testing** - Simulate PLATO decisions
4. **Performance Testing** - Load test without PLATO dependencies

### Stub Implementation

```java
// perception-testing/src/main/java/.../stubs/GovernanceStubs.java
public class GovernanceStubs {

    /**
     * Create a stub governance decision for testing.
     */
    public static GovernanceDecision approved(String scenarioId) {
        return GovernanceDecision.builder()
            .scenarioId(scenarioId)
            .status(GovernanceStatus.APPROVED)
            .school(GovernanceSchool.PLURALIST)
            .confidence(0.85)
            .rationale("Stub: Auto-approved for testing")
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Create a rejection stub.
     */
    public static GovernanceDecision rejected(String scenarioId, String reason) {
        return GovernanceDecision.builder()
            .scenarioId(scenarioId)
            .status(GovernanceStatus.REJECTED)
            .school(GovernanceSchool.DEONTOLOGICAL)
            .confidence(0.95)
            .rationale("Stub: Rejected - " + reason)
            .policyViolations(List.of(reason))
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Create a pending governance decision.
     */
    public static GovernanceDecision pending(String scenarioId) {
        return GovernanceDecision.builder()
            .scenarioId(scenarioId)
            .status(GovernanceStatus.PENDING)
            .school(GovernanceSchool.PLURALIST)
            .rationale("Stub: Awaiting governance review")
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Create a governance timeline for RIM testing.
     */
    public static List<GovernanceTimelineEntry> timeline(
            String nodeId, int entryCount) {
        return IntStream.range(0, entryCount)
            .mapToObj(i -> GovernanceTimelineEntry.builder()
                .eventId("evt-" + i)
                .nodeId(nodeId)
                .platoPlanId("plan-stub-" + i)
                .platoStepId("step-" + (i % 3))
                .status(i % 4 == 0 ? GovernanceStatus.REJECTED : GovernanceStatus.APPROVED)
                .riskScore(0.2 + (i * 0.1) % 0.8)
                .timestamp(Instant.now().minus(Duration.ofHours(entryCount - i)))
                .build())
            .collect(Collectors.toList());
    }
}
```

### Using Stubs in Tests

```java
@SpringBootTest
@ActiveProfiles("test")
class ScenarioGovernanceIT extends AbstractApiIntegrationTest {

    @MockBean
    private PlatoGovernanceClient platoClient;

    @Test
    @DisplayName("should handle approved governance decision")
    void shouldHandleApprovedDecision() {
        // Arrange
        String scenarioId = "scenario-test-123";
        when(platoClient.getDecision(scenarioId))
            .thenReturn(Mono.just(GovernanceStubs.approved(scenarioId)));

        // Act & Assert
        mockMvc.perform(get("/api/v1/scenarios/{id}/governance", scenarioId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("should handle rejected scenario")
    void shouldHandleRejectedScenario() {
        // Arrange
        String scenarioId = "scenario-test-456";
        when(platoClient.getDecision(scenarioId))
            .thenReturn(Mono.just(GovernanceStubs.rejected(
                scenarioId, "POLICY_VIOLATION_001")));

        // Act & Assert
        mockMvc.perform(get("/api/v1/scenarios/{id}/governance", scenarioId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.policyViolations[0]").value("POLICY_VIOLATION_001"));
    }
}
```

### Stub Configuration

```yaml
# application-test.yml
perception:
  governance:
    mode: STUB
    stub:
      default-status: APPROVED
      default-school: PLURALIST
      latency-ms: 50  # Simulate network latency
```

---

## Kafka Topics

### Topics for PERCEPTION-PLATO Communication

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `perception.scenarios.generated` | P → PLATO | New scenarios for governance |
| `plato.governance.decisions` | PLATO → P | Governance decisions |
| `plato.governance.outcomes` | PLATO → P | Action execution outcomes |
| `perception.feedback.governance` | P → PLATO | Feedback on governance effectiveness |

### Event Schemas

#### Scenario Generated Event

```json
{
  "eventType": "SCENARIO_GENERATED",
  "scenarioId": "scenario-123",
  "eventId": "event-456",
  "title": "Supply Chain Disruption Response",
  "confidence": 0.85,
  "impactScore": 0.72,
  "domain": "supply_chain",
  "urgency": "HIGH",
  "generatedAt": "2025-01-17T12:00:00Z",
  "schoolsApplied": ["PATTERN_MATCHING", "CAUSAL_ANALYSIS"]
}
```

#### Governance Decision Event

```json
{
  "eventType": "GOVERNANCE_DECISION",
  "scenarioId": "scenario-123",
  "status": "APPROVED",
  "school": "PLURALIST",
  "confidence": 0.92,
  "rationale": "Scenario aligns with risk tolerance thresholds",
  "policyId": "policy-risk-001",
  "approvalId": "approval-789",
  "decidedAt": "2025-01-17T12:01:30Z",
  "adjustments": {
    "confidenceBoost": 0.05,
    "reason": "High trust sources"
  }
}
```

---

## Intelligence Feedback Loop

### How PERCEPTION Learns from PLATO Decisions

```java
@KafkaListener(topics = "plato.governance.outcomes")
public void handleGovernanceOutcome(GovernanceOutcomeEvent event) {
    
    // 1. Update scenario effectiveness based on outcome
    scenarioFeedbackService.recordOutcome(
        event.getScenarioId(),
        event.getActionOutcome(),
        event.getEffectivenessScore()
    );
    
    // 2. Update source trust based on prediction accuracy
    if (event.getPredictionAccuracy() != null) {
        trustService.adjustSourceTrust(
            event.getSourceIds(),
            event.getPredictionAccuracy()
        );
    }
    
    // 3. Feed into reasoning rule calibration
    reasoningService.recordGovernanceOutcome(
        event.getRulesApplied(),
        event.getOutcomeQuality()
    );
    
    // 4. Update RIM with governance timeline
    rimGovernanceService.recordEvent(event);
}
```

### Metrics Tracked

| Metric | Description | Used By |
|--------|-------------|---------|
| `governance_decision_accuracy` | % of decisions with positive outcomes | Both |
| `scenario_governance_latency` | Time from generation to decision | PERCEPTION |
| `policy_violation_rate` | % of scenarios violating policies | PLATO |
| `school_effectiveness` | Effectiveness by governance school | Both |

---

## Configuration

### PERCEPTION Configuration

```yaml
# application.yml (PERCEPTION)
perception:
  plato:
    enabled: true
    base-url: https://plato.internal:8080
    connect-timeout-ms: 5000
    read-timeout-ms: 15000
    
  governance:
    mode: LIVE  # or STUB for testing
    sync-enabled: true
    feedback-enabled: true
    
  kafka:
    topics:
      governance-decisions: plato.governance.decisions
      governance-outcomes: plato.governance.outcomes
```

### PLATO Configuration

```yaml
# application.yml (PLATO)
plato:
  perception:
    enabled: true
    base-url: https://perception.internal:8080
    
  schools:
    default: PLURALIST
    weights:
      consequentialist: 0.3
      deontological: 0.25
      virtue_based: 0.2
      pluralist: 0.25
```

---

## Testing Strategies

### 1. Unit Tests with Stubs

```java
@ExtendWith(MockitoExtension.class)
class GovernanceIntegrationTest {

    @Mock
    private PlatoGovernanceClient platoClient;

    @Test
    void shouldProcessGovernanceDecision() {
        when(platoClient.getDecision(any()))
            .thenReturn(Mono.just(GovernanceStubs.approved("test")));
        // ... test logic
    }
}
```

### 2. Integration Tests with WireMock

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class PlatoIntegrationIT {

    @Test
    void shouldCallPlatoForGovernance() {
        stubFor(post(urlEqualTo("/api/v1/governance/decisions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "scenarioId": "test-123",
                        "status": "APPROVED",
                        "school": "PLURALIST"
                    }
                    """)));
        
        // ... test interaction
    }
}
```

### 3. E2E Tests with Embedded Kafka

```java
@SpringBootTest
@EmbeddedKafka(topics = {"plato.governance.decisions"})
class GovernanceE2ETest {

    @Autowired
    private KafkaTemplate<String, GovernanceDecisionEvent> kafkaTemplate;

    @Test
    void shouldConsumeGovernanceDecisions() {
        kafkaTemplate.send("plato.governance.decisions", 
            GovernanceStubs.decisionEvent("scenario-123", GovernanceStatus.APPROVED));
        
        // ... verify handling
    }
}
```

---

## Related Documentation

- [PERCEPTION Scenario Generation](../../PERCEPTION/docs/guides/ADDING_A_SCENARIO_SCHOOL.md)
- [PLATO Governance Architecture](../../PLATO/docs/ARCHITECTURE.md)
- [BUTTERFLY Ecosystem Overview](../architecture/ecosystem-overview.md)
- [Kafka Topic Registry](kafka-contracts.md)
