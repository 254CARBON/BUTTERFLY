# BUTTERFLY Testing Library

Shared testing infrastructure for the BUTTERFLY ecosystem, providing consistent Testcontainers configurations, base test classes, and test utilities across all services.

## Overview

This library provides:

- **Singleton Testcontainers** - Pre-configured containers for PostgreSQL, Kafka, Redis, and Cassandra
- **Base Test Classes** - Reusable test class hierarchies for API and Kafka integration tests  
- **Test Fixtures** - Factories and utilities for generating consistent test data
- **Container Registry** - Centralized container tracking and status reporting

## Installation

Add the dependency to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>butterfly-testing</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

## Quick Start

### API Integration Tests

Extend `AbstractApiIntegrationTest` for controller tests:

```java
@SpringBootTest
class MyControllerIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void shouldCreateResource() throws Exception {
        MyRequest request = new MyRequest("test");
        
        performPost("/api/v1/resources", request)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }
    
    @Test
    void shouldGetResource() throws Exception {
        performGet("/api/v1/resources/{id}", "resource-123")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("test"));
    }
}
```

### Kafka Integration Tests

Extend `AbstractKafkaIntegrationTest` for messaging tests:

```java
@SpringBootTest
class MyKafkaIntegrationTest extends AbstractKafkaIntegrationTest {

    @Override
    protected List<String> getRequiredTopics() {
        return List.of("my.input.topic", "my.output.topic");
    }
    
    @Test
    void shouldProcessMessage() throws Exception {
        // Send message
        sendMessage("my.input.topic", "key", "value");
        
        // Wait for processed output
        ConsumerRecord<String, String> output = 
            waitForMessage("my.output.topic", Duration.ofSeconds(10));
        
        assertThat(output.value()).contains("processed");
    }
}
```

### Direct Container Usage

For custom test configurations:

```java
@SpringBootTest
class CustomIntegrationTest {

    static {
        ButterflyTestContainers.startCoreContainers();
    }
    
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ButterflyTestContainers.configurePostgres(registry);
        ButterflyTestContainers.configureKafka(registry);
        // Add custom properties
        registry.add("my.custom.property", () -> "value");
    }
}
```

## Available Containers

| Container | Image | Default Port | Services Using |
|-----------|-------|--------------|----------------|
| PostgreSQL | `postgres:15-alpine` | 5432 | ODYSSEY, PERCEPTION, NEXUS |
| Kafka | `confluentinc/cp-kafka:7.5.2` | 9092 | All services |
| Redis | `redis:7.2-alpine` | 6379 | ODYSSEY, NEXUS (cache) |
| Cassandra | `cassandra:4.1` | 9042 | CAPSULE, PLATO |

## Container Management

### Starting Containers

```java
// Start core containers (PostgreSQL, Kafka, Redis)
ButterflyTestContainers.startCoreContainers();

// Start all containers including Cassandra
ButterflyTestContainers.startWithCassandra();

// Individual container access (lazy initialization)
PostgreSQLContainer<?> postgres = ButterflyTestContainers.getPostgresContainer();
KafkaContainer kafka = ButterflyTestContainers.getKafkaContainer();
```

### Configuring Properties

```java
@DynamicPropertySource
static void configure(DynamicPropertyRegistry registry) {
    // Configure all core containers at once
    ButterflyTestContainers.configureCoreProperties(registry);
    
    // Or configure individually
    ButterflyTestContainers.configurePostgres(registry);
    ButterflyTestContainers.configureKafka(registry);
    ButterflyTestContainers.configureRedis(registry);
    ButterflyTestContainers.configureCassandra(registry);
}
```

## Test Data Factories

Generate consistent test data:

```java
// Unique identifiers
String id = TestDataFactories.uniqueId("capsule");  // "capsule-a1b2c3d4"
String uuid = TestDataFactories.uuid();

// RIM node identifiers
String rimId = TestDataFactories.rimNodeId("entity", "finance", "EURUSD");
// -> "rim:entity:finance:EURUSD"

// Timestamps
Instant now = TestDataFactories.now();
Instant pastHour = TestDataFactories.hoursAgo(1);
List<Instant> series = TestDataFactories.timestampSeries(
    Instant.now(), 10, 1, ChronoUnit.HOURS);

// Random data
double stress = TestDataFactories.randomStress();      // 0.0 - 1.0
double highStress = TestDataFactories.randomHighStress();  // 0.7 - 1.0
String ticker = TestDataFactories.randomTicker();      // "AAPL", "MSFT", etc.
String currency = TestDataFactories.randomCurrencyPair();  // "EURUSD"

// Collection generation
List<String> ids = TestDataFactories.listOf(10, i -> TestDataFactories.uniqueId("item"));
```

## Container Registry

Track and inspect running containers:

```java
// Check container status
boolean isKafkaRunning = ContainerRegistry.isRunning("kafka");

// Get container instance
Optional<GenericContainer<?>> postgres = ContainerRegistry.get("postgres");

// Get status summary
String status = ContainerRegistry.getStatusSummary();
log.info(status);
// Container Status:
//   - postgres: RUNNING (image: postgres:15-alpine)
//   - kafka: RUNNING (image: confluentinc/cp-kafka:7.5.2)
//   - redis: RUNNING (image: redis:7.2-alpine)
```

## Best Practices

### 1. Use Static Initializers

Start containers in static initializers to ensure they're ready before Spring context:

```java
static {
    ButterflyTestContainers.startCoreContainers();
}
```

### 2. Enable Container Reuse

Containers are configured with `withReuse(true)` for faster local development. Add to `~/.testcontainers.properties`:

```properties
testcontainers.reuse.enable=true
```

### 3. Extend Base Classes

Use the provided base classes rather than configuring containers directly:

```java
// Prefer this:
class MyTest extends AbstractApiIntegrationTest { }

// Over this:
class MyTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    // ... boilerplate ...
}
```

### 4. Use Test Profiles

Base classes activate the `test` profile. Add service-specific test configuration in `application-test.yml`.

## Migration Guide

### From PERCEPTION's AbstractControllerIntegrationTest

```java
// Before
public class MyTest extends AbstractControllerIntegrationTest {
    // Custom container setup in base class
}

// After
public class MyTest extends AbstractApiIntegrationTest {
    // Containers already configured, just write tests
}
```

### From ODYSSEY's BaseIntegrationTest

```java
// Before
public class MyTest extends BaseIntegrationTest {
    // Uses TestContainersConfig singleton
}

// After
public class MyTest extends AbstractApiIntegrationTest {
    // ButterflyTestContainers provides the same singleton pattern
}
```

## Related Documentation

- [Testing Strategy](../docs/development/testing-strategy.md) - Ecosystem testing guidelines
- [CI/CD Pipeline](../docs/development/ci-cd.md) - Continuous integration setup
- [Contributing Guide](../docs/development/contributing.md) - Contribution standards

