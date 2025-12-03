# Adding a New Connector

> Step-by-step guide to adding a new data source connector in PERCEPTION

**Last Updated**: 2025-12-03  
**Target Audience**: Backend developers working on PERCEPTION acquisition

---

## Overview

PERCEPTION uses Apache Camel route templates to define connectors for different data sources. This guide walks through adding a new connector type.

## Prerequisites

- PERCEPTION development environment set up (see [DEV_ENVIRONMENT_SETUP.md](../../PERCEPTION/docs/DEV_ENVIRONMENT_SETUP.md))
- Familiarity with Apache Camel basics
- Understanding of PERCEPTION's acquisition architecture

## Architecture Overview

```
perception-acquisition/
├── config/
│   └── ConnectorProperties.java      # Resilience settings
├── route/
│   ├── templates/                     # Route templates by category
│   │   ├── InfrastructureRouteTemplates.java
│   │   ├── WebhookRouteTemplates.java
│   │   ├── StorageRouteTemplates.java
│   │   └── ...
│   └── processor/                     # Shared processors
│       ├── NormalizationProcessor.java
│       ├── ContentHashProcessor.java
│       └── KafkaPublishProcessor.java
└── service/
    └── AcquisitionService.java        # Route management
```

---

## Step 1: Choose the Right Template Category

Connectors are organized by category:

| Category | File | Example Sources |
|----------|------|-----------------|
| Infrastructure | `InfrastructureRouteTemplates.java` | Vault, Consul |
| Webhook | `WebhookRouteTemplates.java` | Generic webhooks |
| Storage | `StorageRouteTemplates.java` | S3, GCS, Azure Blob |
| Database | `DatabaseRouteTemplates.java` | JDBC, MongoDB |
| SaaS | `SaaSRouteTemplates.java` | Salesforce, HubSpot |
| Social Media | `SocialMediaRouteTemplates.java` | Twitter, Reddit |
| Communication | `CommunicationRouteTemplates.java` | Slack, Teams |
| Email | `EmailRouteTemplates.java` | IMAP, POP3 |

---

## Step 2: Define the Route Template

Create or extend an existing template class. Here's a minimal example:

```java
package com.z254.butterfly.perception.acquisition.route.templates;

import com.z254.butterfly.perception.acquisition.config.ConnectorProperties;
import com.z254.butterfly.perception.acquisition.route.config.IngestionRouteConfiguration;
import com.z254.butterfly.perception.acquisition.route.processor.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyNewRouteTemplates extends RouteBuilder {

    private final IngestionRouteConfiguration ingestionRouteConfiguration;
    private final ConnectorProperties connectorProperties;
    private final NormalizationProcessor normalizationProcessor;
    private final ContentHashProcessor contentHashProcessor;
    private final QualityScoringProcessor qualityScoringProcessor;
    private final KafkaPublishProcessor kafkaPublishProcessor;
    private final IdempotentRepository ingestionIdempotentRepository;

    @Override
    public void configure() {
        // Apply standard error handling and DLQ configuration
        ingestionRouteConfiguration.apply(this);
        
        // Define your templates
        defineMySourceTemplate();
    }

    private void defineMySourceTemplate() {
        routeTemplate("my-source-template")
            // Required parameters
            .templateParameter("routeId")
            .templateParameter("sourceId")
            .templateParameter("sourceName")
            .templateParameter("sourceType")
            .templateParameter("tenantId")
            .templateParameter("consumerUri")
            // Optional parameters with defaults
            .templateParameter("shadowMode", "false")
            .templateParameter("dlqTopic", "perception.dlq")
            .templateParameter("publishAvro", "false")
            
            // Route definition
            .from("{{consumerUri}}")
            .routeId("{{routeId}}")
            
            // Set standard headers
            .setHeader("sourceId", constant("{{sourceId}}"))
            .setHeader("sourceName", constant("{{sourceName}}"))
            .setHeader("sourceType", constant("{{sourceType}}"))
            .setHeader("tenantId", constant("{{tenantId}}"))
            
            // Apply resilience (circuit breaker + bulkhead)
            .circuitBreaker()
                .resilience4jConfiguration()
                    .failureRateThreshold(connectorProperties.getResilience().getCircuitBreakerFailureRateThreshold())
                    .slidingWindowSize(connectorProperties.getResilience().getCircuitBreakerSlidingWindowSize())
                    .bulkheadEnabled(connectorProperties.getResilience().isBulkheadEnabled())
                    .bulkheadMaxConcurrentCalls(connectorProperties.getResilience().getBulkheadMaxConcurrentCalls())
                .end()
                
                // Transform and process content
                .convertBodyTo(String.class)
                .process(normalizationProcessor)
                .process(contentHashProcessor)
            .onFallback()
                .log("Circuit breaker fallback for route {{routeId}}")
                .throwException(new IllegalStateException("Circuit breaker open"))
            .end()
            
            // Idempotency check (skip duplicates)
            .idempotentConsumer(header("idempotencyKey"), ingestionIdempotentRepository)
                .skipDuplicate(true)
            
            // Quality scoring and publish
            .process(qualityScoringProcessor)
            .process(kafkaPublishProcessor)
            .toD("kafka:${header.dynamicKafkaTopic}");
    }
}
```

---

## Step 3: Add Configuration Properties (Optional)

If your connector needs specific configuration, add it to `ConnectorProperties.java`:

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "perception.connectors")
public class ConnectorProperties {
    
    private Resilience resilience = new Resilience();
    private MyNewSource myNewSource = new MyNewSource();  // Add this

    @Data
    public static class MyNewSource {
        private String apiKey;
        private String baseUrl = "https://api.example.com";
        private int timeoutMs = 30000;
    }
}
```

Then configure in `application.yml`:

```yaml
perception:
  connectors:
    my-new-source:
      api-key: ${MY_SOURCE_API_KEY:}
      base-url: https://api.example.com
      timeout-ms: 30000
```

---

## Step 4: Create a Custom Processor (If Needed)

For source-specific transformations, create a processor:

```java
package com.z254.butterfly.perception.acquisition.route.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MySourceMapperProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getMessage().getBody(String.class);
        JsonNode json = objectMapper.readTree(body);
        
        // Extract and map fields to standard format
        String title = json.path("title").asText();
        String content = json.path("body").asText();
        String timestamp = json.path("created_at").asText();
        
        // Set normalized headers
        exchange.getMessage().setHeader("contentTitle", title);
        exchange.getMessage().setHeader("contentTimestamp", timestamp);
        exchange.getMessage().setBody(content);
    }
}
```

---

## Step 5: Register the Source Type

Add your source type to the `Source` model enum or validation:

```java
// In Source.java or relevant enum
public enum SourceType {
    HTTP,
    RSS,
    WEBHOOK,
    MY_NEW_SOURCE,  // Add this
    // ...
}
```

---

## Step 6: Write Tests

### Unit Test for the Processor

```java
@ExtendWith(MockitoExtension.class)
class MySourceMapperProcessorTest {

    @InjectMocks
    private MySourceMapperProcessor processor;

    @Mock
    private Exchange exchange;

    @Mock
    private org.apache.camel.Message message;

    @Test
    void shouldMapSourceSpecificFields() throws Exception {
        // Given
        String input = """
            {"title": "Test Title", "body": "Content here", "created_at": "2024-01-01T00:00:00Z"}
            """;
        when(exchange.getMessage()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn(input);

        // When
        processor.process(exchange);

        // Then
        verify(message).setHeader("contentTitle", "Test Title");
        verify(message).setBody("Content here");
    }
}
```

### Integration Test for the Route

```java
@SpringBootTest
@CamelSpringBootTest
class MySourceRouteIntegrationTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producer;

    @EndpointInject("mock:kafka:perception.content.raw")
    private MockEndpoint mockKafka;

    @Test
    void shouldProcessAndPublishContent() throws Exception {
        // Given
        mockKafka.expectedMessageCount(1);
        
        // When
        producer.sendBody("direct:my-source-test", "test content");
        
        // Then
        mockKafka.assertIsSatisfied();
    }
}
```

---

## Step 7: Document the Connector

Add documentation in `PERCEPTION/docs/acquisition/`:

```markdown
# My New Source Connector

## Overview
Connects to MySource API to ingest content.

## Configuration
| Property | Description | Default |
|----------|-------------|---------|
| `api-key` | API key for authentication | (required) |
| `base-url` | Base URL for the API | `https://api.example.com` |
| `timeout-ms` | Request timeout | `30000` |

## Usage
1. Configure credentials in `application.yml`
2. Register a source via POST `/api/v1/acquisition/sources`
3. Monitor via `/api/v1/monitoring/health`
```

---

## Checklist

Before submitting your PR:

- [ ] Route template follows standard patterns (resilience, idempotency, DLQ)
- [ ] Configuration properties documented
- [ ] Unit tests for processors (>80% coverage)
- [ ] Integration test for route
- [ ] Documentation added/updated
- [ ] No hardcoded credentials
- [ ] Error handling follows DLQ strategy

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Acquisition Connectors](../../PERCEPTION/docs/ACQUISITION_CONNECTORS.md) | Full connector reference |
| [Ingestion DLQ Strategy](../../PERCEPTION/docs/INGESTION_DLQ_STRATEGY.md) | Error handling patterns |
| [Ingestion Guardrails](../../PERCEPTION/docs/INGESTION_GUARDRAILS.md) | Quality gates |
| [Apache Camel Docs](https://camel.apache.org/components/4.0.x/) | Camel component reference |

