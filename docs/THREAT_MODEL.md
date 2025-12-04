# BUTTERFLY Threat Model
**Security Assessment for Production Hardening (Phase 6)**  
_Component of the BUTTERFLY ecosystem engineered by 254STUDIOZ_

**Last Updated**: 2025-12-03  
**Version**: 1.0.0  
**Status**: Active

---

## Table of Contents

1. [Overview](#1-overview)
2. [System Boundaries](#2-system-boundaries)
3. [External API Attack Surface](#3-external-api-attack-surface)
4. [Internal Orchestration Attack Surface](#4-internal-orchestration-attack-surface)
5. [Threat Catalog](#5-threat-catalog)
6. [Risk Assessment Matrix](#6-risk-assessment-matrix)
7. [Mitigation Strategies](#7-mitigation-strategies)
8. [Security Controls Mapping](#8-security-controls-mapping)

---

## 1. Overview

### 1.1 Purpose

This document provides a high-level threat model for the BUTTERFLY ecosystem, focusing on:
- **ODYSSEY**: Strategic cognition system with world state, path engine, and experiment capabilities
- **HORIZONBOX**: Probabilistic foresight engine
- **QUANTUMROOM**: Multi-agent game-theoretic simulation
- **Supporting infrastructure**: Kafka messaging, JanusGraph/Cassandra persistence, Redis caching

### 1.2 Scope

| In Scope | Out of Scope |
|----------|--------------|
| REST API endpoints | Physical security |
| WebSocket connections | Social engineering |
| Kafka message streams | Client-side vulnerabilities |
| Database access patterns | Third-party SaaS integrations |
| Inter-service communication | Mobile applications |
| Authentication/Authorization | Network infrastructure |

### 1.3 Methodology

This threat model follows STRIDE (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege) combined with DREAD risk assessment.

---

## 2. System Boundaries

### 2.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           TRUST BOUNDARY: Internet                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│    ┌──────────┐    ┌──────────┐    ┌─────────────┐    ┌─────────────┐  │
│    │ External │    │ Partner  │    │   Internal  │    │   Admin     │  │
│    │  Users   │    │   Apps   │    │   Services  │    │   Users     │  │
│    └────┬─────┘    └────┬─────┘    └──────┬──────┘    └──────┬──────┘  │
│         │               │                 │                   │         │
├─────────┼───────────────┼─────────────────┼───────────────────┼─────────┤
│         │               │   TRUST BOUNDARY: API Gateway       │         │
│         └───────────────┴─────────┬───────┴───────────────────┘         │
│                                   │                                      │
│    ┌──────────────────────────────┴──────────────────────────────────┐  │
│    │                         Load Balancer / API Gateway              │  │
│    │                    (TLS Termination, Rate Limiting)              │  │
│    └──────────────────────────────┬──────────────────────────────────┘  │
│                                   │                                      │
├───────────────────────────────────┼──────────────────────────────────────┤
│                     TRUST BOUNDARY: Application Tier                     │
│                                   │                                      │
│    ┌──────────┐    ┌──────────┐  │  ┌─────────────┐    ┌────────────┐  │
│    │ ODYSSEY  │    │HORIZONBOX│  │  │ QUANTUMROOM │    │  CAPSULE   │  │
│    │ :8000    │    │  :8001   │  │  │   :8002     │    │   :8082    │  │
│    └────┬─────┘    └────┬─────┘  │  └──────┬──────┘    └─────┬──────┘  │
│         │               │        │         │                  │         │
│    ┌────┴───────────────┴────────┴─────────┴──────────────────┴─────┐  │
│    │                    Internal Service Mesh                        │  │
│    │              (mTLS, Service Discovery, Circuit Breakers)        │  │
│    └────┬───────────────┬────────────────────┬──────────────────────┘  │
│         │               │                    │                          │
├─────────┼───────────────┼────────────────────┼──────────────────────────┤
│         │   TRUST BOUNDARY: Data Tier        │                          │
│         │               │                    │                          │
│    ┌────┴────┐    ┌─────┴─────┐    ┌────────┴───────┐    ┌──────────┐  │
│    │PostgreSQL│    │  Kafka    │    │ JanusGraph/    │    │  Redis   │  │
│    │  :5432  │    │  :9092    │    │  Cassandra     │    │  :6379   │  │
│    └─────────┘    └───────────┘    └────────────────┘    └──────────┘  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Data Classification

| Classification | Examples | Protection Level |
|----------------|----------|------------------|
| **Confidential** | JWT secrets, API keys, credentials | Encrypted at rest and transit, vault storage |
| **Internal** | Actor models, path weights, experiment results | Encrypted in transit, access-controlled |
| **Public** | API documentation, health endpoints | No encryption required |

---

## 3. External API Attack Surface

### 3.1 ODYSSEY API (`/api/v1/*`)

| Endpoint Category | Path Pattern | Risk Level | Assets at Risk |
|-------------------|--------------|------------|----------------|
| World State | `/api/v1/state/*` | High | Strategic intelligence, entity metrics |
| Path Engine | `/api/v1/paths/*` | High | Narrative path weights, predictions |
| Actor Engine | `/api/v1/actors/*` | High | Actor models, behavioral profiles |
| Experiments | `/api/v1/experiments/*` | Critical | Experiment designs, execution control |
| Adversarial | `/api/v1/adversarial/*` | Critical | Vulnerability data, attack surfaces |
| Story Radar | `/api/v1/radar/*` | Medium | Market signals, trade ideas |
| Gameboard | `/api/v1/gameboard/*` | High | Structural interventions |
| Consensus | `/api/v1/consensus/*` | Medium | Multi-school consensus data |
| Admin | `/api/admin/*` | Critical | System configuration |

**Key Threats:**
- T-API-01: Unauthorized access to strategic intelligence
- T-API-02: Manipulation of path weights or actor models
- T-API-03: Injection attacks via query parameters
- T-API-04: Mass data exfiltration through list endpoints

### 3.2 HORIZONBOX API (`/api/v1/*`)

| Endpoint | Path | Risk Level | Assets at Risk |
|----------|------|------------|----------------|
| Foresight Query | `POST /api/v1/foresight` | High | Probability curves, forecasts |
| Regime Transitions | `POST /api/v1/regime-transitions` | High | Regime shift probabilities |
| Constraints | `POST /api/v1/constraints` | Medium | Structural constraint data |
| Status | `GET /api/v1/status` | Low | Service health |

**Key Threats:**
- T-HB-01: Forged foresight queries affecting downstream decisions
- T-HB-02: Information leakage through timing attacks
- T-HB-03: Denial of service via complex query patterns

### 3.3 QUANTUMROOM API (`/api/v1/*`)

| Endpoint Category | Path Pattern | Risk Level | Assets at Risk |
|-------------------|--------------|------------|----------------|
| Simulations | `/api/v1/simulations/*` | High | Multi-agent simulation results |
| Coalitions | `/api/v1/coalitions/*` | High | Coalition formation data |
| Equilibria | `/api/v1/equilibria/*` | High | Game-theoretic equilibrium profiles |
| Negotiations | `/api/v1/negotiations/*` | High | Negotiation corridor data |

**Key Threats:**
- T-QR-01: Manipulation of actor response predictions
- T-QR-02: Unauthorized access to coalition intelligence
- T-QR-03: Injection through scenario parameters

### 3.4 GraphQL Endpoint

| Endpoint | Path | Risk Level | Assets at Risk |
|----------|------|------------|----------------|
| GraphQL | `/graphql` | High | All queryable data |
| GraphiQL | `/graphiql` | Medium | Query exploration |

**Key Threats:**
- T-GQL-01: Query complexity attacks (DoS)
- T-GQL-02: Introspection leaking schema details
- T-GQL-03: Batched query abuse

### 3.5 WebSocket Connections

| Endpoint | Path | Risk Level | Assets at Risk |
|----------|------|------------|----------------|
| Real-time updates | `ws://*/ws/*` | High | Real-time strategic updates |

**Key Threats:**
- T-WS-01: Connection hijacking
- T-WS-02: Message injection
- T-WS-03: Resource exhaustion via connection flooding

---

## 4. Internal Orchestration Attack Surface

### 4.1 CAPSULE Update Cycle

The CAPSULE orchestrator (`CapsuleOrchestratorService`) performs periodic updates:

```
Event Ingestion → CAPSULE Assembly → JanusGraph Persist → 
HORIZONBOX Fusion → QUANTUMROOM Fusion → Strategy Synthesis
```

| Component | Threat | Risk Level |
|-----------|--------|------------|
| Event Ingestion | Malicious event injection | High |
| CAPSULE Assembly | Data integrity tampering | High |
| Graph Persistence | Gremlin injection | Critical |
| Fusion Services | Parameter manipulation | High |

**Key Threats:**
- T-CAP-01: Malformed CAPSULE causing system instability
- T-CAP-02: Gremlin traversal injection via actor/entity IDs
- T-CAP-03: Race conditions during concurrent updates

### 4.2 Kafka Event Streams

| Topic Pattern | Direction | Risk Level | Threat |
|---------------|-----------|------------|--------|
| `perception.events.*` | Inbound | High | Event spoofing, replay attacks |
| `rim.fast-path` | Inbound | Critical | High-frequency manipulation |
| `synapse.trade.ideas` | Outbound | High | Trade idea interception |
| `odyssey.perception.dlq` | Internal | Medium | DLQ poisoning |

**Key Threats:**
- T-KFK-01: Unauthorized message production
- T-KFK-02: Consumer group hijacking
- T-KFK-03: Schema Registry tampering
- T-KFK-04: Message replay attacks

### 4.3 Database Access

| Database | Protocol | Risk Level | Threats |
|----------|----------|------------|---------|
| PostgreSQL | JDBC | High | SQL injection, credential theft |
| JanusGraph | Gremlin | Critical | Traversal injection |
| Cassandra | CQL | High | CQL injection |
| Redis | RESP | Medium | Cache poisoning |

**Key Threats:**
- T-DB-01: SQL/CQL/Gremlin injection
- T-DB-02: Credential exposure in logs
- T-DB-03: Unencrypted connections
- T-DB-04: Backup data exposure

### 4.4 Inter-Service Communication

| Flow | Protocol | Risk Level | Threats |
|------|----------|------------|---------|
| ODYSSEY ↔ HORIZONBOX | HTTP/REST | High | SSRF, response tampering |
| ODYSSEY ↔ QUANTUMROOM | HTTP/REST | High | SSRF, response tampering |
| ODYSSEY ↔ CAPSULE | HTTP/REST | High | Request forgery |
| All Services ↔ Redis | RESP | Medium | Cache poisoning |

**Key Threats:**
- T-IPC-01: Service impersonation
- T-IPC-02: Man-in-the-middle attacks
- T-IPC-03: Response manipulation

---

## 5. Threat Catalog

### 5.1 STRIDE Classification

| ID | Threat | Category | Affected Components |
|----|--------|----------|---------------------|
| **Authentication & Identity** |
| T-AUTH-01 | JWT token theft via XSS | Spoofing | All API endpoints |
| T-AUTH-02 | Weak JWT secret brute force | Spoofing | Auth service |
| T-AUTH-03 | Token replay attacks | Spoofing | All protected endpoints |
| T-AUTH-04 | API key exposure in logs | Information Disclosure | Logging system |
| **Authorization** |
| T-AUTHZ-01 | Scope escalation | Elevation of Privilege | API endpoints |
| T-AUTHZ-02 | IDOR (Insecure Direct Object Reference) | Information Disclosure | Entity endpoints |
| T-AUTHZ-03 | Missing function-level access control | Elevation of Privilege | Admin endpoints |
| **Injection** |
| T-INJ-01 | SQL injection | Tampering | PostgreSQL queries |
| T-INJ-02 | Gremlin traversal injection | Tampering | JanusGraph queries |
| T-INJ-03 | GraphQL injection | Tampering | GraphQL resolver |
| T-INJ-04 | Log injection | Tampering | Logging system |
| **Denial of Service** |
| T-DOS-01 | API rate limit bypass | Denial of Service | All endpoints |
| T-DOS-02 | GraphQL complexity attack | Denial of Service | GraphQL endpoint |
| T-DOS-03 | WebSocket connection flooding | Denial of Service | WebSocket handlers |
| T-DOS-04 | Kafka consumer lag attack | Denial of Service | Event processors |
| **Data Security** |
| T-DATA-01 | Sensitive data in error responses | Information Disclosure | Exception handlers |
| T-DATA-02 | Mass assignment vulnerabilities | Tampering | DTO binding |
| T-DATA-03 | Unencrypted data at rest | Information Disclosure | Databases |
| T-DATA-04 | Insufficient audit logging | Repudiation | All operations |

### 5.2 Business Logic Threats

| ID | Threat | Risk | Mitigation |
|----|--------|------|------------|
| T-BIZ-01 | Path weight manipulation | Critical | Integrity checks, audit trail |
| T-BIZ-02 | Experiment result tampering | Critical | Signed outcomes, verification |
| T-BIZ-03 | Actor model poisoning | High | Input validation, anomaly detection |
| T-BIZ-04 | Adversarial data exfiltration | Critical | Access controls, data masking |
| T-BIZ-05 | Trade idea front-running | Critical | Encryption, timing controls |

---

## 6. Risk Assessment Matrix

### 6.1 DREAD Scoring

| Threat ID | Damage | Reproducibility | Exploitability | Affected Users | Discoverability | **Total** | **Priority** |
|-----------|--------|-----------------|----------------|----------------|-----------------|-----------|--------------|
| T-AUTH-01 | 8 | 7 | 6 | 9 | 5 | **35** | Critical |
| T-AUTH-02 | 9 | 3 | 4 | 10 | 3 | **29** | High |
| T-AUTHZ-01 | 8 | 8 | 5 | 7 | 6 | **34** | Critical |
| T-INJ-02 | 9 | 6 | 5 | 8 | 4 | **32** | Critical |
| T-DOS-01 | 6 | 9 | 8 | 10 | 8 | **41** | Critical |
| T-BIZ-01 | 10 | 5 | 4 | 8 | 3 | **30** | High |
| T-BIZ-05 | 10 | 4 | 3 | 6 | 2 | **25** | High |

### 6.2 Risk Prioritization

| Priority | Count | Immediate Action Required |
|----------|-------|---------------------------|
| Critical | 4 | Within 1 sprint |
| High | 8 | Within 2 sprints |
| Medium | 6 | Within 1 quarter |
| Low | 3 | Roadmap item |

---

## 7. Mitigation Strategies

### 7.1 Authentication & Authorization

| Control | Implementation | Status |
|---------|----------------|--------|
| JWT validation | Spring Security `SecurityFilterChain` | Planned |
| Token expiration | Short-lived tokens (1h), refresh tokens | Planned |
| Scope enforcement | `@PreAuthorize` annotations | Planned |
| API key rotation | Automated rotation via Vault | Active |
| Centralized SecretProvider | `butterfly-security-starter` + Vault | Active |
| mTLS for services | PLATO↔CAPSULE client certificates | In Progress |

### 7.2 Input Validation

| Control | Implementation | Status |
|---------|----------------|--------|
| Bean validation | `@Valid`, `@NotNull`, `@Size` on DTOs | Partial |
| SQL parameterization | JPA/Hibernate prepared statements | Active |
| Gremlin parameterization | Parameterized traversals | Review needed |
| GraphQL depth limiting | Query complexity analyzer | Planned |
| Request size limits | `server.max-http-header-size` | Active |

### 7.3 Rate Limiting

| Control | Implementation | Status |
|---------|----------------|--------|
| API rate limiting | `RateLimitingFilter` with Redis | Planned |
| Per-client limits | Token bucket algorithm | Planned |
| Endpoint-specific limits | Configurable via properties | Active |
| WebSocket limits | Connection pooling | Planned |

### 7.4 Transport Security

| Control | Implementation | Status |
|---------|----------------|--------|
| TLS 1.3 | Load balancer termination | Planned |
| HSTS headers | `Strict-Transport-Security` | Planned |
| Certificate pinning | For inter-service calls | Planned |
| PLATO↔CAPSULE mTLS | WebClient keystore + Vault secrets | Active |
| Secure cookies | `HttpOnly`, `Secure`, `SameSite` | Planned |

### 7.5 Security Headers

| Header | Value | Purpose |
|--------|-------|---------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Force HTTPS |
| `X-Content-Type-Options` | `nosniff` | Prevent MIME sniffing |
| `X-Frame-Options` | `DENY` | Prevent clickjacking |
| `Content-Security-Policy` | `default-src 'self'` | XSS protection |
| `X-XSS-Protection` | `1; mode=block` | XSS filter |

### 7.6 Audit Logging

| Event | Log Level | Retention |
|-------|-----------|-----------|
| Authentication success/failure | INFO/WARN | 90 days |
| Authorization denied | WARN | 90 days |
| Data access | DEBUG | 30 days |
| Configuration changes | INFO | 365 days |
| Security alerts | ERROR | 365 days |

---

## 8. Security Controls Mapping

### 8.1 SOC2 Trust Service Criteria

| TSC | Control | BUTTERFLY Implementation |
|-----|---------|-------------------------|
| CC6.1 | Logical access security | JWT + scope-based RBAC |
| CC6.2 | Access authentication | Multi-factor for admin |
| CC6.3 | Access authorization | `@PreAuthorize` annotations |
| CC6.6 | System boundaries | API gateway, network policies |
| CC6.7 | Information transmission | TLS 1.3, mTLS for services |
| CC7.1 | Configuration management | Infrastructure as code |
| CC7.2 | Change management | CI/CD pipeline controls |
| CC8.1 | Incident response | Alerting, runbooks |

### 8.2 OWASP Top 10 Coverage

| Risk | Mitigation | Status |
|------|------------|--------|
| A01 Broken Access Control | RBAC, scope enforcement | Planned |
| A02 Cryptographic Failures | TLS, secure secrets | Partial |
| A03 Injection | Input validation, parameterized queries | Active |
| A04 Insecure Design | Threat modeling, security reviews | Active |
| A05 Security Misconfiguration | Secure defaults, CI config lint (`security:check`) | Active |
| A06 Vulnerable Components | OWASP dependency check | Active |
| A07 Auth Failures | JWT validation, session management | Planned |
| A08 Software/Data Integrity | Signed artifacts, SBOM | Planned |
| A09 Logging Failures | Security event logging | Planned |
| A10 SSRF | URL validation, allowlists | Planned |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [SECURITY_BASELINE.md](SECURITY_BASELINE.md) | Controls inventory and compliance mapping |
| [SECRETS_MANAGEMENT.md](SECRETS_MANAGEMENT.md) | Secrets handling procedures |
| [API_SPECIFICATION.md](../ODYSSEY/API_SPECIFICATION.md) | API security requirements |
| [PLATO/SECURITY.md](../PLATO/SECURITY.md) | PLATO security policy |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2025-12-03 | Security Team | Initial threat model |

---

**Document Maintained By:** BUTTERFLY Security Team, 254STUDIOZ  
**Security Contact:** security@254studioz.com
