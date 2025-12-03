# BUTTERFLY Security Model

> Authentication, authorization, and data protection implementation

**Last Updated**: 2025-12-03  
**Target Audience**: Security engineers, developers

---

## Authentication

### JWT Authentication

BUTTERFLY uses JSON Web Tokens for user session authentication.

#### Token Structure

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "key-2024-01"
  },
  "payload": {
    "sub": "user-123",
    "iss": "butterfly-auth",
    "aud": "butterfly-api",
    "iat": 1701590400,
    "exp": 1701594000,
    "roles": ["analyst", "viewer"],
    "tenant_id": "tenant-abc",
    "permissions": ["capsule:read", "capsule:write"]
  }
}
```

#### Token Lifecycle

| Phase | Duration | Action |
|-------|----------|--------|
| Access Token | 1 hour | API access |
| Refresh Token | 7 days | Token renewal |
| Session | 24 hours | Max session length |

#### Validation

```java
// JWT validation in NEXUS
@Component
public class JwtValidator {
    
    public Claims validate(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(publicKey)
            .requireIssuer("butterfly-auth")
            .requireAudience("butterfly-api")
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}
```

### API Key Authentication

For service-to-service and integration scenarios.

#### Key Format

```
bf_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxx
bf_test_xxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

- Prefix: `bf_live_` (production) or `bf_test_` (test)
- Body: 32 random alphanumeric characters
- Total: 40 characters

#### Key Storage

- Keys are hashed using Argon2id before storage
- Only the hash is stored, never the plain key
- Salt is unique per key

```java
String hash = Argon2Factory.create()
    .hash(10, 65536, 1, apiKey.toCharArray());
```

#### Key Scopes

| Scope | Description |
|-------|-------------|
| `read` | Read-only access |
| `write` | Read and write access |
| `admin` | Full administrative access |
| `capsule:*` | CAPSULE service access |
| `odyssey:*` | ODYSSEY service access |
| `perception:*` | PERCEPTION service access |
| `plato:*` | PLATO service access |

---

## Authorization

### Role-Based Access Control (RBAC)

#### Default Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| `admin` | Full system access | All |
| `analyst` | Create and analyze | Read, write, execute |
| `viewer` | Read-only access | Read |
| `operator` | Operations access | Read, execute, manage |

#### Permission Model

```
<service>:<resource>:<action>

Examples:
- capsule:capsules:read
- capsule:capsules:write
- odyssey:graphs:query
- perception:events:submit
- plato:plans:execute
```

#### Authorization Check

```java
@PreAuthorize("hasPermission(#scopeId, 'capsule:read')")
public Capsule getCapsule(String scopeId) {
    // ...
}
```

### Multi-Tenancy

#### Tenant Isolation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Tenant Isolation Model                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Request → JWT Extraction → Tenant ID → Query Filter                        │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ Tenant A Data                                                           ││
│  │ ┌───────────┐  ┌───────────┐  ┌───────────┐                            ││
│  │ │ Capsules  │  │  Graphs   │  │  Events   │                            ││
│  │ │ tenant=A  │  │ tenant=A  │  │ tenant=A  │                            ││
│  │ └───────────┘  └───────────┘  └───────────┘                            ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ Tenant B Data                                                           ││
│  │ ┌───────────┐  ┌───────────┐  ┌───────────┐                            ││
│  │ │ Capsules  │  │  Graphs   │  │  Events   │                            ││
│  │ │ tenant=B  │  │ tenant=B  │  │ tenant=B  │                            ││
│  │ └───────────┘  └───────────┘  └───────────┘                            ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Implementation

```java
@Component
public class TenantContextFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain chain) {
        String tenantId = extractTenantFromJwt(request);
        TenantContext.setCurrentTenant(tenantId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

// Repository automatically filters by tenant
@Repository
public interface CapsuleRepository {
    
    @Query("SELECT c FROM Capsule c WHERE c.tenantId = :#{@tenantContext.current}")
    List<Capsule> findAll();
}
```

---

## Data Protection

### Encryption at Rest

| Component | Encryption | Key Management |
|-----------|------------|----------------|
| Cassandra | AES-256 | AWS KMS / HashiCorp Vault |
| PostgreSQL | AES-256 | Transparent Data Encryption |
| Redis | AES-256 | Redis Enterprise / manual |
| ClickHouse | AES-256 | Native encryption |
| Kafka | AES-256 | Per-topic encryption |

### Encryption in Transit

- **External**: TLS 1.3 required, TLS 1.2 minimum
- **Internal**: mTLS between all services
- **Databases**: SSL/TLS connections required

#### TLS Configuration

```yaml
# Minimum TLS configuration
server:
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.3,TLSv1.2
    ciphers:
      - TLS_AES_256_GCM_SHA384
      - TLS_CHACHA20_POLY1305_SHA256
      - TLS_AES_128_GCM_SHA256
```

### Data Classification

| Classification | Description | Handling |
|----------------|-------------|----------|
| Public | Non-sensitive | No restrictions |
| Internal | Business data | Encrypted, access logged |
| Confidential | Sensitive data | Encrypted, need-to-know |
| Restricted | PII, credentials | Encrypted, strict access |

### Data Retention

| Data Type | Retention | Deletion |
|-----------|-----------|----------|
| Capsules | Configurable (default 1 year) | Soft delete, then purge |
| Events | 90 days | Automatic purge |
| Audit logs | 7 years | Immutable storage |
| User data | Account lifetime + 30 days | GDPR-compliant deletion |

---

## Input Validation

### Validation Rules

```java
@PostMapping("/api/v1/capsules")
public CapsuleResponse create(
    @Valid @RequestBody CapsuleRequest request) {
    // Validated automatically
}

public class CapsuleRequest {
    @NotNull
    @Pattern(regexp = "^rim:[a-z]+:[a-z]+:[a-zA-Z0-9_-]+$")
    private String scopeId;
    
    @NotNull
    @PastOrPresent
    private Instant timestamp;
    
    @Min(1)
    @Max(86400)
    private int resolution;
}
```

### SQL Injection Prevention

- Use parameterized queries exclusively
- JPA/Hibernate with named parameters
- No string concatenation for queries

### XSS Prevention

- Output encoding for all responses
- Content-Type headers set correctly
- CSP headers configured

---

## Secrets Management

### Secret Types

| Secret | Storage | Rotation |
|--------|---------|----------|
| JWT private key | Vault / KMS | Annually |
| Database credentials | Vault / K8s Secrets | Quarterly |
| API keys | Hashed in DB | User-controlled |
| TLS certificates | Cert-manager | Automatic (90 days) |

### Secret Access

```yaml
# Kubernetes external secrets
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: butterfly-secrets
spec:
  secretStoreRef:
    kind: ClusterSecretStore
    name: vault-backend
  target:
    name: butterfly-secrets
  data:
    - secretKey: jwt-private-key
      remoteRef:
        key: butterfly/production/jwt
        property: private-key
```

---

## Audit Logging

### Logged Events

| Event Type | Data Captured |
|------------|---------------|
| Authentication | User, IP, success/failure, timestamp |
| Authorization | User, resource, action, result |
| Data access | User, resource, operation, timestamp |
| Configuration change | User, change type, old/new values |
| Security events | Type, details, severity |

### Log Format

```json
{
  "timestamp": "2024-12-03T10:00:00.000Z",
  "event_type": "AUTHORIZATION",
  "user_id": "user-123",
  "tenant_id": "tenant-abc",
  "action": "capsule:read",
  "resource": "rim:entity:finance:EURUSD",
  "result": "ALLOWED",
  "client_ip": "192.168.1.100",
  "user_agent": "ButterflySDK/1.0",
  "trace_id": "abc123"
}
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Security Overview](README.md) | Security index |
| [Hardening Guide](hardening.md) | Production security |
| [API Authentication](../api/authentication.md) | Auth implementation |

