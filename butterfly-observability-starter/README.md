# BUTTERFLY Observability Starter

> Spring Boot starter for BUTTERFLY observability features

**Version**: 0.1.0

## Overview

This starter provides auto-configured observability infrastructure for BUTTERFLY services:

- **Correlation ID Propagation** - Automatic propagation across HTTP, Kafka, and WebClient
- **OpenTelemetry Integration** - Distributed tracing with OTLP export
- **Structured Logging** - JSON-formatted logs with correlation context
- **Prometheus Metrics** - Micrometer metrics export
- **Agent Thought Visualization** - Real-time streaming of CORTEX agent reasoning

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.z254.butterfly</groupId>
    <artifactId>butterfly-observability-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Minimal Configuration

The starter works out of the box with sensible defaults. For production, configure:

```yaml
butterfly:
  observability:
    enabled: true
    tracing:
      service-name: ${spring.application.name}
      environment: production

management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

## Features

### Correlation ID

Automatically propagates correlation IDs:
- Extracts from incoming HTTP headers (`X-Correlation-ID`)
- Generates if missing
- Adds to MDC for logging
- Propagates to downstream HTTP calls
- Adds to Kafka message headers

### Structured Logging

JSON-formatted logs include:
```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "message": "Processing request",
  "correlationId": "abc123",
  "traceId": "def456",
  "spanId": "789xyz",
  "service": "my-service",
  "environment": "production"
}
```

### Agent Thought Visualization

Stream agent thoughts in real-time:
```bash
curl -N http://localhost:8080/api/v1/agent-thoughts/stream/agent-001
```

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `butterfly.observability.enabled` | `true` | Enable observability features |
| `butterfly.observability.correlation-id.header-name` | `X-Correlation-ID` | Header name |
| `butterfly.observability.correlation-id.generate-if-missing` | `true` | Generate if not present |
| `butterfly.observability.tracing.enabled` | `true` | Enable tracing |
| `butterfly.observability.logging.structured` | `true` | Use JSON logging |
| `butterfly.observability.agent-thoughts.enabled` | `true` | Enable thought endpoints |
| `butterfly.observability.agent-thoughts.kafka-topic` | `cortex.agent.thoughts` | Kafka topic |

## Integration with Logback

Include the provided logback configuration:

```xml
<!-- logback-spring.xml -->
<configuration>
    <include resource="logback-spring-butterfly.xml"/>
</configuration>
```

## Health Indicators

The starter registers health indicators for:
- OpenTelemetry collector connectivity
- Kafka connectivity (for agent thoughts)

Check via `/actuator/health`.
