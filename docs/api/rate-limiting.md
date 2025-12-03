# BUTTERFLY API Rate Limiting

> Rate limit policies and best practices

**Last Updated**: 2025-12-03  
**Target Audience**: API consumers, developers

---

## Overview

BUTTERFLY implements rate limiting to ensure fair usage, protect service stability, and maintain performance for all users.

---

## Rate Limit Tiers

### Standard Limits by Endpoint Type

| Endpoint Type | Limit | Window | Description |
|---------------|-------|--------|-------------|
| **Query APIs** | 1000 requests | 1 minute | Read operations |
| **Command APIs** | 100 requests | 1 minute | Write operations |
| **Admin APIs** | 100 requests | 1 minute | Administrative operations |
| **WebSocket** | 10 connections | Per client | Concurrent connections |

### Per-Service Defaults

| Service | Query | Command | Admin |
|---------|-------|---------|-------|
| PERCEPTION | 1000/min | 100/min | 100/min |
| CAPSULE | 1000/min | 100/min | 100/min |
| ODYSSEY | 500/min | 50/min | 100/min |
| PLATO | 500/min | 100/min | 100/min |
| NEXUS | 500/min | 50/min | 100/min |

---

## Rate Limit Headers

### Response Headers

Every API response includes rate limit information:

```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 950
X-RateLimit-Reset: 1701609660
```

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Maximum requests allowed in window |
| `X-RateLimit-Remaining` | Requests remaining in current window |
| `X-RateLimit-Reset` | Unix timestamp when window resets |

### Rate Limited Response

When limits are exceeded:

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
    "requestId": "req-abc123",
    "timestamp": "2024-12-03T10:00:00Z"
  }
}
```

---

## Client Implementation

### Proactive Rate Limit Handling

```java
// Java example
public class RateLimitAwareClient {
    private final AtomicInteger remaining = new AtomicInteger(1000);
    private final AtomicLong resetTime = new AtomicLong(0);
    
    public <T> T execute(Supplier<T> request) {
        // Check if we should wait
        if (remaining.get() <= 0) {
            long waitTime = resetTime.get() - System.currentTimeMillis();
            if (waitTime > 0) {
                sleep(waitTime);
            }
        }
        
        try {
            Response<T> response = request.get();
            
            // Update limits from headers
            remaining.set(response.getHeader("X-RateLimit-Remaining"));
            resetTime.set(response.getHeader("X-RateLimit-Reset") * 1000);
            
            return response.body();
        } catch (RateLimitException e) {
            // Wait and retry
            int retryAfter = e.getRetryAfter();
            sleep(retryAfter * 1000);
            return execute(request);
        }
    }
}
```

### Python Implementation

```python
import time
import requests

class RateLimitedClient:
    def __init__(self, base_url):
        self.base_url = base_url
        self.remaining = 1000
        self.reset_time = 0
    
    def request(self, method, path, **kwargs):
        # Wait if necessary
        if self.remaining <= 0:
            wait_time = self.reset_time - time.time()
            if wait_time > 0:
                time.sleep(wait_time)
        
        response = requests.request(method, f"{self.base_url}{path}", **kwargs)
        
        # Update limits
        self.remaining = int(response.headers.get("X-RateLimit-Remaining", 1000))
        self.reset_time = int(response.headers.get("X-RateLimit-Reset", 0))
        
        if response.status_code == 429:
            retry_after = int(response.headers.get("Retry-After", 60))
            time.sleep(retry_after)
            return self.request(method, path, **kwargs)
        
        return response
```

### JavaScript/TypeScript Implementation

```typescript
class RateLimitedClient {
  private remaining: number = 1000;
  private resetTime: number = 0;
  
  async request(url: string, options?: RequestInit): Promise<Response> {
    // Wait if necessary
    if (this.remaining <= 0) {
      const waitTime = this.resetTime - Date.now();
      if (waitTime > 0) {
        await new Promise(resolve => setTimeout(resolve, waitTime));
      }
    }
    
    const response = await fetch(url, options);
    
    // Update limits
    this.remaining = parseInt(response.headers.get('X-RateLimit-Remaining') || '1000');
    this.resetTime = parseInt(response.headers.get('X-RateLimit-Reset') || '0') * 1000;
    
    if (response.status === 429) {
      const retryAfter = parseInt(response.headers.get('Retry-After') || '60');
      await new Promise(resolve => setTimeout(resolve, retryAfter * 1000));
      return this.request(url, options);
    }
    
    return response;
  }
}
```

---

## Retry Strategy

### Exponential Backoff

For 429 responses, use exponential backoff with jitter:

```
wait_time = min(base_delay * (2 ^ attempt) + random(0, 1), max_delay)

Where:
  base_delay = 1 second
  max_delay = 60 seconds
  attempt = retry attempt number (0, 1, 2, ...)
```

### Example Implementation

```python
import random
import time

def retry_with_backoff(func, max_retries=5, base_delay=1, max_delay=60):
    for attempt in range(max_retries):
        try:
            return func()
        except RateLimitError as e:
            if attempt == max_retries - 1:
                raise
            
            # Use Retry-After header if available
            delay = e.retry_after if e.retry_after else (
                min(base_delay * (2 ** attempt), max_delay) + random.uniform(0, 1)
            )
            
            time.sleep(delay)
```

---

## Best Practices

### 1. Monitor Rate Limit Headers

Always check `X-RateLimit-Remaining` before making requests:

```python
if int(response.headers.get("X-RateLimit-Remaining", 1000)) < 10:
    # Slow down or queue requests
    pass
```

### 2. Use Batch APIs

For bulk operations, use batch endpoints:

```http
POST /api/v1/capsules/batch
Content-Type: application/json

{
  "capsules": [
    {...},
    {...},
    {...}
  ]
}
```

### 3. Implement Request Queuing

For high-volume applications:

```python
from queue import Queue
from threading import Thread
import time

class RequestQueue:
    def __init__(self, rate_limit=100, window=60):
        self.queue = Queue()
        self.rate_limit = rate_limit
        self.window = window
        self.tokens = rate_limit
        self.last_refill = time.time()
    
    def enqueue(self, request):
        self.queue.put(request)
    
    def process(self):
        while True:
            # Refill tokens
            now = time.time()
            if now - self.last_refill > self.window:
                self.tokens = self.rate_limit
                self.last_refill = now
            
            if self.tokens > 0 and not self.queue.empty():
                request = self.queue.get()
                self.tokens -= 1
                request.execute()
            else:
                time.sleep(0.1)
```

### 4. Cache Responses

Reduce API calls by caching responses:

```python
from functools import lru_cache

@lru_cache(maxsize=1000, ttl=60)
def get_capsule(capsule_id):
    return client.get(f"/api/v1/capsules/{capsule_id}")
```

### 5. Use Webhooks for Real-Time Data

Instead of polling, subscribe to webhooks:

```http
POST /api/v1/webhooks
Content-Type: application/json

{
  "url": "https://your-app.com/webhook",
  "events": ["capsule.created", "event.detected"]
}
```

---

## Enterprise Rate Limits

For higher limits, contact us about enterprise plans:

| Plan | Query | Command | Support |
|------|-------|---------|---------|
| Standard | 1000/min | 100/min | Community |
| Professional | 5000/min | 500/min | Email |
| Enterprise | Custom | Custom | Dedicated |

Contact: enterprise@254studioz.com

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Overview](README.md) | API section overview |
| [Authentication](authentication.md) | Auth patterns |
| [Error Handling](error-handling.md) | Error codes |

