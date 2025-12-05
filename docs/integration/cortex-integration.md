# CORTEX Integration Guide

## Overview

This guide covers how to integrate with CORTEX, the AI Agent Platform, including submitting tasks, receiving results, and consuming agent thought streams.

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
cortex:
  client:
    base-url: http://cortex:8080
    timeout-ms: 30000
    enabled: true

spring:
  kafka:
    bootstrap-servers: kafka:9092
```

### 3. Submit a Task

```java
@Autowired
private WebClient webClient;

public Mono<TaskResponse> submitTask(String agentId, TaskRequest request) {
    return webClient.post()
        .uri("/api/v1/agents/{agentId}/tasks", agentId)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(TaskResponse.class);
}
```

## API Integration

### Task Submission

**Request:**
```json
POST /api/v1/agents/{agentId}/tasks
{
    "taskId": "task-001",
    "description": "Analyze system health and recommend optimizations",
    "context": {
        "targetServices": ["order-service", "payment-service"],
        "timeWindow": "24h"
    },
    "constraints": {
        "maxTokens": 4000,
        "timeoutSeconds": 300,
        "requireApproval": false
    }
}
```

**Response:**
```json
{
    "taskId": "task-001",
    "episodeId": "ep-abc123",
    "agentId": "analyzer-agent",
    "status": "IN_PROGRESS",
    "createdAt": "2024-12-05T10:30:00Z"
}
```

### Task Status

```java
public Mono<TaskStatus> getTaskStatus(String taskId) {
    return webClient.get()
        .uri("/api/v1/tasks/{taskId}", taskId)
        .retrieve()
        .bodyToMono(TaskStatus.class);
}
```

### Multi-Agent Tasks

```java
public Mono<CollaborativeTaskResponse> submitCollaborativeTask(
        CollaborativeTaskRequest request) {
    return webClient.post()
        .uri("/api/v1/tasks/collaborative")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(CollaborativeTaskResponse.class);
}
```

## Kafka Integration

### Consuming Agent Thoughts

```java
@KafkaListener(topics = "cortex.agent.thoughts", groupId = "my-consumer")
public void handleAgentThought(ConsumerRecord<String, AgentThought> record) {
    AgentThought thought = record.value();
    
    log.info("Agent {} thought: {} - {}",
        thought.getAgentId(),
        thought.getThoughtType(),
        thought.getContent());
    
    // Process thought (visualization, logging, etc.)
}
```

### Consuming Episode Events

```java
@KafkaListener(topics = "cortex.agent.episodes", groupId = "my-consumer")
public void handleEpisodeEvent(ConsumerRecord<String, AgentEpisode> record) {
    AgentEpisode episode = record.value();
    
    if (episode.getStatus() == EpisodeStatus.COMPLETED) {
        log.info("Agent {} completed task with output: {}",
            episode.getAgentId(),
            episode.getFinalOutput());
    }
}
```

### Consuming Coordination Events

```java
@KafkaListener(topics = "cortex.coordination", groupId = "my-consumer")
public void handleCoordinationEvent(
        ConsumerRecord<String, MultiAgentCoordination> record) {
    MultiAgentCoordination coord = record.value();
    
    if (coord.getCoordinationType() == CoordinationType.HANDOFF) {
        log.info("Handoff from {} to {}",
            coord.getSourceAgentId(),
            coord.getTargetAgentIds());
    }
}
```

## Real-time Thought Streaming (SSE)

### JavaScript Client

```javascript
const eventSource = new EventSource(
    'http://cortex:8080/api/v1/agent-thoughts/stream/analyzer-agent'
);

eventSource.onmessage = (event) => {
    const thought = JSON.parse(event.data);
    console.log(`[${thought.thoughtType}] ${thought.content}`);
    displayThought(thought);
};

eventSource.onerror = (error) => {
    console.error('Stream error:', error);
};
```

### Java WebClient

```java
public Flux<AgentThought> streamThoughts(String agentId) {
    return webClient.get()
        .uri("/api/v1/agent-thoughts/stream/{agentId}", agentId)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(AgentThought.class);
}
```

## Using the Client Library

### Direct Usage

```java
@Configuration
public class CortexConfig {
    
    @Bean
    public WebClient cortexWebClient(
            @Value("${cortex.client.base-url}") String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
```

### With Resilience

```java
@Service
public class ResilientCortexClient {
    
    private final WebClient webClient;
    private final CircuitBreakerRegistry cbRegistry;
    
    public Mono<TaskResponse> submitTaskWithRetry(
            String agentId, TaskRequest request) {
        
        CircuitBreaker cb = cbRegistry.getServiceCircuitBreaker("cortex");
        
        return Mono.fromCallable(() -> 
                webClient.post()
                    .uri("/api/v1/agents/{agentId}/tasks", agentId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TaskResponse.class)
                    .block())
            .transformDeferred(CircuitBreakerOperator.of(cb))
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));
    }
}
```

## Error Handling

### Common Errors

| Status | Meaning | Action |
|--------|---------|--------|
| 400 | Invalid request | Fix request payload |
| 401 | Unauthorized | Check authentication |
| 429 | Rate limited | Back off and retry |
| 503 | Service unavailable | Retry with backoff |

### Error Response Format

```json
{
    "error": "TASK_REJECTED",
    "message": "Token budget exceeded for agent",
    "details": {
        "agentId": "analyzer-agent",
        "remainingBudget": 0,
        "requestedTokens": 1000
    },
    "timestamp": "2024-12-05T10:30:00Z",
    "correlationId": "abc-123"
}
```

## Best Practices

1. **Use Correlation IDs** - Pass `X-Correlation-ID` header for tracing
2. **Handle Timeouts** - Set appropriate timeouts for long-running tasks
3. **Monitor Budgets** - Check token budget before submitting large tasks
4. **Subscribe to Events** - Use Kafka for async task completion notification
5. **Implement Retries** - Use exponential backoff for transient failures

## Testing

### Unit Testing

```java
@Test
void shouldSubmitTask() {
    mockServer.expect(requestTo("/api/v1/agents/test-agent/tasks"))
        .andRespond(withSuccess(
            "{\"taskId\":\"task-001\",\"status\":\"IN_PROGRESS\"}",
            MediaType.APPLICATION_JSON));
    
    TaskResponse response = client.submitTask("test-agent", request).block();
    
    assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
}
```

### Integration Testing

Use the `butterfly-e2e` scenarios:
```bash
./scenario-runner -s cortex-agent-golden-path --dry-run
```

## Related Documentation

- [CORTEX Service](../services/cortex.md)
- [Agent Architecture](../architecture/agent-architecture.md)
- [CORTEX Operations](../operations/runbooks/cortex-operations.md)
