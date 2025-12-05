# AURORA Integration Guide

## Overview

This guide covers how to integrate with AURORA, the Self-Healing Platform, including receiving anomaly notifications, triggering manual remediations, and consuming healing events.

## Prerequisites

- Java 17+
- Maven/Gradle
- Access to Kafka cluster
- `butterfly-common` dependency

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>butterfly-common</artifactId>
    <version>0.3.0</version>
</dependency>
```

### 2. Configure Connection

```yaml
aurora:
  client:
    base-url: http://aurora:8080
    timeout-ms: 30000
    enabled: true

spring:
  kafka:
    bootstrap-servers: kafka:9092
```

### 3. Register for Anomaly Notifications

```java
@KafkaListener(topics = "aurora.anomaly.detection", groupId = "my-service")
public void handleAnomaly(ConsumerRecord<String, AnomalyDetection> record) {
    AnomalyDetection anomaly = record.value();
    
    if (anomaly.getAffectedService().equals(myServiceName)) {
        log.warn("Anomaly detected: {} - {}",
            anomaly.getAnomalyType(),
            anomaly.getDescription());
        
        // Take proactive action if needed
    }
}
```

## API Integration

### Query Anomalies

```java
public Flux<Anomaly> getAnomalies(String service, String severity) {
    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/v1/aurora/anomalies")
            .queryParam("service", service)
            .queryParam("severity", severity)
            .build())
        .retrieve()
        .bodyToFlux(Anomaly.class);
}
```

### Query Incidents

```java
public Flux<Incident> getOpenIncidents() {
    return webClient.get()
        .uri("/api/v1/aurora/incidents/open")
        .retrieve()
        .bodyToFlux(Incident.class);
}

public Mono<Incident> getIncidentDetails(String incidentId) {
    return webClient.get()
        .uri("/api/v1/aurora/incidents/{id}", incidentId)
        .retrieve()
        .bodyToMono(Incident.class);
}
```

### Manual Remediation Trigger

```java
public Mono<RemediationResponse> triggerRemediation(
        String incidentId, RemediationType type) {
    return webClient.post()
        .uri("/api/v1/aurora/remediation/{incidentId}/execute", incidentId)
        .bodyValue(Map.of("type", type.name()))
        .retrieve()
        .bodyToMono(RemediationResponse.class);
}
```

### Acknowledge Anomaly

```java
public Mono<Void> acknowledgeAnomaly(String anomalyId, String note) {
    return webClient.post()
        .uri("/api/v1/aurora/anomalies/{id}/acknowledge", anomalyId)
        .bodyValue(Map.of("note", note))
        .retrieve()
        .toBodilessEntity()
        .then();
}
```

## Kafka Integration

### Consuming Anomaly Events

```java
@KafkaListener(topics = "aurora.anomaly.detection", groupId = "my-consumer")
public void handleAnomalyDetection(
        ConsumerRecord<String, AnomalyDetection> record) {
    AnomalyDetection anomaly = record.value();
    
    log.info("Anomaly {} detected: {} on {} (severity: {})",
        anomaly.getAnomalyId(),
        anomaly.getAnomalyType(),
        anomaly.getAffectedService(),
        anomaly.getSeverity());
    
    // Alert, dashboard update, etc.
}
```

### Consuming Remediation Requests

```java
@KafkaListener(topics = "aurora.remediation.requests", groupId = "my-consumer")
public void handleRemediationRequest(
        ConsumerRecord<String, RemediationRequest> record) {
    RemediationRequest request = record.value();
    
    log.info("Remediation requested: {} for incident {}",
        request.getRemediationType(),
        request.getIncidentId());
    
    // Track remediation progress
}
```

### Consuming Remediation Results

```java
@KafkaListener(topics = "aurora.remediation.results", groupId = "my-consumer")
public void handleRemediationResult(
        ConsumerRecord<String, RemediationResult> record) {
    RemediationResult result = record.value();
    
    if (result.isSuccess()) {
        log.info("Remediation {} succeeded in {}ms",
            result.getResultId(),
            result.getDurationMs());
    } else {
        log.error("Remediation {} failed: {}",
            result.getResultId(),
            result.getErrorMessage());
    }
}
```

## Integrating Your Service with AURORA

### 1. Expose Health Endpoints

AURORA needs health information to verify remediations:

```java
@RestController
@RequestMapping("/health")
public class HealthController {
    
    @GetMapping("/detailed")
    public HealthStatus getDetailedHealth() {
        return HealthStatus.builder()
            .status(Status.UP)
            .latencyP95(calculateP95Latency())
            .errorRate(calculateErrorRate())
            .cpuUsage(getCpuUsage())
            .memoryUsage(getMemoryUsage())
            .build();
    }
}
```

### 2. Emit Custom Metrics

Help AURORA detect anomalies specific to your service:

```java
@Component
public class ServiceMetrics {
    
    private final MeterRegistry registry;
    
    @PostConstruct
    public void registerMetrics() {
        // Business-specific metrics
        Gauge.builder("order_queue_depth", orderQueue, Queue::size)
            .description("Orders pending processing")
            .register(registry);
        
        Timer.builder("payment_processing_time")
            .description("Payment processing duration")
            .register(registry);
    }
}
```

### 3. Support Remediation Actions

Make your service remediation-friendly:

```java
@RestController
@RequestMapping("/admin")
public class AdminController {
    
    @PostMapping("/graceful-restart")
    public Mono<Void> gracefulRestart() {
        return drainConnections()
            .then(flushCaches())
            .then(restart());
    }
    
    @PostMapping("/clear-cache")
    public Mono<Void> clearCache() {
        return cacheManager.clear();
    }
    
    @PostMapping("/reset-connections")
    public Mono<Void> resetConnections() {
        return connectionPool.reset();
    }
}
```

## Using the CAPSULE Client for Incident History

```java
@Autowired
private IncidentCapsuleClient capsuleClient;

public Flux<IncidentCapsule> getRecentIncidents(String service) {
    return capsuleClient.getByService(service)
        .take(10);
}

public Mono<MttrStats> getMttrStats(String service) {
    return capsuleClient.getMttrStats(service);
}

public Flux<IncidentCapsule> getRelatedIncidents(String incidentId) {
    return capsuleClient.getRelatedIncidents(incidentId);
}
```

## Webhooks

### Register for Notifications

```java
@PostMapping("/webhooks/aurora")
public Mono<Void> handleAuroraWebhook(@RequestBody WebhookEvent event) {
    return switch (event.getType()) {
        case "ANOMALY_DETECTED" -> handleAnomaly(event);
        case "INCIDENT_CREATED" -> handleIncident(event);
        case "REMEDIATION_STARTED" -> handleRemediationStarted(event);
        case "REMEDIATION_COMPLETED" -> handleRemediationCompleted(event);
        default -> Mono.empty();
    };
}
```

### Webhook Payload

```json
{
    "type": "ANOMALY_DETECTED",
    "timestamp": "2024-12-05T10:30:00Z",
    "correlationId": "abc-123",
    "payload": {
        "anomalyId": "anomaly-001",
        "anomalyType": "LATENCY_SPIKE",
        "affectedService": "order-service",
        "severity": "SEV2_HIGH",
        "description": "P95 latency increased 400%"
    }
}
```

## Error Handling

### Common Errors

| Status | Meaning | Action |
|--------|---------|--------|
| 400 | Invalid request | Fix request payload |
| 404 | Incident not found | Verify incident ID |
| 409 | Remediation in progress | Wait for completion |
| 503 | AURORA unavailable | Retry with backoff |

## Best Practices

1. **Don't Suppress Anomalies** - Let AURORA learn from all anomalies
2. **Provide Good Health Data** - Detailed metrics enable better detection
3. **Support Safe Remediations** - Implement graceful restart, cache clear
4. **Handle Notifications Quickly** - Process Kafka messages efficiently
5. **Test with Chaos** - Validate your service survives AURORA remediations

## Testing

### Mock AURORA Responses

```java
@Test
void shouldHandleAnomalyNotification() {
    AnomalyDetection anomaly = AnomalyDetection.newBuilder()
        .setAnomalyId("test-001")
        .setAnomalyType(AnomalyType.LATENCY_SPIKE)
        .setAffectedService("order-service")
        .build();
    
    handler.handleAnomaly(new ConsumerRecord<>("topic", 0, 0, "key", anomaly));
    
    verify(alertService).sendAlert(any());
}
```

### Integration Testing

Use `butterfly-e2e` scenarios:
```bash
./scenario-runner -s aurora-auto-remediation --dry-run
```

## Related Documentation

- [AURORA Service](../services/aurora.md)
- [Self-Healing Architecture](../architecture/self-healing-architecture.md)
- [AURORA Operations](../operations/runbooks/aurora-operations.md)
