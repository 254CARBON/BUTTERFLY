# BUTTERFLY Client Integration Guide

> Patterns and best practices for building BUTTERFLY integrations

**Last Updated**: 2025-12-03  
**Target Audience**: Integration developers

---

## Overview

This guide covers best practices for building robust integrations with BUTTERFLY APIs, including error handling, retry logic, connection management, and performance optimization.

---

## Client Architecture

### Recommended Client Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Recommended Client Architecture                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                        Your Application                             │    │
│  │                                                                      │    │
│  │  ┌────────────────────────────────────────────────────────────┐    │    │
│  │  │                    BUTTERFLY Client                         │    │    │
│  │  │                                                              │    │    │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌───────────────────┐  │    │    │
│  │  │  │ Auth Module │  │ Rate Limit  │  │  Circuit Breaker  │  │    │    │
│  │  │  │             │  │   Handler   │  │                   │  │    │    │
│  │  │  │ Token mgmt  │  │ Backoff/    │  │ Resilience4j /    │  │    │    │
│  │  │  │ Refresh     │  │ Throttle    │  │ Polly            │  │    │    │
│  │  │  └─────────────┘  └─────────────┘  └───────────────────┘  │    │    │
│  │  │                                                              │    │    │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌───────────────────┐  │    │    │
│  │  │  │   Retry     │  │   Cache     │  │   Connection      │  │    │    │
│  │  │  │   Logic     │  │   Layer     │  │   Pool            │  │    │    │
│  │  │  │             │  │             │  │                   │  │    │    │
│  │  │  │ Exp backoff │  │ Response    │  │ HTTP/2 / Keep-    │  │    │    │
│  │  │  │ Jitter      │  │ caching     │  │ alive             │  │    │    │
│  │  │  └─────────────┘  └─────────────┘  └───────────────────┘  │    │    │
│  │  │                                                              │    │    │
│  │  │  ┌─────────────────────────────────────────────────────┐  │    │    │
│  │  │  │              Service Clients                          │  │    │    │
│  │  │  │  CapsuleClient │ PerceptionClient │ PlatoClient │...│  │    │    │
│  │  │  └─────────────────────────────────────────────────────┘  │    │    │
│  │  │                                                              │    │    │
│  │  └────────────────────────────────────────────────────────────┘    │    │
│  │                                                                      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Authentication Management

### Token Lifecycle

```java
public class AuthenticationManager {
    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;
    private final Object lock = new Object();
    
    public String getAccessToken() {
        synchronized (lock) {
            if (isExpired()) {
                refreshTokens();
            }
            return accessToken;
        }
    }
    
    private boolean isExpired() {
        // Refresh 5 minutes before expiry
        return Instant.now().plus(Duration.ofMinutes(5)).isAfter(expiresAt);
    }
    
    private void refreshTokens() {
        // Call refresh endpoint
        TokenResponse response = authClient.refresh(refreshToken);
        this.accessToken = response.getAccessToken();
        this.refreshToken = response.getRefreshToken();
        this.expiresAt = Instant.now().plusSeconds(response.getExpiresIn());
    }
}
```

### API Key Management

```python
import os
from functools import cached_property

class ButterflyClient:
    def __init__(self):
        self._api_key = os.environ.get("BUTTERFLY_API_KEY")
        if not self._api_key:
            raise ValueError("BUTTERFLY_API_KEY environment variable not set")
    
    @property
    def headers(self):
        return {
            "X-API-Key": self._api_key,
            "Content-Type": "application/json",
            "Accept": "application/json"
        }
```

---

## Error Handling

### Comprehensive Error Handling

```python
import requests
from enum import Enum
from dataclasses import dataclass

class ButterflyErrorCode(Enum):
    VALIDATION_ERROR = "VALIDATION_ERROR"
    UNAUTHORIZED = "UNAUTHORIZED"
    FORBIDDEN = "FORBIDDEN"
    NOT_FOUND = "NOT_FOUND"
    RATE_LIMITED = "RATE_LIMITED"
    INTERNAL_ERROR = "INTERNAL_ERROR"

@dataclass
class ButterflyError(Exception):
    code: str
    message: str
    details: dict = None
    request_id: str = None

class ButterflyClient:
    def _handle_response(self, response: requests.Response):
        if response.ok:
            return response.json()
        
        try:
            error_data = response.json()
            error = error_data.get("error", {})
            raise ButterflyError(
                code=error.get("code", "UNKNOWN"),
                message=error.get("message", "Unknown error"),
                details=error.get("details"),
                request_id=error_data.get("meta", {}).get("requestId")
            )
        except ValueError:
            raise ButterflyError(
                code="PARSE_ERROR",
                message=f"HTTP {response.status_code}: {response.text}"
            )
    
    def get_capsule(self, capsule_id: str):
        try:
            response = self.session.get(f"{self.base_url}/api/v1/capsules/{capsule_id}")
            return self._handle_response(response)
        except ButterflyError as e:
            if e.code == "NOT_FOUND":
                return None  # Handle gracefully
            elif e.code == "RATE_LIMITED":
                # Handle rate limiting
                raise
            else:
                raise
```

---

## Retry Logic

### Exponential Backoff with Jitter

```java
public class RetryPolicy {
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 30000;
    private static final Random random = new Random();
    
    public <T> T executeWithRetry(Supplier<T> operation) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (RetryableException e) {
                lastException = e;
                
                if (attempt < MAX_RETRIES - 1) {
                    long delay = calculateDelay(attempt);
                    Thread.sleep(delay);
                }
            } catch (NonRetryableException e) {
                throw e;  // Don't retry
            }
        }
        
        throw lastException;
    }
    
    private long calculateDelay(int attempt) {
        // Exponential backoff: base * 2^attempt
        long exponentialDelay = BASE_DELAY_MS * (1L << attempt);
        
        // Cap at max delay
        long cappedDelay = Math.min(exponentialDelay, MAX_DELAY_MS);
        
        // Add jitter (0-100% of delay)
        long jitter = (long) (random.nextDouble() * cappedDelay);
        
        return cappedDelay + jitter;
    }
    
    private boolean isRetryable(Exception e) {
        if (e instanceof HttpException) {
            int status = ((HttpException) e).getStatusCode();
            return status == 429 || status >= 500;
        }
        return e instanceof IOException;
    }
}
```

### TypeScript Implementation

```typescript
async function executeWithRetry<T>(
  operation: () => Promise<T>,
  options: { maxRetries?: number; baseDelayMs?: number } = {}
): Promise<T> {
  const { maxRetries = 3, baseDelayMs = 1000 } = options;
  
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      return await operation();
    } catch (error) {
      if (!isRetryable(error) || attempt === maxRetries - 1) {
        throw error;
      }
      
      const delay = calculateDelay(attempt, baseDelayMs);
      await sleep(delay);
    }
  }
  
  throw new Error("Unreachable");
}

function calculateDelay(attempt: number, baseDelayMs: number): number {
  const exponentialDelay = baseDelayMs * Math.pow(2, attempt);
  const maxDelay = 30000;
  const cappedDelay = Math.min(exponentialDelay, maxDelay);
  const jitter = Math.random() * cappedDelay;
  return cappedDelay + jitter;
}

function isRetryable(error: any): boolean {
  if (error.status === 429) return true;
  if (error.status >= 500) return true;
  return false;
}
```

---

## Connection Management

### HTTP Connection Pooling

```java
// Java with OkHttp
OkHttpClient client = new OkHttpClient.Builder()
    .connectionPool(new ConnectionPool(
        10,  // Max idle connections
        5,   // Keep alive duration
        TimeUnit.MINUTES
    ))
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build();
```

```python
# Python with requests Session
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

session = requests.Session()

# Configure retry strategy
retry_strategy = Retry(
    total=3,
    backoff_factor=1,
    status_forcelist=[429, 500, 502, 503, 504]
)

# Configure connection pooling
adapter = HTTPAdapter(
    max_retries=retry_strategy,
    pool_connections=10,
    pool_maxsize=10
)

session.mount("http://", adapter)
session.mount("https://", adapter)
```

---

## Caching

### Response Caching

```python
from functools import lru_cache
from datetime import datetime, timedelta
import hashlib

class CachedClient:
    def __init__(self, client, ttl_seconds=60):
        self.client = client
        self.cache = {}
        self.ttl = timedelta(seconds=ttl_seconds)
    
    def get_capsule(self, capsule_id: str):
        cache_key = f"capsule:{capsule_id}"
        
        # Check cache
        if cache_key in self.cache:
            entry = self.cache[cache_key]
            if datetime.now() < entry["expires_at"]:
                return entry["data"]
        
        # Fetch from API
        data = self.client.get_capsule(capsule_id)
        
        # Cache response
        self.cache[cache_key] = {
            "data": data,
            "expires_at": datetime.now() + self.ttl
        }
        
        return data
    
    def invalidate(self, pattern: str):
        keys_to_remove = [k for k in self.cache if pattern in k]
        for key in keys_to_remove:
            del self.cache[key]
```

---

## Circuit Breaker

### Resilience Pattern

```java
// Using Resilience4j
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .failureRateThreshold(50)
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .slidingWindowSize(10)
    .permittedNumberOfCallsInHalfOpenState(5)
    .build();

CircuitBreaker circuitBreaker = CircuitBreaker.of("capsule-client", config);

public Capsule getCapsule(String id) {
    return circuitBreaker.executeSupplier(() -> {
        return client.get("/api/v1/capsules/" + id);
    });
}
```

---

## Observability

### Request Logging and Metrics

```python
import logging
import time
from functools import wraps

logger = logging.getLogger(__name__)

def log_request(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        start_time = time.time()
        request_id = generate_request_id()
        
        try:
            result = func(*args, **kwargs, request_id=request_id)
            duration = time.time() - start_time
            
            logger.info(
                "API call succeeded",
                extra={
                    "request_id": request_id,
                    "method": func.__name__,
                    "duration_ms": duration * 1000,
                    "status": "success"
                }
            )
            
            return result
            
        except Exception as e:
            duration = time.time() - start_time
            
            logger.error(
                "API call failed",
                extra={
                    "request_id": request_id,
                    "method": func.__name__,
                    "duration_ms": duration * 1000,
                    "status": "error",
                    "error": str(e)
                }
            )
            
            raise
    
    return wrapper
```

---

## Complete Client Example

### Java Client

```java
public class ButterflyClient {
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AuthManager authManager;
    private final CircuitBreaker circuitBreaker;
    private final String baseUrl;
    
    public ButterflyClient(ButterflyClientConfig config) {
        this.baseUrl = config.getBaseUrl();
        this.objectMapper = new ObjectMapper();
        this.authManager = new AuthManager(config);
        
        this.httpClient = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .addInterceptor(new AuthInterceptor(authManager))
            .addInterceptor(new RetryInterceptor())
            .addInterceptor(new LoggingInterceptor())
            .build();
        
        this.circuitBreaker = CircuitBreaker.of("butterfly", 
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .build());
    }
    
    public Capsule getCapsule(String id) {
        return circuitBreaker.executeSupplier(() -> {
            Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/capsules/" + id)
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new ButterflyException(response);
                }
                return objectMapper.readValue(
                    response.body().string(), 
                    Capsule.class
                );
            }
        });
    }
}
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Overview](../api/README.md) | API documentation |
| [SDKs](sdks/) | Language-specific clients |
| [Error Handling](../api/error-handling.md) | Error codes |
| [Rate Limiting](../api/rate-limiting.md) | Rate limits |

