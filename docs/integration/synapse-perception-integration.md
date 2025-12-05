# SYNAPSE-PERCEPTION Integration Guide

This guide documents how SYNAPSE communicates with PERCEPTION APIs through the `HttpConnector`, enabling AI agents to access intelligence, scenarios, and signals.

## Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           SYNAPSE                                        │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                 │
│  │  AI Agent   │───▶│   Action    │───▶│    Tool     │                 │
│  │  (PLATO)    │    │  Executor   │    │  Registry   │                 │
│  └─────────────┘    └──────┬──────┘    └─────────────┘                 │
│                            │                                            │
│                     ┌──────▼──────┐                                     │
│                     │ HttpConnector│                                     │
│                     │  + SSRF Prev │                                     │
│                     │  + Circuit   │                                     │
│                     │    Breaker   │                                     │
│                     └──────┬──────┘                                     │
└────────────────────────────┼────────────────────────────────────────────┘
                             │ HTTPS
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          PERCEPTION                                     │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                │
│  │ Intelligence│    │   Signals   │    │     RIM     │                │
│  │    API      │    │    API      │    │    API      │                │
│  └─────────────┘    └─────────────┘    └─────────────┘                │
└────────────────────────────────────────────────────────────────────────┘
```

## Key Components

### HttpConnector

The `HttpConnector` in SYNAPSE is the primary mechanism for calling external APIs, including PERCEPTION. It provides:

| Feature | Description |
|---------|-------------|
| **SSRF Prevention** | URL validation against allowlists |
| **Circuit Breaker** | Resilience4j circuit breaker for fault tolerance |
| **Input Validation** | Parameter sanitization |
| **Sandbox Mode** | Stricter restrictions for testing |
| **Metrics** | Prometheus metrics for monitoring |

### Tool Registry

PERCEPTION endpoints are registered as "tools" that AI agents can invoke:

```java
// Example tool registration for PERCEPTION signals API
Tool signalsTool = Tool.builder()
    .id("perception-signals")
    .name("PERCEPTION Signals")
    .description("Get weak signals from PERCEPTION")
    .connectorType(Tool.ConnectorType.HTTP)
    .callbackUrl("${perception.api.base-url}/api/v1/signals/detected")
    .allowedCallbackDomains(Set.of(
        "perception.internal",
        "perception.butterfly.example.com"
    ))
    .timeoutMs(30000L)
    .build();
```

---

## Configuration

### SYNAPSE Configuration

```yaml
# application.yml (SYNAPSE)
synapse:
  connectors:
    http:
      default-timeout-ms: 30000
      max-connections: 100
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 100

  # Perception-specific settings
  perception:
    api:
      base-url: https://perception.internal:8080
      connect-timeout-ms: 5000
      read-timeout-ms: 30000
```

### Tool Registration for PERCEPTION APIs

Register PERCEPTION tools in SYNAPSE's tool registry:

```java
@Configuration
public class PerceptionToolsConfiguration {

    @Bean
    public List<Tool> perceptionTools(
            @Value("${synapse.perception.api.base-url}") String baseUrl) {
        
        return List.of(
            // Signals API
            Tool.builder()
                .id("perception-signals-detected")
                .name("Get Detected Signals")
                .description("Retrieve weak signals from PERCEPTION")
                .connectorType(Tool.ConnectorType.HTTP)
                .callbackUrl(baseUrl + "/api/v1/signals/detected")
                .parameters(Map.of(
                    "hoursBack", Tool.ParameterSpec.builder()
                        .type("integer")
                        .defaultValue(24)
                        .required(false)
                        .build()
                ))
                .timeoutMs(15000L)
                .build(),
            
            // Intelligence API
            Tool.builder()
                .id("perception-events")
                .name("Get Events")
                .description("Retrieve events from PERCEPTION")
                .connectorType(Tool.ConnectorType.HTTP)
                .callbackUrl(baseUrl + "/api/v1/intelligence/events")
                .timeoutMs(20000L)
                .build(),
            
            // Scenarios API
            Tool.builder()
                .id("perception-scenarios")
                .name("Get Scenarios")
                .description("Retrieve scenarios from PERCEPTION")
                .connectorType(Tool.ConnectorType.HTTP)
                .callbackUrl(baseUrl + "/api/v1/intelligence/events/{eventId}/scenarios")
                .parameters(Map.of(
                    "eventId", Tool.ParameterSpec.builder()
                        .type("string")
                        .required(true)
                        .build()
                ))
                .timeoutMs(20000L)
                .build(),
            
            // RIM API
            Tool.builder()
                .id("perception-rim-mesh")
                .name("Get RIM Mesh")
                .description("Retrieve Reality Integration Mesh snapshot")
                .connectorType(Tool.ConnectorType.HTTP)
                .callbackUrl(baseUrl + "/api/v1/rim/mesh")
                .parameters(Map.of(
                    "scope", Tool.ParameterSpec.builder()
                        .type("string")
                        .defaultValue("global")
                        .build(),
                    "window", Tool.ParameterSpec.builder()
                        .type("string")
                        .defaultValue("PT5M")
                        .build()
                ))
                .timeoutMs(30000L)
                .build()
        );
    }
}
```

---

## Authentication Flow

### JWT Propagation

SYNAPSE propagates authentication context to PERCEPTION:

```
1. AI Agent (PLATO) initiates action
           │
           ▼
2. SYNAPSE extracts JWT from ExecutionContext
           │
           ▼
3. HttpConnector adds auth headers:
   - Authorization: Bearer <jwt>
   - X-Correlation-ID: <correlation-id>
   - X-User-ID: <user-id>
   - X-Service-ID: synapse
           │
           ▼
4. PERCEPTION validates JWT and processes request
```

### Header Configuration

The `HttpConnector` automatically adds these headers:

```java
// From HttpConnector.buildRequestConfig()
headers.add("X-Correlation-ID", action.getCorrelationId());
headers.add("X-Action-ID", action.getId());
headers.add("X-Tool-ID", tool.getId());

if (context.getSecurity().getUserId() != null) {
    headers.add("X-User-ID", context.getSecurity().getUserId());
}
if (context.getSecurity().getServiceId() != null) {
    headers.add("X-Service-ID", context.getSecurity().getServiceId());
}
```

### Service Account Authentication

For service-to-service calls (no user context):

```yaml
# SYNAPSE service account configuration
synapse:
  security:
    service-account:
      client-id: synapse-service
      client-secret: ${SYNAPSE_SERVICE_SECRET}
      token-endpoint: https://auth.butterfly.example.com/oauth/token
```

---

## Example Integration: Getting Signals for AI Analysis

### 1. Action Definition

```java
Action action = Action.builder()
    .id(UUID.randomUUID().toString())
    .toolId("perception-signals-detected")
    .correlationId(correlationId)
    .parameters(Map.of(
        "hoursBack", 24,
        "method", "GET"
    ))
    .build();
```

### 2. Execution Context

```java
ExecutionContext context = ExecutionContext.builder()
    .security(SecurityContext.builder()
        .userId(currentUser.getId())
        .serviceId("synapse")
        .permissions(Set.of("perception:signals:read"))
        .build())
    .environment(EnvironmentContext.builder()
        .environmentName("production")
        .build())
    .tracing(TracingContext.builder()
        .traceId(MDC.get("traceId"))
        .spanId(MDC.get("spanId"))
        .build())
    .build();
```

### 3. Action Execution

```java
@Service
@RequiredArgsConstructor
public class IntelligenceService {
    
    private final ActionExecutorRegistry executorRegistry;
    
    public Mono<List<WeakSignal>> getSignalsForAnalysis(int hoursBack) {
        Action action = buildSignalsAction(hoursBack);
        ExecutionContext context = buildContext();
        
        return executorRegistry.getExecutor(action)
            .flatMap(executor -> executor.execute(action, context))
            .map(result -> {
                if (result.getOutcomeStatus() == ActionResult.OutcomeStatus.SUCCESS) {
                    return parseSignals(result.getOutput());
                } else {
                    throw new PerceptionIntegrationException(
                        result.getErrorCode(), 
                        result.getErrorMessage()
                    );
                }
            });
    }
}
```

---

## Error Handling

### Circuit Breaker Behavior

The `HttpConnector` uses Resilience4j circuit breaker:

| State | Behavior |
|-------|----------|
| **CLOSED** | Normal operation, requests pass through |
| **OPEN** | Requests fail fast with `CIRCUIT_BREAKER_OPEN` |
| **HALF_OPEN** | Limited requests to test recovery |

### Error Response Mapping

```java
// PERCEPTION error → SYNAPSE ActionResult
switch (httpStatus) {
    case 400 -> errorCode = "HTTP_400";  // Bad Request
    case 401 -> errorCode = "HTTP_401";  // Unauthorized
    case 403 -> errorCode = "HTTP_403";  // Forbidden
    case 404 -> errorCode = "HTTP_404";  // Not Found
    case 429 -> errorCode = "HTTP_429";  // Rate Limited
    case 500 -> errorCode = "HTTP_500";  // Server Error
    case 503 -> errorCode = "HTTP_503";  // Service Unavailable
}
```

### Handling PERCEPTION Errors in SYNAPSE

```java
public Mono<ActionResult> handlePerceptionResponse(ActionResult result) {
    switch (result.getErrorCode()) {
        case "HTTP_429" -> {
            // Rate limited - schedule retry
            return scheduleRetry(result.getActionId(), Duration.ofSeconds(30));
        }
        case "HTTP_503" -> {
            // PERCEPTION unavailable - use cached data
            return useCachedData(result.getToolId());
        }
        case "URL_VALIDATION_FAILED" -> {
            // Security block - log and alert
            securityAlerter.alert("SSRF attempt blocked", result);
            return Mono.just(result);
        }
        default -> {
            return Mono.just(result);
        }
    }
}
```

---

## Security Considerations

### SSRF Prevention

The `HttpConnector` validates URLs against allowlists:

```java
// Tool-specific allowed domains
Tool tool = Tool.builder()
    .allowedCallbackDomains(Set.of(
        "perception.internal",
        "perception.butterfly.example.com"
    ))
    .build();
```

### Sandbox Mode

In sandbox environments, stricter rules apply:

- Only pre-approved domains allowed
- HTTPS required
- Reduced timeouts (5 seconds max)
- Additional logging

```java
// Sandbox detection
if ("sandbox".equalsIgnoreCase(context.getEnvironment().getEnvironmentName())) {
    // Apply stricter validation
}
```

### Input Validation

All parameters are validated before making requests:

```java
ValidationResult paramValidation = inputValidator.validateParameters(action.getParameters());
if (!paramValidation.isValid()) {
    return buildSecurityErrorResult(action, tool, 
        "INPUT_VALIDATION_FAILED", paramValidation.error(), startTime);
}
```

---

## Metrics and Monitoring

### Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `synapse.http.request.duration` | Timer | Request latency by tool and status |
| `synapse.http.ssrf.blocked` | Counter | SSRF attempts blocked |
| `synapse.http.validation.failed` | Counter | Input validation failures |
| `synapse.http.sandbox.blocked` | Counter | Sandbox-blocked requests |
| `synapse.http.sandbox.executions` | Counter | Sandbox mode executions |

### Prometheus Queries

```promql
# Average latency to PERCEPTION APIs
rate(synapse_http_request_duration_seconds_sum{tool_id=~"perception-.*"}[5m]) 
/ rate(synapse_http_request_duration_seconds_count{tool_id=~"perception-.*"}[5m])

# Error rate for PERCEPTION calls
sum(rate(synapse_http_request_duration_seconds_count{tool_id=~"perception-.*", status="ERROR"}[5m]))
/ sum(rate(synapse_http_request_duration_seconds_count{tool_id=~"perception-.*"}[5m]))

# Circuit breaker opens
increase(synapse_circuit_breaker_calls_total{name="httpConnector", kind="not_permitted"}[1h])
```

### Alerting Rules

```yaml
# alerts/synapse-perception.yml
groups:
  - name: synapse-perception-integration
    rules:
      - alert: PerceptionHighLatency
        expr: |
          histogram_quantile(0.95, 
            rate(synapse_http_request_duration_seconds_bucket{tool_id=~"perception-.*"}[5m])
          ) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency to PERCEPTION APIs"
          
      - alert: PerceptionCircuitBreakerOpen
        expr: |
          synapse_circuit_breaker_state{name="httpConnector"} == 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker to PERCEPTION is open"
```

---

## Troubleshooting

### Common Issues

#### 1. Connection Refused

```
Error: Connection refused to perception.internal:8080
```

**Resolution:**
- Verify PERCEPTION service is running
- Check network policies/firewalls
- Verify DNS resolution

#### 2. Authentication Failures

```
Error: HTTP_401 - Unauthorized
```

**Resolution:**
- Verify JWT is present in context
- Check JWT expiration
- Verify service account permissions

#### 3. Circuit Breaker Open

```
Error: CIRCUIT_BREAKER_OPEN - HTTP connector temporarily unavailable
```

**Resolution:**
- Check PERCEPTION health endpoint
- Review error rates in metrics
- Wait for circuit breaker recovery (default: 30s)

#### 4. URL Validation Failed

```
Error: URL_VALIDATION_FAILED - Domain 'unknown.domain.com' not in allowlist
```

**Resolution:**
- Add domain to tool's `allowedCallbackDomains`
- Verify URL is not being constructed from user input

### Debug Logging

Enable debug logging for integration issues:

```yaml
logging:
  level:
    com.z254.butterfly.synapse.connector.http: DEBUG
    io.github.resilience4j: DEBUG
```

---

## Related Documentation

- [PERCEPTION API Documentation](../../PERCEPTION/docs/API_DOCUMENTATION.md)
- [PERCEPTION Endpoint Topology](../../PERCEPTION/docs/API_ENDPOINT_TOPOLOGY.md)
- [SYNAPSE Architecture](../../SYNAPSE/docs/ARCHITECTURE.md)
- [BUTTERFLY Security Model](../security/security-model.md)
