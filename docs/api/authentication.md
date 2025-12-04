# BUTTERFLY API Authentication

> Authentication patterns for BUTTERFLY APIs

**Last Updated**: 2025-12-03  
**Target Audience**: API consumers, developers, security engineers

---

## Overview

BUTTERFLY supports multiple authentication methods to accommodate different use cases:

1. **JWT Bearer Tokens** - User authentication
2. **API Keys** - Service and automation authentication
3. **OAuth 2.0 / OIDC** - Federated authentication

---

## JWT Bearer Token Authentication

### How It Works

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       JWT Authentication Flow                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Client                    Auth Service                 API Service        │
│     │                            │                           │              │
│     │  1. POST /auth/token       │                           │              │
│     │     {credentials}          │                           │              │
│     │───────────────────────────▶│                           │              │
│     │                            │                           │              │
│     │  2. 200 OK                 │                           │              │
│     │     {access_token, ...}    │                           │              │
│     │◀───────────────────────────│                           │              │
│     │                            │                           │              │
│     │  3. GET /api/v1/resource   │                           │              │
│     │     Authorization: Bearer {token}                      │              │
│     │────────────────────────────────────────────────────────▶│              │
│     │                            │                           │              │
│     │                            │  4. Validate JWT          │              │
│     │                            │     - Check signature     │              │
│     │                            │     - Check expiry        │              │
│     │                            │     - Extract claims      │              │
│     │                            │                           │              │
│     │  5. 200 OK                 │                           │              │
│     │     {data}                 │                           │              │
│     │◀────────────────────────────────────────────────────────│              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Obtaining a Token

```bash
curl -X POST http://localhost:8084/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "your-password"
  }'
```

**Response:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
  "scope": "read:capsules write:specs"
}
```

### Using the Token

```bash
curl http://localhost:8081/api/v1/capsules \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Token Structure

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

### Refreshing Tokens

```bash
curl -X POST http://localhost:8084/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
  }'
```

### Token Expiration

| Token Type | Default TTL | Configurable |
|------------|-------------|--------------|
| Access Token | 1 hour | Yes |
| Refresh Token | 24 hours | Yes |

---

## API Key Authentication

For service-to-service and automation scenarios.

### Obtaining an API Key

API keys are provisioned through the admin interface or API:

```bash
curl -X POST http://localhost:8084/api/v1/admin/api-keys \
  -H "Authorization: Bearer {admin-token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "data-pipeline",
    "scopes": ["read:capsules", "write:capsules"],
    "expiry": "2025-12-31T23:59:59Z"
  }'
```

**Response:**

```json
{
  "id": "key_abc123",
  "name": "data-pipeline",
  "key": "butterfly_live_a1b2c3d4e5f6g7h8i9j0...",
  "scopes": ["read:capsules", "write:capsules"],
  "created_at": "2024-12-03T10:00:00Z",
  "expires_at": "2025-12-31T23:59:59Z"
}
```

**Note:** The full API key is only shown once at creation. Store it securely.

### Using API Keys

```bash
curl http://localhost:8081/api/v1/capsules \
  -H "X-API-Key: butterfly_live_a1b2c3d4e5f6g7h8i9j0..."
```

### API Key Format

```
butterfly_{env}_{random}

Where:
  env    = live | test | dev
  random = 32-character cryptographically random string
```

### API Key Best Practices

1. **Never commit API keys** to version control
2. **Use environment variables** for key storage
3. **Rotate keys regularly** (at least annually)
4. **Use minimal scopes** required for the task
5. **Monitor key usage** for anomalies

---

## OAuth 2.0 / OIDC Integration

For enterprise SSO integration.

### Supported Flows

| Flow | Use Case |
|------|----------|
| Authorization Code | Web applications |
| Authorization Code + PKCE | SPAs, mobile apps |
| Client Credentials | Service-to-service |

### Authorization Code Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      OAuth 2.0 Authorization Code Flow                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   User          Browser           BUTTERFLY          Identity Provider      │
│    │               │                  │                      │              │
│    │ 1. Login      │                  │                      │              │
│    │──────────────▶│                  │                      │              │
│    │               │                  │                      │              │
│    │               │  2. Redirect to IdP                     │              │
│    │               │────────────────────────────────────────▶│              │
│    │               │                  │                      │              │
│    │               │  3. Login at IdP │                      │              │
│    │◀──────────────────────────────────────────────────────────│              │
│    │               │                  │                      │              │
│    │ 4. Consent    │                  │                      │              │
│    │──────────────▶│                  │                      │              │
│    │               │                  │                      │              │
│    │               │  5. Redirect with code                  │              │
│    │               │◀────────────────────────────────────────│              │
│    │               │                  │                      │              │
│    │               │  6. Exchange code│                      │              │
│    │               │─────────────────▶│                      │              │
│    │               │                  │  7. Verify with IdP  │              │
│    │               │                  │─────────────────────▶│              │
│    │               │                  │                      │              │
│    │               │                  │  8. ID token         │              │
│    │               │                  │◀─────────────────────│              │
│    │               │                  │                      │              │
│    │               │  9. JWT token    │                      │              │
│    │               │◀─────────────────│                      │              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### OIDC Configuration

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com/realms/butterfly
          jwk-set-uri: https://auth.example.com/realms/butterfly/protocol/openid-connect/certs
```

### Client Credentials Flow

For service accounts:

```bash
curl -X POST https://auth.example.com/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=my-service" \
  -d "client_secret=my-secret" \
  -d "scope=read:capsules"
```

---

## Permission Scopes

### Available Scopes

| Scope | Description |
|-------|-------------|
| `read:capsules` | Read CAPSULE history |
| `write:capsules` | Create CAPSULEs |
| `read:specs` | Read specifications |
| `write:specs` | Create/update specifications |
| `execute:plans` | Execute plans |
| `read:events` | Read detected events |
| `read:actors` | Read actor models |
| `admin:governance` | Manage policies |
| `admin:users` | User management |
| `admin:monitoring` | Access monitoring |

### Scope Hierarchy

```
admin:* ──▶ Full administrative access
  │
  ├── admin:governance
  ├── admin:users
  └── admin:monitoring

write:* ──▶ Read + write access
  │
  ├── write:capsules (includes read:capsules)
  └── write:specs (includes read:specs)

read:* ──▶ Read-only access
  │
  ├── read:capsules
  ├── read:specs
  ├── read:events
  └── read:actors
```

---

## Security Best Practices

### Token Storage

| Environment | Storage Method |
|-------------|----------------|
| Browser | HttpOnly cookie, sessionStorage |
| Mobile | Secure storage (Keychain, Keystore) |
| Server | Environment variables, secrets manager |

### Request Security

Always include:

```bash
curl https://api.butterfly.example.com/api/v1/capsules \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: $(uuidgen)" \
  --tlsv1.2
```

### Error Handling

| Status | Meaning | Action |
|--------|---------|--------|
| 401 | Invalid/expired token | Refresh or re-authenticate |
| 403 | Insufficient permissions | Request additional scopes |
| 429 | Rate limited | Back off and retry |

---

## Troubleshooting

### Token Validation Errors

```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Token validation failed",
    "details": {
      "reason": "expired"
    }
  }
}
```

**Solutions:**
- Check token expiration
- Verify signature algorithm matches
- Ensure correct issuer and audience

### Scope Errors

```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Insufficient permissions",
    "details": {
      "required": ["write:specs"],
      "provided": ["read:specs"]
    }
  }
}
```

**Solutions:**
- Request additional scopes
- Use appropriate credentials

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Overview](README.md) | API section overview |
| [Security Architecture](../architecture/security-architecture.md) | Security design |
| [Error Handling](error-handling.md) | Error codes |
