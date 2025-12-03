# BUTTERFLY Security Baseline
**Production Hardening Compliance Documentation (Phase 6)**  
_Component of the BUTTERFLY ecosystem engineered by 254STUDIOZ_

**Last Updated**: 2025-12-03  
**Version**: 1.0.0  
**Status**: Active

---

## Table of Contents

1. [Overview](#1-overview)
2. [Controls Inventory](#2-controls-inventory)
3. [SOC2 Compliance Mapping](#3-soc2-compliance-mapping)
4. [Implementation Status](#4-implementation-status)
5. [Security Configuration Checklist](#5-security-configuration-checklist)
6. [Vulnerability Management](#6-vulnerability-management)
7. [Incident Response](#7-incident-response)
8. [Security Contacts](#8-security-contacts)

---

## 1. Overview

### 1.1 Purpose

This document establishes the security baseline for the BUTTERFLY ecosystem, providing:
- Comprehensive controls inventory
- SOC2 Trust Service Criteria mapping
- Implementation status tracking
- Compliance verification checklists

### 1.2 Scope

| Component | Version | Coverage |
|-----------|---------|----------|
| ODYSSEY | 1.0.0 | Full |
| HORIZONBOX | 0.1.0 | Full |
| QUANTUMROOM | 0.1.0 | Full |
| CAPSULE | 1.0.0 | Full |
| PERCEPTION | 1.0.0 | Partial |
| PLATO | 1.0.0 | Full |

### 1.3 Baseline Standards

This baseline is aligned with:
- **SOC2 Type II** Trust Service Criteria (CC series)
- **OWASP ASVS** Level 2
- **CIS Benchmarks** for Kubernetes and containers
- **NIST Cybersecurity Framework** Core Functions

---

## 2. Controls Inventory

### 2.1 Authentication Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| AUTH-01 | JWT token validation | Documented | Spring Security filter | Missing filter | Critical |
| AUTH-02 | Token expiration | 1h in docs | Configurable, default 1h | Implement | High |
| AUTH-03 | API key authentication | Documented | Active validation service | Implement | High |
| AUTH-04 | mTLS for services | Planned | Certificate-based auth | Implement | Medium |
| AUTH-05 | OAuth 2.0 / OIDC | Documented | Integration ready | Partial | Medium |
| AUTH-06 | Session management | Not implemented | Secure cookie config | Implement | High |
| AUTH-07 | Multi-factor authentication | Not implemented | TOTP for admin users | Roadmap | Low |

### 2.2 Authorization Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| AUTHZ-01 | Role-based access control | Scopes documented | `@PreAuthorize` enforcement | Partial | Critical |
| AUTHZ-02 | Endpoint-level authorization | Not enforced | All endpoints protected | Implement | Critical |
| AUTHZ-03 | Resource-level authorization | Not implemented | Owner/tenant checks | Roadmap | Medium |
| AUTHZ-04 | Admin API protection | Token-based (dev) | Strong auth + audit | Implement | Critical |
| AUTHZ-05 | GraphQL authorization | Not implemented | Field-level auth | Roadmap | Medium |

### 2.3 Transport Security Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| TLS-01 | HTTPS enforcement | Documented | Load balancer termination | Configure | High |
| TLS-02 | TLS version | Not specified | TLS 1.2+ only | Configure | High |
| TLS-03 | HSTS header | Not configured | `max-age=31536000` | Implement | High |
| TLS-04 | Certificate management | Manual | Automated rotation | Roadmap | Medium |
| TLS-05 | Internal TLS/mTLS | Not implemented | Service mesh encryption | Roadmap | Medium |

### 2.4 Rate Limiting Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| RATE-01 | API rate limiting | Properties defined | Active filter | Implement filter | Critical |
| RATE-02 | Per-client limits | Defined (1000/min query) | Redis-backed tracking | Implement | High |
| RATE-03 | Endpoint-specific limits | Defined | Fine-grained control | Implement | High |
| RATE-04 | Rate limit headers | Not implemented | `X-RateLimit-*` headers | Implement | Medium |
| RATE-05 | WebSocket limits | Not implemented | Connection pooling | Roadmap | Medium |

### 2.5 Input Validation Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| VAL-01 | Bean validation | Partial (`@Valid`) | Comprehensive DTOs | Audit | High |
| VAL-02 | SQL injection prevention | JPA parameterization | Verified coverage | Active | N/A |
| VAL-03 | Gremlin injection prevention | Review needed | Parameterized traversals | Audit | Critical |
| VAL-04 | GraphQL validation | Not implemented | Depth/complexity limits | Implement | High |
| VAL-05 | Request size limits | Default | Explicit limits | Configure | Medium |
| VAL-06 | Content-type validation | Partial | Strict enforcement | Implement | Medium |

### 2.6 Security Headers Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| HDR-01 | X-Content-Type-Options | Not configured | `nosniff` | Implement | High |
| HDR-02 | X-Frame-Options | Not configured | `DENY` | Implement | High |
| HDR-03 | X-XSS-Protection | Not configured | `1; mode=block` | Implement | Medium |
| HDR-04 | Content-Security-Policy | Not configured | Restrictive policy | Implement | High |
| HDR-05 | Referrer-Policy | Not configured | `strict-origin-when-cross-origin` | Implement | Medium |

### 2.7 Logging & Audit Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| LOG-01 | Security event logging | Basic logging | Structured security events | Enhance | High |
| LOG-02 | Authentication audit | Not implemented | Success/failure tracking | Implement | Critical |
| LOG-03 | Authorization audit | Not implemented | Access denied logging | Implement | High |
| LOG-04 | Data access audit | Not implemented | Sensitive data access log | Roadmap | Medium |
| LOG-05 | Log integrity | Not implemented | Tamper-evident logging | Roadmap | Low |
| LOG-06 | Log retention | Not configured | 90-365 days by type | Configure | Medium |

### 2.8 Secrets Management Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| SEC-01 | Secrets in code | Gitignored `.env` | No secrets in repo | Active | N/A |
| SEC-02 | Environment variables | Documented | Enforced injection | Active | N/A |
| SEC-03 | Vault integration | Documented | Production deployment | Roadmap | Medium |
| SEC-04 | Secret rotation | Manual | Automated | Roadmap | Medium |
| SEC-05 | Secret scanning | TruffleHog/Gitleaks | CI blocking | Active | N/A |

### 2.9 Dependency Management Controls

| ID | Control | Current State | Target State | Gap | Priority |
|----|---------|---------------|--------------|-----|----------|
| DEP-01 | OWASP dependency check | CI (CVSS 9 fail) | CI (CVSS 7 fail) | Lower threshold | High |
| DEP-02 | License compliance | CI scanning | Blocking on violations | Configure | Medium |
| DEP-03 | SBOM generation | Not implemented | Per-release SBOM | Roadmap | Low |
| DEP-04 | Dependency updates | Manual | Dependabot/Renovate | Roadmap | Medium |

---

## 3. SOC2 Compliance Mapping

### 3.1 Common Criteria (CC) Series

| TSC ID | Criteria | BUTTERFLY Control | Status |
|--------|----------|-------------------|--------|
| **CC1 - Control Environment** |
| CC1.1 | Commitment to integrity and ethics | Security policy, code of conduct | Documented |
| CC1.2 | Board oversight | Security review process | Partial |
| CC1.3 | Management structure | Roles and responsibilities | Documented |
| CC1.4 | Competence commitment | Security training | Roadmap |
| CC1.5 | Accountability | Incident response plan | Documented |
| **CC2 - Communication and Information** |
| CC2.1 | Information quality | Data classification | Documented |
| CC2.2 | Internal communication | Security documentation | Active |
| CC2.3 | External communication | SECURITY.md, contact info | Active |
| **CC3 - Risk Assessment** |
| CC3.1 | Risk objectives | Threat model | Active |
| CC3.2 | Risk identification | STRIDE analysis | Active |
| CC3.3 | Fraud assessment | Business logic threats | Active |
| CC3.4 | Change impact | Security review in PR | Active |
| **CC4 - Monitoring Activities** |
| CC4.1 | Ongoing monitoring | Health indicators, metrics | Active |
| CC4.2 | Control evaluation | Security scanning CI | Active |
| **CC5 - Control Activities** |
| CC5.1 | Control selection | Security baseline | Active |
| CC5.2 | Technology controls | Defense in depth | Partial |
| CC5.3 | Policy deployment | Documentation, automation | Active |
| **CC6 - Logical and Physical Access** |
| CC6.1 | Logical access security | JWT + RBAC | Implementing |
| CC6.2 | Access registration | API key management | Documented |
| CC6.3 | Access modification | Scope management | Documented |
| CC6.4 | Access removal | Token revocation | Partial |
| CC6.5 | Access authentication | JWT validation | Implementing |
| CC6.6 | Access protection | TLS, security headers | Implementing |
| CC6.7 | Information transmission | TLS 1.2+, encryption | Partial |
| CC6.8 | Access restriction | Rate limiting | Implementing |
| **CC7 - System Operations** |
| CC7.1 | Infrastructure management | IaC, Docker, K8s | Active |
| CC7.2 | Change management | CI/CD, PR review | Active |
| CC7.3 | Configuration management | Spring profiles | Active |
| CC7.4 | Event detection | Actuator, Prometheus | Active |
| CC7.5 | Incident response | Runbooks, alerting | Partial |
| **CC8 - Change Management** |
| CC8.1 | Change authorization | PR approval required | Active |
| **CC9 - Risk Mitigation** |
| CC9.1 | Risk mitigation | Security controls | Active |
| CC9.2 | Vendor management | Dependency scanning | Active |

### 3.2 Availability Criteria (A Series)

| TSC ID | Criteria | BUTTERFLY Control | Status |
|--------|----------|-------------------|--------|
| A1.1 | Capacity planning | Load testing framework | Active |
| A1.2 | Recovery planning | Backup documentation | Partial |
| A1.3 | Recovery testing | DR drills | Roadmap |

### 3.3 Confidentiality Criteria (C Series)

| TSC ID | Criteria | BUTTERFLY Control | Status |
|--------|----------|-------------------|--------|
| C1.1 | Confidential information | Data classification | Active |
| C1.2 | Confidentiality commitment | Privacy policy | Documented |

---

## 4. Implementation Status

### 4.1 Sprint C Deliverables

| Deliverable | Status | Verification |
|-------------|--------|--------------|
| Threat Model (`docs/THREAT_MODEL.md`) | Complete | Document exists |
| Security Baseline (`docs/SECURITY_BASELINE.md`) | Complete | This document |
| Spring Security Config | In Progress | 401 on unauth request |
| Rate Limiting Filter | In Progress | 429 on excess requests |
| Secure Configuration | In Progress | Env var injection |
| CI Security Gate | In Progress | Build fails on CVSS >= 7 |
| ENGINEERING_ROADMAP update | In Progress | Security section exists |

### 4.2 Hardening Verification

| Surface | Hardening | Verification Method |
|---------|-----------|---------------------|
| API | Security filter chain | `curl -X GET /api/v1/state/world` returns 401 |
| API | Rate limiting | 1001 requests in 60s returns 429 |
| API | Security headers | Response contains HSTS, X-Frame-Options |
| Data | TLS enforcement | Connection without TLS rejected |
| Infra | CORS configuration | Cross-origin request from unknown origin rejected |

---

## 5. Security Configuration Checklist

### 5.1 Production Deployment Checklist

```markdown
## Pre-Deployment Security Checklist

### Secrets & Credentials
- [ ] All secrets loaded from environment variables or Vault
- [ ] No hardcoded credentials in configuration files
- [ ] JWT secret is cryptographically strong (256+ bits)
- [ ] Database credentials rotated from development defaults
- [ ] API keys generated with appropriate scopes

### Authentication & Authorization
- [ ] Spring Security filter chain enabled
- [ ] JWT validation configured with proper secret
- [ ] Token expiration set (recommended: 1 hour)
- [ ] Admin endpoints require elevated privileges
- [ ] GraphQL introspection disabled in production

### Transport Security
- [ ] TLS 1.2+ enforced at load balancer
- [ ] HSTS header enabled (max-age >= 31536000)
- [ ] Secure cookie flags set (HttpOnly, Secure, SameSite)
- [ ] Internal service communication uses mTLS

### Rate Limiting
- [ ] Rate limiting filter active
- [ ] Redis configured for distributed rate tracking
- [ ] Rate limit headers returned in responses
- [ ] WebSocket connection limits configured

### Security Headers
- [ ] X-Content-Type-Options: nosniff
- [ ] X-Frame-Options: DENY
- [ ] Content-Security-Policy configured
- [ ] Referrer-Policy set

### Logging & Monitoring
- [ ] Security event logging enabled
- [ ] Log aggregation configured
- [ ] Alerting for authentication failures
- [ ] Alerting for rate limit violations

### Dependency Management
- [ ] OWASP dependency check passes (CVSS < 7)
- [ ] No critical vulnerabilities in dependencies
- [ ] License compliance verified

### Network Security
- [ ] CORS configured with explicit allowed origins
- [ ] Network policies restrict inter-service traffic
- [ ] Database ports not exposed externally
- [ ] Kafka/Redis accessible only from application tier
```

### 5.2 Configuration Properties Reference

```properties
# Security Configuration Reference for Production

# JWT Configuration
security.jwt.secret=${JWT_SECRET_KEY}
security.jwt.expiration-ms=3600000
security.jwt.refresh-expiration-ms=86400000

# Rate Limiting
odyssey.rate-limiting.enabled=true
odyssey.rate-limiting.query-api.requests-per-minute=1000
odyssey.rate-limiting.command-api.requests-per-minute=100
odyssey.rate-limiting.admin-api.requests-per-minute=100

# CORS Configuration
security.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:https://app.254studioz.com}
security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
security.cors.allowed-headers=Authorization,Content-Type,X-Request-ID
security.cors.max-age=3600

# Security Headers
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict

# Database Security
spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# TLS/SSL
server.ssl.enabled=false  # TLS terminated at load balancer
server.forward-headers-strategy=native
```

---

## 6. Vulnerability Management

### 6.1 Scanning Schedule

| Scan Type | Tool | Frequency | Blocking |
|-----------|------|-----------|----------|
| SAST | CodeQL | Every PR | Yes |
| SAST | Semgrep | Every PR | Yes |
| Dependency | OWASP Check | Every PR | Yes (CVSS >= 7) |
| Secrets | TruffleHog | Every PR | Yes |
| Secrets | Gitleaks | Every PR | Yes |
| License | License Plugin | Weekly | No |
| DAST | OWASP ZAP | Pre-release | No (planned) |
| Container | Trivy | Pre-release | No (planned) |

### 6.2 Vulnerability Response SLA

| Severity | CVSS Range | Response Time | Resolution Time |
|----------|------------|---------------|-----------------|
| Critical | 9.0 - 10.0 | 4 hours | 24 hours |
| High | 7.0 - 8.9 | 24 hours | 7 days |
| Medium | 4.0 - 6.9 | 7 days | 30 days |
| Low | 0.1 - 3.9 | 30 days | 90 days |

### 6.3 Dependency Update Policy

- **Critical/High CVE**: Immediate patch release
- **Medium CVE**: Next scheduled release
- **Low CVE**: Quarterly update cycle
- **No CVE**: Major version updates reviewed quarterly

---

## 7. Incident Response

### 7.1 Security Incident Classification

| Severity | Definition | Examples |
|----------|------------|----------|
| P1 - Critical | Active exploitation, data breach | Credential theft, unauthorized access |
| P2 - High | Vulnerability with high likelihood | Exposed endpoint, auth bypass |
| P3 - Medium | Vulnerability with mitigating controls | Missing header, verbose error |
| P4 - Low | Best practice deviation | Outdated library, minor config |

### 7.2 Response Procedures

1. **Detection**: Automated alerting via Prometheus/Grafana
2. **Triage**: Security team assessment within SLA
3. **Containment**: Isolate affected systems if needed
4. **Eradication**: Deploy fix or mitigation
5. **Recovery**: Restore normal operations
6. **Post-Incident**: Root cause analysis, documentation

### 7.3 Communication

| Audience | Timing | Method |
|----------|--------|--------|
| Security Team | Immediate | Slack #security-alerts |
| Engineering | Within 1 hour | Email, Slack |
| Management | Within 4 hours | Email, meeting |
| External (if required) | Per policy | Coordinated disclosure |

---

## 8. Security Contacts

### 8.1 Reporting Vulnerabilities

**DO NOT** report security vulnerabilities through public GitHub issues.

| Contact | Email | Use Case |
|---------|-------|----------|
| Security Team | security@254studioz.com | Vulnerability reports |
| Engineering Lead | engineering@254studioz.com | Security questions |
| Emergency | security-emergency@254studioz.com | Active incidents |

### 8.2 PGP Key

Available at: `https://254studioz.com/.well-known/security.txt`

### 8.3 Bug Bounty

Currently private program. Contact security@254studioz.com for information.

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [THREAT_MODEL.md](THREAT_MODEL.md) | Threat analysis and risk assessment |
| [SECRETS_MANAGEMENT.md](SECRETS_MANAGEMENT.md) | Secrets handling procedures |
| [PLATO/SECURITY.md](../PLATO/SECURITY.md) | PLATO security policy and reporting |
| [API_SPECIFICATION.md](../ODYSSEY/API_SPECIFICATION.md) | API security requirements |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2025-12-03 | Security Team | Initial security baseline |

---

**Document Maintained By:** BUTTERFLY Security Team, 254STUDIOZ  
**Security Contact:** security@254studioz.com

