# BUTTERFLY API Error Handling

> Standardized error codes and response formats

**Last Updated**: 2025-12-03  
**Target Audience**: API consumers, developers

---

## Overview

BUTTERFLY APIs use a consistent error response format across all services, making it easier to handle errors programmatically.

---

## Error Response Format

All error responses follow this structure:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
      // Additional context (optional)
    }
  },
  "meta": {
    "requestId": "req-abc123",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `error.code` | string | Machine-readable error code |
| `error.message` | string | Human-readable description |
| `error.details` | object | Additional error context |
| `meta.requestId` | string | Unique request identifier |
| `meta.timestamp` | string | ISO 8601 timestamp |

---

## HTTP Status Codes

### Client Errors (4xx)

| Status | Code | Description |
|--------|------|-------------|
| 400 | `VALIDATION_ERROR` | Invalid request parameters |
| 400 | `MALFORMED_REQUEST` | Request body parsing failed |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |
| 401 | `INVALID_TOKEN` | Token validation failed |
| 401 | `EXPIRED_TOKEN` | Token has expired |
| 403 | `FORBIDDEN` | Insufficient permissions |
| 403 | `ACCESS_DENIED` | Resource access denied |
| 404 | `NOT_FOUND` | Resource not found |
| 405 | `METHOD_NOT_ALLOWED` | HTTP method not supported |
| 409 | `CONFLICT` | Resource conflict (duplicate) |
| 409 | `VERSION_CONFLICT` | Optimistic locking failure |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type not supported |
| 422 | `UNPROCESSABLE_ENTITY` | Semantic validation failed |
| 429 | `RATE_LIMITED` | Too many requests |

### Server Errors (5xx)

| Status | Code | Description |
|--------|------|-------------|
| 500 | `INTERNAL_ERROR` | Unexpected server error |
| 502 | `BAD_GATEWAY` | Upstream service error |
| 503 | `SERVICE_UNAVAILABLE` | Service temporarily unavailable |
| 504 | `GATEWAY_TIMEOUT` | Upstream timeout |

---

## Error Examples

### Validation Error

```http
POST /api/v1/capsules
Content-Type: application/json

{
  "scopeId": "invalid-id",
  "timestamp": "not-a-date"
}
```

**Response:**

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {
      "violations": [
        {
          "field": "scopeId",
          "value": "invalid-id",
          "message": "must match pattern rim:{nodeType}:{namespace}:{localId}"
        },
        {
          "field": "timestamp",
          "value": "not-a-date",
          "message": "must be a valid ISO 8601 timestamp"
        }
      ]
    }
  },
  "meta": {
    "requestId": "req-abc123",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Authentication Error

```http
GET /api/v1/capsules
Authorization: Bearer expired-token
```

**Response:**

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json
WWW-Authenticate: Bearer error="invalid_token", error_description="Token has expired"

{
  "error": {
    "code": "EXPIRED_TOKEN",
    "message": "Authentication token has expired",
    "details": {
      "expired_at": "2024-12-03T09:00:00Z"
    }
  },
  "meta": {
    "requestId": "req-def456",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Permission Error

```http
POST /api/v1/specs
Authorization: Bearer valid-token-without-write-scope
```

**Response:**

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "error": {
    "code": "FORBIDDEN",
    "message": "Insufficient permissions for this operation",
    "details": {
      "required_scopes": ["write:specs"],
      "provided_scopes": ["read:specs"]
    }
  },
  "meta": {
    "requestId": "req-ghi789",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Not Found Error

```http
GET /api/v1/capsules/cap_nonexistent
```

**Response:**

```http
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "error": {
    "code": "NOT_FOUND",
    "message": "Capsule not found",
    "details": {
      "resource_type": "Capsule",
      "resource_id": "cap_nonexistent"
    }
  },
  "meta": {
    "requestId": "req-jkl012",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Conflict Error

```http
POST /api/v1/capsules
Content-Type: application/json

{
  "scopeId": "rim:entity:finance:EURUSD",
  "timestamp": "2024-12-03T10:00:00Z",
  "resolution": 60,
  "vantageMode": "omniscient"
}
```

**Response (if duplicate exists):**

```http
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "error": {
    "code": "CONFLICT",
    "message": "A CAPSULE with this idempotency key already exists",
    "details": {
      "existing_id": "cap_abc123",
      "idempotency_key": "sha256:a1b2c3..."
    }
  },
  "meta": {
    "requestId": "req-mno345",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Rate Limit Error

```http
GET /api/v1/capsules
```

**Response:**

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 60
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1701609660

{
  "error": {
    "code": "RATE_LIMITED",
    "message": "Rate limit exceeded",
    "details": {
      "limit": 1000,
      "window": "1m",
      "retry_after": 60
    }
  },
  "meta": {
    "requestId": "req-pqr678",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

### Internal Error

```http
GET /api/v1/capsules
```

**Response:**

```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "An unexpected error occurred",
    "details": {
      "trace_id": "trace-abc123"
    }
  },
  "meta": {
    "requestId": "req-stu901",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

---

## Error Handling Best Practices

### Client-Side Handling

```java
// Java example with exception handling
try {
    Capsule capsule = capsuleClient.get("cap_123").block();
} catch (WebClientResponseException e) {
    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        // Handle not found
        log.info("Capsule not found");
    } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        // Handle auth error - refresh token
        refreshToken();
        retry();
    } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        // Handle rate limiting - backoff
        int retryAfter = parseRetryAfter(e);
        Thread.sleep(retryAfter * 1000);
        retry();
    } else {
        // Log and rethrow
        log.error("API error: {}", e.getResponseBodyAsString());
        throw e;
    }
}
```

```python
# Python example
import requests
from requests.exceptions import HTTPError

try:
    response = requests.get(
        "http://localhost:8081/api/v1/capsules/cap_123",
        headers={"Authorization": f"Bearer {token}"}
    )
    response.raise_for_status()
    capsule = response.json()
except HTTPError as e:
    error = e.response.json().get("error", {})
    if e.response.status_code == 404:
        print(f"Not found: {error.get('message')}")
    elif e.response.status_code == 429:
        retry_after = int(e.response.headers.get("Retry-After", 60))
        time.sleep(retry_after)
        # retry
    else:
        print(f"Error: {error.get('code')} - {error.get('message')}")
```

### Retry Strategy

For transient errors (5xx, 429), implement exponential backoff:

```python
import time
import random

def retry_with_backoff(func, max_retries=3, base_delay=1):
    for attempt in range(max_retries):
        try:
            return func()
        except Exception as e:
            if attempt == max_retries - 1:
                raise
            delay = base_delay * (2 ** attempt) + random.uniform(0, 1)
            time.sleep(delay)
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Overview](README.md) | API section overview |
| [Authentication](authentication.md) | Auth patterns |
| [Rate Limiting](rate-limiting.md) | Rate limits |

