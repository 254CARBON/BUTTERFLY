# BUTTERFLY Testing Strategy

> Testing approach and best practices for BUTTERFLY

**Last Updated**: 2025-12-03  
**Target Audience**: Developers, QA engineers

---

## Overview

BUTTERFLY employs a comprehensive testing strategy with multiple test layers to ensure quality and reliability. This document defines canonical testing expectations that apply across all services, with service-specific variations noted where applicable.

---

## Test Pyramid

```
                    ┌─────────────┐
                    │    E2E      │  ← Few, slow, expensive
                    │   Tests     │
                    └─────────────┘
                  ┌─────────────────┐
                  │   Integration   │  ← Some, medium speed
                  │     Tests       │
                  └─────────────────┘
              ┌─────────────────────────┐
              │       Unit Tests        │  ← Many, fast, cheap
              │                         │
              └─────────────────────────┘
```

| Layer | Coverage Target | Execution Time |
|-------|----------------|----------------|
| Unit | 80%+ | < 5 minutes |
| Integration | Critical paths | < 15 minutes |
| E2E | Happy paths | < 30 minutes |

---

## Per-Service Test Matrix

### What Constitutes "Green" for PRs

| Service | Unit Tests | Integration Tests | E2E Tests | Chaos Tests | Coverage |
|---------|-----------|-------------------|-----------|-------------|----------|
| **CAPSULE** | `mvn test` ✓ | `mvn verify` ✓ | Optional | Optional | ≥80% |
| **ODYSSEY** | `mvn test` ✓ | `mvn verify -P integration-tests` ✓ | Golden path ✓ | - | ≥80% |
| **PERCEPTION** | `mvn test` ✓ | `mvn verify -P integration-tests` ✓ | Portal E2E ✓ | Nightly | ≥80% |
| **PLATO** | `mvn test` ✓ | `mvn verify` ✓ | - | - | ≥80% |
| **NEXUS** | `mvn test` ✓ | `mvn verify` ✓ | - | - | ≥80% |
| **butterfly-common** | `mvn test` ✓ | - | - | - | ≥90% |

**Legend**: ✓ = Required for PR merge, Optional = Nice-to-have, Nightly = Runs on schedule

### Test Commands by Service

#### CAPSULE

```bash
# Unit tests
mvn -f CAPSULE/pom.xml test

# All tests including integration
mvn -f CAPSULE/pom.xml verify

# SDK tests
mvn -f CAPSULE/capsule-sdk-java/pom.xml test
cd CAPSULE/capsule-sdk-python && pytest -v
cd CAPSULE/capsule-sdk-typescript && npm test

# UI tests
cd CAPSULE/capsule-ui && npm test
cd CAPSULE/capsule-ui && npm run test:e2e

# Chaos tests
cd CAPSULE && python chaos_test.py

# Performance tests
cd CAPSULE && python performance_test.py
```

#### ODYSSEY

```bash
# Unit tests
mvn -f ODYSSEY/pom.xml test

# Integration tests
mvn -f ODYSSEY/pom.xml verify -P integration-tests

# Specific module
mvn -f ODYSSEY/pom.xml test -pl odyssey-core

# Golden path (from apps root)
./butterfly-e2e/run-golden-path.sh
```

#### PERCEPTION

```bash
# Unit tests
mvn -f PERCEPTION/pom.xml test

# Specific module
mvn -f PERCEPTION/pom.xml test -pl perception-signals

# Integration tests
mvn -f PERCEPTION/pom.xml verify -P integration-tests

# Chaos tests (runs in nightly CI)
mvn -f PERCEPTION/pom.xml test -pl perception-api -Dgroups=chaos

# Portal tests
cd PERCEPTION/perception-portal && npm test
cd PERCEPTION/perception-portal && npm run test:e2e
```

#### PLATO

```bash
# Unit tests
mvn -f PLATO/pom.xml test

# All tests
mvn -f PLATO/pom.xml verify

# Specific test class
mvn -f PLATO/pom.xml test -Dtest=SpecServiceTest
```

#### NEXUS

```bash
# Unit tests
mvn -f butterfly-nexus/pom.xml test

# All tests
mvn -f butterfly-nexus/pom.xml verify
```

#### butterfly-common

```bash
# Unit tests (higher coverage required)
mvn -f butterfly-common/pom.xml test

# With coverage report
mvn -f butterfly-common/pom.xml test jacoco:report
```

### E2E Test Harness

The `butterfly-e2e` module provides ecosystem-wide testing:

```bash
# Run golden path integration test
./butterfly-e2e/run-golden-path.sh

# Run full scenario suite
./butterfly-e2e/run-scenarios.sh

# Against external Kafka (skip local Docker)
START_DEV_STACK=0 ./butterfly-e2e/run-scenarios.sh

# Full stack smoke test
docker compose -f butterfly-e2e/docker-compose.full.yml up --profile odyssey
```

**Scenario Catalog**: `butterfly-e2e/scenarios/catalog.json`

---

## Unit Tests

### Characteristics

- Test single units in isolation
- Fast execution (milliseconds)
- No external dependencies
- Run on every commit

### Structure

```java
@ExtendWith(MockitoExtension.class)
class CapsuleServiceTest {
    
    @Mock
    private CapsuleRepository repository;
    
    @Mock
    private EventPublisher publisher;
    
    @InjectMocks
    private CapsuleService service;
    
    @Test
    void create_withValidRequest_persistsCapsule() {
        // Arrange
        CapsuleRequest request = CapsuleRequest.builder()
            .scopeId("rim:entity:finance:EURUSD")
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .build();
        
        when(repository.save(any())).thenAnswer(invocation -> {
            Capsule capsule = invocation.getArgument(0);
            capsule.setId("cap-123");
            return capsule;
        });
        
        // Act
        Capsule result = service.create(request);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getScopeId()).isEqualTo(request.getScopeId());
        verify(repository).save(any(Capsule.class));
        verify(publisher).publish(any(CapsuleCreatedEvent.class));
    }
    
    @Test
    void create_withNullScopeId_throwsException() {
        // Arrange
        CapsuleRequest request = CapsuleRequest.builder()
            .timestamp(Instant.now())
            .build();
        
        // Act & Assert
        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scopeId");
    }
}
```

### Naming Convention

```
methodName_stateUnderTest_expectedBehavior

create_withValidRequest_persistsCapsule
query_withInvalidTimeRange_throwsException
```

### Running Unit Tests

```bash
# All unit tests
./gradlew test

# Specific service
./gradlew :capsule:test

# Specific test class
./gradlew :capsule:test --tests "CapsuleServiceTest"

# With coverage report
./gradlew test jacocoTestReport
```

---

## Integration Tests

### Characteristics

- Test component interactions
- Use real or containerized dependencies
- Medium execution time
- Run on PR and main branch

### Testcontainers Setup

```java
@SpringBootTest
@Testcontainers
class CapsuleRepositoryIntegrationTest {
    
    @Container
    static CassandraContainer<?> cassandra = new CassandraContainer<>("cassandra:4.1")
        .withExposedPorts(9042);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.cassandra.contact-points", 
            () -> cassandra.getHost());
        registry.add("spring.data.cassandra.port", 
            () -> cassandra.getMappedPort(9042));
    }
    
    @Autowired
    private CapsuleRepository repository;
    
    @Test
    void save_andFindById_returnsPersistedCapsule() {
        // Arrange
        Capsule capsule = createCapsule();
        
        // Act
        repository.save(capsule);
        Optional<Capsule> found = repository.findById(capsule.getId());
        
        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getScopeId()).isEqualTo(capsule.getScopeId());
    }
}
```

### API Integration Tests

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CapsuleApiIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void createCapsule_withValidRequest_returns201() throws Exception {
        CapsuleRequest request = CapsuleRequest.builder()
            .scopeId("rim:entity:finance:EURUSD")
            .timestamp(Instant.now())
            .build();
        
        mockMvc.perform(post("/api/v1/capsules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.scopeId").value(request.getScopeId()));
    }
    
    @Test
    void createCapsule_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/capsules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
```

### Running Integration Tests

```bash
# All integration tests
./gradlew integrationTest

# With specific profile
./gradlew integrationTest -Dspring.profiles.active=test
```

---

## Contract Tests

### Consumer-Driven Contracts (Pact)

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "capsule-service")
class CapsuleClientContractTest {
    
    @Pact(consumer = "nexus-gateway")
    public RequestResponsePact createCapsulePact(PactDslWithProvider builder) {
        return builder
            .given("no existing capsule")
            .uponReceiving("a request to create a capsule")
            .path("/api/v1/capsules")
            .method("POST")
            .body(new PactDslJsonBody()
                .stringValue("scopeId", "rim:entity:finance:EURUSD")
                .datetime("timestamp", "yyyy-MM-dd'T'HH:mm:ss'Z'"))
            .willRespondWith()
            .status(201)
            .body(new PactDslJsonBody()
                .stringType("id")
                .stringValue("scopeId", "rim:entity:finance:EURUSD"))
            .toPact();
    }
    
    @Test
    @PactTestFor(pactMethod = "createCapsulePact")
    void testCreateCapsule(MockServer mockServer) {
        // Test using the mock server
    }
}
```

---

## End-to-End Tests

### Location

E2E tests are in the `butterfly-e2e` module.

### Structure

```java
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CapsuleWorkflowE2ETest {
    
    @Autowired
    private ButterflyClient client;
    
    private static String createdCapsuleId;
    
    @Test
    @Order(1)
    void createCapsule_succeeds() {
        CapsuleResponse response = client.capsule().create(
            CapsuleRequest.builder()
                .scopeId("rim:entity:finance:EURUSD")
                .timestamp(Instant.now())
                .build());
        
        assertThat(response.getId()).isNotNull();
        createdCapsuleId = response.getId();
    }
    
    @Test
    @Order(2)
    void queryCapsule_returnsPreviouslyCreated() {
        CapsuleResponse response = client.capsule().get(createdCapsuleId);
        
        assertThat(response.getScopeId())
            .isEqualTo("rim:entity:finance:EURUSD");
    }
    
    @Test
    @Order(3)
    void timeTravel_returnsHistoricalState() {
        TimelineView view = client.capsule().travelTo(
            TimePoint.of(
                "rim:entity:finance:EURUSD",
                Instant.now().minus(Duration.ofHours(1))));
        
        assertThat(view.getState()).isNotNull();
    }
}
```

### Running E2E Tests

```bash
# Start services first
docker compose up -d

# Run E2E tests
./gradlew :butterfly-e2e:test

# Against specific environment
E2E_BASE_URL=http://staging.butterfly.example.com ./gradlew :butterfly-e2e:test
```

---

## Performance Tests

### JMeter / Gatling

```scala
class CapsuleSimulation extends Simulation {
  
  val httpProtocol = http
    .baseUrl("http://localhost:8083")
    .acceptHeader("application/json")
    .header("Authorization", "Bearer ${token}")
  
  val createCapsule = scenario("Create Capsule")
    .exec(http("Create")
      .post("/api/v1/capsules")
      .body(StringBody("""{"scopeId":"rim:entity:finance:EURUSD","timestamp":"2024-01-01T00:00:00Z"}"""))
      .check(status.is(201)))
  
  setUp(
    createCapsule.inject(
      rampUsers(100).during(60),
      constantUsersPerSec(50).during(300)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile3.lt(500),
     global.successfulRequests.percent.gt(99)
   )
}
```

---

## Test Data Management

### Factories

```java
public class CapsuleFactory {
    
    public static Capsule createDefault() {
        return Capsule.builder()
            .id(UUID.randomUUID().toString())
            .scopeId("rim:entity:finance:EURUSD")
            .timestamp(Instant.now())
            .resolution(60)
            .vantageMode(VantageMode.CURRENT)
            .build();
    }
    
    public static CapsuleRequest createRequest() {
        return CapsuleRequest.builder()
            .scopeId("rim:entity:finance:EURUSD")
            .timestamp(Instant.now())
            .build();
    }
}
```

### Fixtures

```java
@Component
public class TestFixtures {
    
    @Autowired
    private CapsuleRepository capsuleRepository;
    
    @Transactional
    public Capsule createAndPersistCapsule() {
        return capsuleRepository.save(CapsuleFactory.createDefault());
    }
    
    @Transactional
    public void cleanup() {
        capsuleRepository.deleteAll();
    }
}
```

---

## Code Coverage

### Configuration

```groovy
jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80
            }
        }
    }
}
```

### Coverage Targets

| Module | Line Coverage | Branch Coverage |
|--------|---------------|-----------------|
| capsule | 80% | 70% |
| odyssey | 80% | 70% |
| perception | 80% | 70% |
| plato | 80% | 70% |
| nexus | 80% | 70% |
| common | 90% | 80% |

---

## Test Environment

### Local

```bash
# Start test infrastructure
docker compose -f docker-compose.test.yml up -d

# Run all tests
./gradlew check
```

### CI

Tests run automatically on:
- Every push
- Every PR
- Nightly full test suite

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Contributing](contributing.md) | Contribution guide |
| [Coding Standards](coding-standards.md) | Code style |
| [CI/CD](ci-cd.md) | Build pipelines |

