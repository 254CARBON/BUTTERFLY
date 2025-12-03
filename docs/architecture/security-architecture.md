# BUTTERFLY Security Architecture

> End-to-end security design for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: Security engineers, architects, compliance teams

---

## Overview

BUTTERFLY implements a defense-in-depth security architecture with multiple layers of protection. This document provides a comprehensive overview of security controls, authentication, authorization, and compliance alignment.

---

## Security Layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Defense in Depth Architecture                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Layer 1: Network Security                                           │   │
│  │  • TLS 1.2+ for all external traffic                                │   │
│  │  • mTLS for service-to-service communication                        │   │
│  │  • Network policies restricting inter-service traffic               │   │
│  │  • WAF and DDoS protection at edge                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Layer 2: Authentication                                             │   │
│  │  • JWT tokens for API access                                        │   │
│  │  • API keys for service accounts                                    │   │
│  │  • OAuth 2.0 / OIDC for user authentication                        │   │
│  │  • Certificate-based auth for internal services                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Layer 3: Authorization                                              │   │
│  │  • Role-Based Access Control (RBAC)                                 │   │
│  │  • Scoped permissions per endpoint                                  │   │
│  │  • Resource-level access control                                    │   │
│  │  • Namespace/tenant isolation                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Layer 4: Application Security                                       │   │
│  │  • Input validation and sanitization                                │   │
│  │  • Rate limiting                                                    │   │
│  │  • Security headers                                                 │   │
│  │  • CORS configuration                                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Layer 5: Data Security                                              │   │
│  │  • Encryption at rest (AES-256)                                     │   │
│  │  • Encryption in transit (TLS)                                      │   │
│  │  • Key management (Vault integration)                               │   │
│  │  • Data classification and handling                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Layer 6: Audit and Monitoring                                       │   │
│  │  • Security event logging                                           │   │
│  │  • Authentication/authorization audit                               │   │
│  │  • Anomaly detection                                                │   │
│  │  • Compliance reporting                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Authentication

### JWT Token Authentication

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         JWT Authentication Flow                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Client                      Auth Service              API Service         │
│     │                              │                         │              │
│     │  1. Authenticate             │                         │              │
│     │  (credentials/OAuth)         │                         │              │
│     │─────────────────────────────▶│                         │              │
│     │                              │                         │              │
│     │  2. JWT Token                │                         │              │
│     │◀─────────────────────────────│                         │              │
│     │                              │                         │              │
│     │  3. API Request              │                         │              │
│     │  Authorization: Bearer <jwt> │                         │              │
│     │───────────────────────────────────────────────────────▶│              │
│     │                              │                         │              │
│     │                              │  4. Validate JWT        │              │
│     │                              │     (signature, expiry, │              │
│     │                              │      claims)            │              │
│     │                              │                         │              │
│     │  5. Response                 │                         │              │
│     │◀───────────────────────────────────────────────────────│              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### JWT Token Structure

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user-123",
    "iss": "butterfly-auth",
    "aud": ["butterfly-api"],
    "exp": 1701610800,
    "iat": 1701607200,
    "scopes": ["read:capsules", "write:specs"],
    "roles": ["analyst", "developer"],
    "tenant": "org-abc"
  }
}
```

### Token Configuration

| Property | Value | Description |
|----------|-------|-------------|
| Algorithm | RS256 | RSA signature with SHA-256 |
| Access Token TTL | 1 hour | Short-lived for security |
| Refresh Token TTL | 24 hours | Longer for UX |
| Key Rotation | 30 days | Regular key rotation |

### API Key Authentication

For service-to-service and automation:

```http
GET /api/v1/capsules
X-API-Key: butterfly_live_abc123xyz789...
```

**API Key Structure:**
- Prefix: `butterfly_{env}_` (live, test, dev)
- Random: 32-character cryptographically random string
- Checksum: 4-character checksum

---

## Authorization

### Role-Based Access Control (RBAC)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              RBAC Model                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  User                                                                       │
│    │                                                                        │
│    └──▶ Roles                                                               │
│           │                                                                  │
│           ├──▶ Admin                                                        │
│           │      └── Permissions: [*]                                       │
│           │                                                                  │
│           ├──▶ Developer                                                    │
│           │      └── Permissions: [read:*, write:specs, write:plans,       │
│           │                        execute:plans]                           │
│           │                                                                  │
│           ├──▶ Analyst                                                      │
│           │      └── Permissions: [read:capsules, read:events,             │
│           │                        read:scenarios]                          │
│           │                                                                  │
│           ├──▶ Operator                                                     │
│           │      └── Permissions: [read:*, admin:monitoring,               │
│           │                        admin:health]                            │
│           │                                                                  │
│           └──▶ Service                                                      │
│                  └── Permissions: [service-specific scopes]                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Permission Scopes

| Scope | Description |
|-------|-------------|
| `read:capsules` | Read CAPSULE history |
| `write:capsules` | Create CAPSULEs |
| `read:specs` | Read specifications |
| `write:specs` | Create/update specifications |
| `execute:plans` | Execute plans |
| `admin:governance` | Manage policies |
| `admin:users` | User management |
| `admin:monitoring` | Access monitoring |

### Spring Security Implementation

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health").permitAll()
                .pathMatchers("/api/v1/specs/**").hasAnyAuthority("SCOPE_read:specs", "SCOPE_write:specs")
                .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

---

## Network Security

### TLS Configuration

```yaml
# Minimum TLS version
server:
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.2,TLSv1.3
    ciphers:
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
      - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
```

### Security Headers

All API responses include:

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
Referrer-Policy: strict-origin-when-cross-origin
```

### CORS Configuration

```yaml
security:
  cors:
    allowed-origins:
      - https://app.254studioz.com
      - https://dashboard.254studioz.com
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    allowed-headers: Authorization,Content-Type,X-Request-ID
    exposed-headers: X-Request-ID,X-RateLimit-Remaining
    max-age: 3600
```

---

## Application Security

### Input Validation

```java
@PostMapping("/capsules")
public Mono<Capsule> createCapsule(@Valid @RequestBody CapsuleRequest request) {
    // Bean validation enforced
    return capsuleService.create(request);
}

public class CapsuleRequest {
    @NotNull
    @Pattern(regexp = "^rim:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-zA-Z0-9_-]+$")
    private String scopeId;
    
    @NotNull
    @PastOrPresent
    private Instant timestamp;
    
    @Min(1)
    @Max(86400)
    private int resolution;
}
```

### Rate Limiting

| Endpoint Type | Limit | Window |
|---------------|-------|--------|
| Query APIs | 1000 requests | 1 minute |
| Command APIs | 100 requests | 1 minute |
| Admin APIs | 100 requests | 1 minute |
| WebSocket | 10 connections | per client |

### SQL/NoSQL Injection Prevention

- Parameterized queries only
- JPA/Spring Data for type-safe queries
- Gremlin parameterized traversals for graph queries

---

## Data Security

### Encryption

| Data State | Encryption | Algorithm |
|------------|------------|-----------|
| At rest (databases) | Yes | AES-256 |
| At rest (object storage) | Yes | AES-256-GCM |
| In transit | Yes | TLS 1.2+ |
| Kafka messages | Optional | Topic-level encryption |

### Secrets Management

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Secrets Management                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Development                Production                                      │
│  ───────────────           ────────────────────                            │
│                                                                              │
│  ┌────────────────┐        ┌────────────────────────────────────────┐     │
│  │  .env files    │        │           HashiCorp Vault               │     │
│  │  (gitignored)  │        │                                         │     │
│  └────────────────┘        │  ┌─────────────────────────────────┐  │     │
│                             │  │  Secret Engines                   │  │     │
│                             │  │  • KV (static secrets)           │  │     │
│                             │  │  • Database (dynamic credentials)│  │     │
│                             │  │  • PKI (certificates)            │  │     │
│                             │  └─────────────────────────────────┘  │     │
│                             │                                         │     │
│                             │  ┌─────────────────────────────────┐  │     │
│                             │  │  Authentication                   │  │     │
│                             │  │  • Kubernetes auth                │  │     │
│                             │  │  • AppRole                        │  │     │
│                             │  └─────────────────────────────────┘  │     │
│                             └────────────────────────────────────────┘     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Audit and Compliance

### Security Event Logging

All security-relevant events are logged:

```json
{
  "timestamp": "2024-12-03T10:00:00Z",
  "eventType": "AUTH_SUCCESS",
  "principal": "user-123",
  "clientIp": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "resource": "/api/v1/specs",
  "action": "CREATE",
  "outcome": "SUCCESS",
  "requestId": "req-abc123",
  "details": {
    "specId": "spec_vol_alert_001"
  }
}
```

### Event Types

| Event Type | Description |
|------------|-------------|
| `AUTH_SUCCESS` | Successful authentication |
| `AUTH_FAILURE` | Failed authentication |
| `AUTHZ_DENIED` | Authorization denied |
| `RATE_LIMITED` | Rate limit exceeded |
| `RESOURCE_ACCESS` | Resource accessed |
| `ADMIN_ACTION` | Administrative action |

### SOC2 Alignment

See [Security Baseline](../SECURITY_BASELINE.md) for complete SOC2 Trust Service Criteria mapping.

| Control Area | BUTTERFLY Implementation |
|--------------|--------------------------|
| CC6.1 Logical Access | JWT + RBAC |
| CC6.6 Access Protection | TLS, security headers |
| CC7.4 Event Detection | Security logging, alerting |
| CC8.1 Change Authorization | PR approval, CI gates |

---

## Vulnerability Management

### Scanning Schedule

| Scan Type | Tool | Frequency | Blocking |
|-----------|------|-----------|----------|
| SAST | CodeQL, Semgrep | Every PR | Yes |
| Dependencies | OWASP Check | Every PR | Yes (CVSS >= 7) |
| Secrets | TruffleHog, Gitleaks | Every PR | Yes |
| Container | Trivy | Pre-release | Planned |

### Response SLAs

| Severity | CVSS | Response | Resolution |
|----------|------|----------|------------|
| Critical | 9.0-10.0 | 4 hours | 24 hours |
| High | 7.0-8.9 | 24 hours | 7 days |
| Medium | 4.0-6.9 | 7 days | 30 days |
| Low | 0.1-3.9 | 30 days | 90 days |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Security Baseline](../SECURITY_BASELINE.md) | Controls inventory and SOC2 mapping |
| [Threat Model](../THREAT_MODEL.md) | Threat analysis |
| [Secrets Management](../SECRETS_MANAGEMENT.md) | Secrets handling |
| [Hardening Guide](../security/hardening.md) | Production hardening |
| [API Authentication](../api/authentication.md) | Auth patterns |

