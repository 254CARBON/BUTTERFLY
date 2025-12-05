# Testing Quickstart: Fastest Path to a Passing Test

> Get from zero to a working test in under 5 minutes

**Last Updated**: 2025-12-05  
**Target Audience**: Developers new to BUTTERFLY testing

---

## Overview

This guide provides copy-paste templates for the most common test scenarios in BUTTERFLY. Each example is self-contained and ready to run.

---

## 1. Unit Test with Mockito (30 seconds)

The quickest test to write. No containers, no Spring context.

### Template

```java
package com.z254.butterfly.perception.mymodule.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyService Unit Tests")
class MyServiceTest {

    @Mock
    private MyRepository repository;

    @Mock
    private ExternalClient externalClient;

    @InjectMocks
    private MyService service;

    @BeforeEach
    void setUp() {
        // Optional: common setup
    }

    @Test
    @DisplayName("should process item successfully")
    void shouldProcessItemSuccessfully() {
        // Arrange
        MyEntity entity = new MyEntity("test-id", "Test Name");
        when(repository.findById("test-id")).thenReturn(Optional.of(entity));

        // Act
        MyResult result = service.process("test-id");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Name");
        verify(repository).findById("test-id");
    }

    @Test
    @DisplayName("should throw exception when not found")
    void shouldThrowExceptionWhenNotFound() {
        // Arrange
        when(repository.findById("missing")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.process("missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("missing");
    }
}
```

### Key Points

- Use `@ExtendWith(MockitoExtension.class)` instead of `@SpringBootTest`
- `@Mock` creates mock dependencies
- `@InjectMocks` creates the service with mocks injected
- Tests run in milliseconds (no Spring context)

---

## 2. Controller Integration Test (2 minutes)

Test your REST controller with MockMvc and real containers.

### Template

```java
package com.z254.butterfly.perception.api.controller;

import com.z254.butterfly.testing.base.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("MyController Integration Tests")
class MyControllerIT extends AbstractControllerIntegrationTest {

    @MockBean
    private MyService myService;

    @Test
    @DisplayName("GET /api/v1/resources should return success envelope")
    void shouldReturnResourceList() throws Exception {
        // Arrange
        when(myService.findAll()).thenReturn(List.of(
            new MyResource("1", "Resource One"),
            new MyResource("2", "Resource Two")
        ));

        // Act & Assert
        performGetWithAuth("/api/v1/resources")
            .andExpect(status().isOk())
            .andExpect(apiSuccess())
            .andExpect(dataArraySize(2));
    }

    @Test
    @DisplayName("POST /api/v1/resources should create resource")
    void shouldCreateResource() throws Exception {
        // Arrange
        CreateResourceRequest request = new CreateResourceRequest("New Resource");
        MyResource created = new MyResource("3", "New Resource");
        when(myService.create(any())).thenReturn(created);

        // Act & Assert
        performPostWithAuth("/api/v1/resources", request)
            .andExpect(status().isCreated())
            .andExpect(apiSuccess())
            .andExpect(jsonPath("$.data.id").value("3"));
    }

    @Test
    @DisplayName("GET /api/v1/resources/{id} should return 404 when not found")
    void shouldReturn404WhenNotFound() throws Exception {
        // Arrange
        when(myService.findById("missing")).thenReturn(Optional.empty());

        // Act & Assert
        performGetWithAuth("/api/v1/resources/missing")
            .andExpect(status().isNotFound())
            .andExpect(apiError("NOT_FOUND"));
    }
}
```

### What You Get from AbstractControllerIntegrationTest

| Method | Description |
|--------|-------------|
| `performGetWithAuth(url)` | GET with default test user |
| `performPostWithAuth(url, body)` | POST with default test user |
| `performPutWithAuth(url, body)` | PUT with default test user |
| `performDeleteWithAuth(url)` | DELETE with default test user |
| `apiSuccess()` | Assert `status: SUCCESS` in envelope |
| `apiError(code)` | Assert `status: ERROR` with specific code |
| `dataArraySize(n)` | Assert `data` array has n elements |
| `extractData(result, Class)` | Parse `data` field to object |

---

## 3. Repository Integration Test (2 minutes)

Test database operations with real PostgreSQL via Testcontainers.

### Template

```java
package com.z254.butterfly.perception.repository;

import com.z254.butterfly.testing.base.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("MyRepository Integration Tests")
class MyRepositoryIT extends AbstractApiIntegrationTest {

    @Autowired
    private MyRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should save and retrieve entity")
    void shouldSaveAndRetrieveEntity() {
        // Arrange
        MyEntity entity = new MyEntity();
        entity.setName("Test Entity");

        // Act
        MyEntity saved = repository.save(entity);
        Optional<MyEntity> found = repository.findById(saved.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Entity");
    }

    @Test
    @DisplayName("should find by custom query")
    void shouldFindByCustomQuery() {
        // Arrange
        repository.save(new MyEntity("active", "Active Entity", Status.ACTIVE));
        repository.save(new MyEntity("inactive", "Inactive Entity", Status.INACTIVE));

        // Act
        List<MyEntity> activeEntities = repository.findByStatus(Status.ACTIVE);

        // Assert
        assertThat(activeEntities).hasSize(1);
        assertThat(activeEntities.get(0).getName()).isEqualTo("Active Entity");
    }
}
```

### What AbstractApiIntegrationTest Provides

- Pre-configured PostgreSQL container (port auto-assigned)
- Pre-configured Kafka container
- Pre-configured Redis container
- Spring datasource properties auto-configured
- `@ActiveProfiles("test")` enabled

---

## 4. Kafka Integration Test (3 minutes)

Test Kafka producers and consumers with real Kafka.

### Template

```java
package com.z254.butterfly.perception.messaging;

import com.z254.butterfly.testing.base.AbstractKafkaIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("MyKafkaProducer Integration Tests")
class MyKafkaProducerIT extends AbstractKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, MyEvent> kafkaTemplate;

    @Autowired
    private MyEventProducer producer;

    @Test
    @DisplayName("should produce event to Kafka topic")
    void shouldProduceEventToTopic() throws Exception {
        // Arrange
        MyEvent event = new MyEvent("test-123", "Test Event", Instant.now());

        // Act
        producer.send(event);

        // Assert - consume and verify
        ConsumerRecord<String, MyEvent> record = 
            consumeOneRecord("my-events-topic", Duration.ofSeconds(10));
        
        assertThat(record).isNotNull();
        assertThat(record.value().getId()).isEqualTo("test-123");
    }
}
```

---

## 5. Service Integration Test (2 minutes)

Test a service with real dependencies.

### Template

```java
package com.z254.butterfly.perception.service;

import com.z254.butterfly.testing.base.AbstractApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional  // Rollback after each test
@DisplayName("MyService Integration Tests")
class MyServiceIT extends AbstractApiIntegrationTest {

    @Autowired
    private MyService service;

    @Autowired
    private MyRepository repository;

    @Test
    @DisplayName("should orchestrate full workflow")
    void shouldOrchestrateFullWorkflow() {
        // Arrange
        CreateRequest request = new CreateRequest("Integration Test Item");

        // Act
        MyResult result = service.createAndProcess(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Status.PROCESSED);

        // Verify persistence
        Optional<MyEntity> persisted = repository.findById(result.getId());
        assertThat(persisted).isPresent();
    }
}
```

---

## Container Configuration Reference

### Default Containers (started automatically)

| Container | Image | Purpose |
|-----------|-------|---------|
| PostgreSQL | `postgres:15-alpine` | Primary database |
| Kafka | `confluentinc/cp-kafka:7.5.2` | Event streaming |
| Redis | `redis:7.2-alpine` | Caching |

### Optional: Add Cassandra

For CAPSULE-related tests:

```java
static {
    ButterflyTestContainers.startWithCassandra();
}
```

### Custom Container Configuration

```java
@DynamicPropertySource
static void configure(DynamicPropertyRegistry registry) {
    // Override defaults
    ButterflyTestContainers.configurePostgres(registry);
    ButterflyTestContainers.configureKafka(registry);
    
    // Add custom properties
    registry.add("my.custom.property", () -> "test-value");
}
```

---

## File Naming Conventions

| Test Type | Suffix | Example |
|-----------|--------|---------|
| Unit tests | `*Test.java` | `MyServiceTest.java` |
| Integration tests | `*IT.java` | `MyServiceIT.java` |
| End-to-end tests | `*E2ETest.java` | `AcquisitionE2ETest.java` |

This aligns with Maven Surefire (unit) and Failsafe (integration) defaults.

---

## Running Tests

```bash
# Unit tests only (fast)
mvn test

# Integration tests only
mvn verify -DskipUTs

# All tests
mvn verify

# Single test class
mvn test -Dtest=MyServiceTest

# Single integration test
mvn verify -Dit.test=MyControllerIT
```

---

## Troubleshooting

### Containers not starting

```bash
# Check Docker is running
docker ps

# Check for port conflicts
docker ps -a | grep testcontainers

# Clean up old containers
docker container prune -f
```

### Tests timing out

```java
// Increase timeout for slow CI
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@Test
void slowTest() { ... }
```

### Database state pollution

```java
// Use @Transactional for automatic rollback
@Transactional
class MyServiceIT { ... }

// Or clean up manually
@BeforeEach
void setUp() {
    repository.deleteAll();
}
```

---

## Related Documentation

- [Testing Strategy](testing-strategy.md) - Full testing philosophy
- [CI/CD Pipeline](ci-cd.md) - How tests run in CI
- [butterfly-testing README](../../butterfly-testing/README.md) - Testing module details
