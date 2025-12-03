# BUTTERFLY API Documentation

> Comprehensive API reference for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: API consumers, developers, integration engineers

---

## Overview

BUTTERFLY exposes REST APIs across all services, providing programmatic access to the entire cognitive intelligence platform. This section documents the unified API landscape.

## API Documentation

| Document | Description |
|----------|-------------|
| [API Catalog](api-catalog.md) | Unified catalog of all endpoints |
| [Authentication](authentication.md) | JWT, API keys, OAuth2 patterns |
| [Error Handling](error-handling.md) | Standardized error codes and responses |
| [Rate Limiting](rate-limiting.md) | Rate limit policies and headers |
| [Versioning](versioning.md) | API versioning strategy |

---

## Quick Reference

### Service Endpoints

| Service | Base URL | Swagger UI | Health |
|---------|----------|------------|--------|
| PERCEPTION | `http://localhost:8080/api/v1` | [Swagger](http://localhost:8080/swagger-ui.html) | `/actuator/health` |
| CAPSULE | `http://localhost:8081/api/v1` | [Swagger](http://localhost:8081/swagger-ui.html) | `/actuator/health` |
| ODYSSEY | `http://localhost:8082/api/v1` | [Swagger](http://localhost:8082/swagger-ui.html) | `/actuator/health` |
| PLATO | `http://localhost:8083/api/v1` | [Swagger](http://localhost:8083/swagger-ui.html) | `/actuator/health` |
| NEXUS | `http://localhost:8084/api/v1` | [Swagger](http://localhost:8084/swagger-ui.html) | `/actuator/health` |

### Authentication Summary

```http
# JWT Bearer Token
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

# API Key
X-API-Key: butterfly_live_abc123xyz789...
```

### Common Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes* | Authentication token |
| `Content-Type` | Yes | `application/json` |
| `Accept` | No | `application/json` |
| `X-Request-ID` | No | Correlation ID for tracing |
| `X-Correlation-ID` | No | Parent trace ID |

*Some endpoints may be public (health checks, documentation)

### Standard Response Format

```json
{
  "data": { ... },
  "meta": {
    "requestId": "req-abc123",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Error Response Format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request parameters",
    "details": [...]
  },
  "meta": {
    "requestId": "req-abc123",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

---

## API Design Principles

### RESTful Resources

APIs follow REST conventions:

```http
GET    /api/v1/capsules           # List resources
POST   /api/v1/capsules           # Create resource
GET    /api/v1/capsules/{id}      # Get single resource
PUT    /api/v1/capsules/{id}      # Update resource
DELETE /api/v1/capsules/{id}      # Delete resource
```

### Query Parameters

Standard query parameters for collections:

| Parameter | Type | Description |
|-----------|------|-------------|
| `page` | int | Page number (0-indexed) |
| `size` | int | Page size (default: 20) |
| `sort` | string | Sort field and direction |
| `filter` | string | Filter expression |

### Pagination Response

```json
{
  "data": [...],
  "meta": {
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 150,
      "totalPages": 8
    }
  }
}
```

---

## Interactive Documentation

Each service provides OpenAPI 3.0 documentation via Swagger UI:

1. Start the service
2. Navigate to `http://localhost:{port}/swagger-ui.html`
3. Use "Try it out" for interactive testing

### Swagger UI Features

- **Endpoint exploration**: Browse all endpoints
- **Request builder**: Construct requests visually
- **Response preview**: See response schemas
- **Authentication**: Configure auth headers

---

## SDK and Client Libraries

For easier integration, consider:

- [Java Client Guide](../integration/sdks/java.md)
- [Python Client Guide](../integration/sdks/python.md)
- [TypeScript Client Guide](../integration/sdks/typescript.md)

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Documentation Index](../index.md) | Main documentation portal |
| [Integration Guide](../integration/README.md) | Integration patterns |
| [First Steps](../getting-started/first-steps.md) | First API calls |

