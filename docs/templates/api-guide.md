# {API_FAMILY} API Guide

> **Purpose**: Explain how to use the {API_FAMILY} APIs, beyond the raw OpenAPI reference.  
> **Audience**: Client developers and integrators.  
> **Last Updated**: {DATE}

---

## 1. Overview

- What this API family is for and when to use it.
- Key use cases and capabilities.

## 2. Authentication

- Required authentication method (JWT, API key, OAuth2).
- How to obtain credentials.
- Example headers.

```bash
# Example authenticated request
curl -H "Authorization: Bearer $TOKEN" \
     https://api.example.com/api/v1/resource
```

## 3. Common Patterns

### Request/Response Format

- Content types (usually `application/json`).
- Standard response envelope if applicable.

### Error Handling

- Standard error response format.
- Common error codes and meanings.

```json
{
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "The requested resource was not found",
    "details": {}
  }
}
```

### Idempotency

- How idempotency works for this API.
- Required headers (e.g., `X-Idempotency-Key`).

### Pagination

- How to paginate through results.
- Example query parameters (`page`, `size`, `cursor`).

## 4. Key Endpoints

### Endpoint 1: {Name}

**When to use**: Description of use case.

**Request:**
```http
POST /api/v1/resource
Content-Type: application/json

{
  "field": "value"
}
```

**Response:**
```json
{
  "id": "resource-123",
  "field": "value",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

### Endpoint 2: {Name}

**When to use**: Description of use case.

<!-- Add more endpoints as needed -->

## 5. Workflows and Recipes

### Recipe 1: {Name}

End-to-end flow that combines multiple endpoints.

1. First, call Endpoint A to...
2. Then, use the response to call Endpoint B...
3. Finally, verify by calling Endpoint C...

## 6. Performance and Limits

- Rate limits (requests per second/minute).
- Payload size limits.
- Expected latency (p50, p99).

| Limit | Value |
|-------|-------|
| Rate limit | 100 req/min |
| Max payload | 1MB |
| p99 latency | <200ms |

## 7. Versioning and Compatibility

- API versioning scheme (URL path, header).
- Deprecation policy.
- Backward compatibility guarantees.

## 8. SDK Support

- Available SDKs and their locations.
- Example usage.

```java
// Java SDK example
CapsuleClient client = CapsuleClient.builder()
    .baseUrl("https://api.example.com")
    .apiKey(apiKey)
    .build();

Capsule capsule = client.getCapsule("capsule-123");
```

## 9. References

- [OpenAPI Specification](../openapi/)
- [API Catalog](../api/api-catalog.md)
- [SDK Documentation](../integration/)

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Catalog](../api/api-catalog.md) | Complete API reference |
| [Authentication Guide](../security/) | Security details |
| [Error Reference](../api/error-handling.md) | Error codes |

