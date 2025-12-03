# BUTTERFLY Java SDK

> Official Java SDK for BUTTERFLY ecosystem integration

**Last Updated**: 2025-12-03  
**SDK Version**: 1.0.0  
**Java Version**: 17+

---

## Installation

### Maven

```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>butterfly-sdk</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Avro schemas -->
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>butterfly-common</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.z254.butterfly:butterfly-sdk:1.0.0'
implementation 'com.z254.butterfly:butterfly-common:1.0.0'
```

---

## Quick Start

### Initialize Client

```java
import com.z254.butterfly.sdk.ButterflyClient;
import com.z254.butterfly.sdk.config.ButterflyConfig;

// Configure client
ButterflyConfig config = ButterflyConfig.builder()
    .nexusUrl("http://localhost:8083")
    .apiKey("your-api-key")
    .connectTimeout(Duration.ofSeconds(10))
    .readTimeout(Duration.ofSeconds(30))
    .build();

// Create client
ButterflyClient client = ButterflyClient.create(config);
```

### Spring Boot Integration

```java
@Configuration
public class ButterflyConfiguration {
    
    @Bean
    public ButterflyClient butterflyClient(
            @Value("${butterfly.nexus.url}") String nexusUrl,
            @Value("${butterfly.api-key}") String apiKey) {
        
        return ButterflyClient.create(
            ButterflyConfig.builder()
                .nexusUrl(nexusUrl)
                .apiKey(apiKey)
                .build()
        );
    }
}
```

```yaml
# application.yml
butterfly:
  nexus:
    url: http://localhost:8083
  api-key: ${BUTTERFLY_API_KEY}
```

---

## API Reference

### CAPSULE Operations

#### Create Capsule

```java
import com.z254.butterfly.sdk.model.CapsuleRequest;
import com.z254.butterfly.sdk.model.CapsuleResponse;

CapsuleRequest request = CapsuleRequest.builder()
    .scopeId("rim:entity:finance:EURUSD")
    .timestamp(Instant.now())
    .resolution(60)  // 1-minute resolution
    .vantageMode(VantageMode.OMNISCIENT)
    .build();

CapsuleResponse response = client.capsule().create(request);

System.out.println("Created capsule: " + response.getCapsuleId());
```

#### Query Capsule

```java
import com.z254.butterfly.sdk.model.CapsuleQuery;
import com.z254.butterfly.sdk.model.Capsule;

CapsuleQuery query = CapsuleQuery.builder()
    .scopeId("rim:entity:finance:EURUSD")
    .fromTime(Instant.now().minus(Duration.ofHours(1)))
    .toTime(Instant.now())
    .vantageMode(VantageMode.CURRENT)
    .build();

List<Capsule> capsules = client.capsule().query(query);

for (Capsule capsule : capsules) {
    System.out.printf("Capsule %s at %s%n", 
        capsule.getCapsuleId(), capsule.getTimestamp());
}
```

#### Travel to Point in Time

```java
import com.z254.butterfly.sdk.model.TimePoint;
import com.z254.butterfly.sdk.model.TimelineView;

TimePoint point = TimePoint.of(
    "rim:entity:finance:EURUSD",
    Instant.parse("2024-12-01T12:00:00Z")
);

TimelineView view = client.capsule().travelTo(point);

System.out.println("State at point: " + view.getState());
System.out.println("Visible events: " + view.getVisibleEvents());
```

### ODYSSEY Operations

#### Query Graph

```java
import com.z254.butterfly.sdk.model.GraphQuery;
import com.z254.butterfly.sdk.model.GraphResult;

GraphQuery query = GraphQuery.builder()
    .startNode("rim:entity:finance:EURUSD")
    .traversalDepth(3)
    .relationTypes(List.of("INFLUENCES", "CORRELATES_WITH"))
    .build();

GraphResult result = client.odyssey().query(query);

for (var node : result.getNodes()) {
    System.out.println("Node: " + node.getRimNodeId());
}

for (var edge : result.getEdges()) {
    System.out.printf("%s -> %s (%s)%n", 
        edge.getSource(), edge.getTarget(), edge.getRelationType());
}
```

#### Get Actor Profile

```java
import com.z254.butterfly.sdk.model.ActorProfile;

ActorProfile actor = client.odyssey().getActor("rim:actor:regulator:FED");

System.out.println("Actor: " + actor.getName());
System.out.println("Type: " + actor.getActorType());
System.out.println("Influence score: " + actor.getInfluenceScore());
```

#### Find Paths

```java
import com.z254.butterfly.sdk.model.PathQuery;
import com.z254.butterfly.sdk.model.Path;

PathQuery query = PathQuery.builder()
    .source("rim:entity:finance:EURUSD")
    .target("rim:entity:finance:GBPUSD")
    .maxDepth(5)
    .build();

List<Path> paths = client.odyssey().findPaths(query);

for (Path path : paths) {
    System.out.println("Path: " + path.getNodes());
    System.out.println("Weight: " + path.getTotalWeight());
}
```

### PERCEPTION Operations

#### Submit Event

```java
import com.z254.butterfly.sdk.model.PerceptionEventInput;
import com.z254.butterfly.sdk.model.EventAck;

PerceptionEventInput event = PerceptionEventInput.builder()
    .eventType(EventType.MARKET)
    .title("Significant market movement detected")
    .entities(List.of("rim:entity:finance:EURUSD"))
    .source("external-system")
    .trustScore(0.8)
    .build();

EventAck ack = client.perception().submitEvent(event);

System.out.println("Event ID: " + ack.getEventId());
```

#### Query Scenarios

```java
import com.z254.butterfly.sdk.model.ScenarioQuery;
import com.z254.butterfly.sdk.model.Scenario;

ScenarioQuery query = ScenarioQuery.builder()
    .entityId("rim:entity:finance:EURUSD")
    .scenarioType(ScenarioType.MARKET_IMPACT)
    .limit(10)
    .build();

List<Scenario> scenarios = client.perception().queryScenarios(query);

for (Scenario scenario : scenarios) {
    System.out.printf("Scenario: %s (probability: %.2f)%n",
        scenario.getTitle(), scenario.getProbability());
}
```

### PLATO Operations

#### Execute Plan

```java
import com.z254.butterfly.sdk.model.PlanRequest;
import com.z254.butterfly.sdk.model.PlanResult;

PlanRequest plan = PlanRequest.builder()
    .name("Analysis Plan")
    .engine(PlatoEngine.RESEARCH_OS)
    .parameters(Map.of(
        "topic", "market-volatility",
        "depth", "comprehensive"
    ))
    .build();

PlanResult result = client.plato().execute(plan);

System.out.println("Plan ID: " + result.getPlanId());
System.out.println("Status: " + result.getStatus());
```

#### Get Engine Status

```java
import com.z254.butterfly.sdk.model.EngineStatus;

EngineStatus status = client.plato().getEngineStatus(PlatoEngine.FEATURE_FORGE);

System.out.println("Engine: " + status.getEngine());
System.out.println("Status: " + status.getState());
System.out.println("Queue depth: " + status.getQueueDepth());
```

---

## Kafka Integration

### Consuming Events

```java
import com.z254.butterfly.common.hft.RimFastEvent;
import com.z254.butterfly.common.kafka.RimKafkaContracts;

// Get consumer config
Map<String, Object> config = RimKafkaContracts.fastPathConsumerConfig(
    "localhost:9092",
    "http://localhost:8081",
    "my-consumer-group"
);

// Create consumer
KafkaConsumer<String, RimFastEvent> consumer = new KafkaConsumer<>(config);
consumer.subscribe(Collections.singletonList("rim.fast-path"));

while (true) {
    ConsumerRecords<String, RimFastEvent> records = 
        consumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, RimFastEvent> record : records) {
        RimFastEvent event = record.value();
        processEvent(event);
    }
    
    consumer.commitSync();
}
```

### Spring Kafka Listener

```java
@Component
public class FastPathEventListener {
    
    @KafkaListener(
        topics = "rim.fast-path",
        groupId = "my-app-group",
        containerFactory = "rimFastEventListenerContainerFactory"
    )
    public void handleEvent(
            @Payload RimFastEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment ack) {
        
        try {
            log.info("Received event for: {}", event.getRimNodeId());
            processEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing event", e);
            // Let it retry or go to DLQ
            throw e;
        }
    }
}
```

---

## Error Handling

### Exception Types

| Exception | Description | Retry? |
|-----------|-------------|--------|
| `ButterflyClientException` | Base exception | Depends |
| `AuthenticationException` | Auth failure | No |
| `RateLimitException` | Rate limited | Yes (with backoff) |
| `ResourceNotFoundException` | Resource not found | No |
| `ServiceUnavailableException` | Service down | Yes |

### Retry Configuration

```java
import com.z254.butterfly.sdk.retry.RetryConfig;

ButterflyConfig config = ButterflyConfig.builder()
    .nexusUrl("http://localhost:8083")
    .apiKey("your-api-key")
    .retryConfig(RetryConfig.builder()
        .maxAttempts(3)
        .initialBackoff(Duration.ofMillis(100))
        .maxBackoff(Duration.ofSeconds(10))
        .backoffMultiplier(2.0)
        .retryOn(ServiceUnavailableException.class)
        .build())
    .build();
```

### Error Handling Example

```java
try {
    CapsuleResponse response = client.capsule().create(request);
} catch (AuthenticationException e) {
    log.error("Authentication failed - check API key");
    throw e;
} catch (RateLimitException e) {
    log.warn("Rate limited - retry after: {}s", e.getRetryAfterSeconds());
    Thread.sleep(e.getRetryAfterSeconds() * 1000);
    // Retry
} catch (ResourceNotFoundException e) {
    log.warn("Resource not found: {}", e.getResourceId());
    // Handle missing resource
} catch (ButterflyClientException e) {
    log.error("API error: {} - {}", e.getErrorCode(), e.getMessage());
    throw e;
}
```

---

## Observability

### Metrics

The SDK exposes Micrometer metrics:

```java
// Enable metrics
MeterRegistry registry = new SimpleMeterRegistry();

ButterflyConfig config = ButterflyConfig.builder()
    .nexusUrl("http://localhost:8083")
    .apiKey("your-api-key")
    .meterRegistry(registry)
    .build();
```

Available metrics:
- `butterfly.sdk.requests` - Request count by operation
- `butterfly.sdk.request.duration` - Request latency histogram
- `butterfly.sdk.errors` - Error count by type

### Tracing

```java
// Enable tracing with OpenTelemetry
Tracer tracer = GlobalOpenTelemetry.getTracer("butterfly-sdk");

ButterflyConfig config = ButterflyConfig.builder()
    .nexusUrl("http://localhost:8083")
    .apiKey("your-api-key")
    .tracer(tracer)
    .build();
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Client Integration Guide](../client-guide.md) | Integration patterns |
| [Kafka Contracts](../kafka-contracts.md) | Event schemas |
| [API Authentication](../../api/authentication.md) | Auth details |
| [butterfly-common](../../services/butterfly-common.md) | Shared library |

