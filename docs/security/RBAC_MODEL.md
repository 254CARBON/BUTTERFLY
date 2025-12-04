# BUTTERFLY RBAC Model

> Ecosystem-wide Role-Based Access Control for the BUTTERFLY platform

**Last Updated**: 2025-12-04  
**Version**: 1.0.0  
**Status**: Active

---

## Table of Contents

1. [Overview](#1-overview)
2. [Role Hierarchy](#2-role-hierarchy)
3. [Role Definitions](#3-role-definitions)
4. [Permission Scopes](#4-permission-scopes)
5. [Cross-Service Mapping](#5-cross-service-mapping)
6. [Implementation Guide](#6-implementation-guide)
7. [Migration from Legacy Roles](#7-migration-from-legacy-roles)

---

## 1. Overview

### 1.1 Purpose

This document defines the unified Role-Based Access Control (RBAC) model for the BUTTERFLY platform. All services (PERCEPTION, CAPSULE, ODYSSEY, PLATO, NEXUS, SYNAPSE) share this common authorization model to ensure consistent security semantics across the ecosystem.

### 1.2 Key Principles

| Principle | Description |
|-----------|-------------|
| **Unified Identity** | Single principal representation across all services |
| **Hierarchical Roles** | Higher roles inherit permissions from lower roles |
| **Scoped Permissions** | Fine-grained access via `service:resource:action` format |
| **Governance Integration** | Special roles for governed autonomous agents |
| **Audit Traceability** | All authorization decisions are logged |

### 1.3 Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      BUTTERFLY RBAC Architecture                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐    ┌──────────────────┐    ┌───────────────────────┐ │
│  │ ButterflyPrincipal│───▶│  ButterflyRole   │───▶│   ButterflyScope      │ │
│  │                  │    │                  │    │                       │ │
│  │ • subject        │    │ • ADMIN          │    │ service:resource:action│
│  │ • tenantId       │    │ • APPROVER       │    │                       │ │
│  │ • roles[]        │    │ • OPERATOR       │    │ • capsule:*:read      │ │
│  │ • scopes[]       │    │ • DEVELOPER      │    │ • plato:specs:write   │ │
│  │ • metadata       │    │ • ANALYST        │    │ • odyssey:exp:execute │ │
│  └──────────────────┘    │ • GOVERNED_ACTOR │    └───────────────────────┘ │
│                          │ • SERVICE        │                              │
│                          │ • VIEWER         │                              │
│                          └──────────────────┘                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Role Hierarchy

### 2.1 Hierarchy Diagram

```
                    ┌─────────────┐
                    │    ADMIN    │  Level 100 - Full access
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │   APPROVER  │  Level 90 - Governance approval
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │   OPERATOR  │  Level 80 - Platform operations
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │  DEVELOPER  │  Level 70 - Development access
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │   ANALYST   │  Level 60 - Analysis access
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │                         │
       ┌──────┴──────┐           ┌──────┴──────┐
       │GOVERNED_ACTOR│           │   SERVICE   │
       │  Level 50   │           │  Level 40   │
       └──────┬──────┘           └──────┬──────┘
              │                         │
              └────────────┬────────────┘
                           │
                    ┌──────┴──────┐
                    │   VIEWER    │  Level 10 - Read-only
                    └─────────────┘
```

### 2.2 Hierarchy Levels

| Level | Role | Implied Roles |
|-------|------|---------------|
| 100 | ADMIN | All roles |
| 90 | APPROVER | OPERATOR, DEVELOPER, ANALYST, GOVERNED_ACTOR, SERVICE, VIEWER |
| 80 | OPERATOR | DEVELOPER, ANALYST, GOVERNED_ACTOR, SERVICE, VIEWER |
| 70 | DEVELOPER | ANALYST, GOVERNED_ACTOR, SERVICE, VIEWER |
| 60 | ANALYST | GOVERNED_ACTOR, SERVICE, VIEWER |
| 50 | GOVERNED_ACTOR | VIEWER |
| 40 | SERVICE | VIEWER |
| 10 | VIEWER | None |

---

## 3. Role Definitions

### 3.1 ADMIN

**Purpose**: Full system administrator with unrestricted access.

| Attribute | Value |
|-----------|-------|
| Code | `admin` |
| Level | 100 |
| Can Approve | Yes |
| Requires Governance | No |

**Permissions**:
- All read/write/admin operations across all services
- System configuration and administration
- User and role management
- Audit log access

**Use Cases**:
- Platform administrators
- Emergency access
- System maintenance

### 3.2 APPROVER

**Purpose**: Governance approvers who can approve deployments and override policy violations.

| Attribute | Value |
|-----------|-------|
| Code | `approver` |
| Level | 90 |
| Can Approve | Yes |
| Requires Governance | No |

**Permissions**:
- All read operations
- `plato:governance:approve` - Approve workflows
- `plato:governance:override` - Override violations
- `nexus:evolution:read` - View learning signals

**Use Cases**:
- Compliance officers
- Team leads approving deployments
- Risk management personnel

### 3.3 OPERATOR

**Purpose**: Platform operators responsible for monitoring and operational health.

| Attribute | Value |
|-----------|-------|
| Code | `operator` |
| Level | 80 |
| Can Approve | No |
| Requires Governance | No |

**Permissions**:
- All read operations
- `*:monitoring:admin` - Access monitoring dashboards
- `*:health:admin` - Health check management
- `plato:plans:execute` - Execute approved plans

**Use Cases**:
- DevOps engineers
- Site reliability engineers
- Operations teams

### 3.4 DEVELOPER

**Purpose**: Service developers with write access to development and staging environments.

| Attribute | Value |
|-----------|-------|
| Code | `developer` |
| Level | 70 |
| Can Approve | No |
| Requires Governance | No |

**Permissions**:
- All read operations
- `plato:specs:write` - Create specifications
- `plato:artifacts:write` - Create artifacts
- `plato:plans:write` - Create plans
- `perception:signals:write` - Create signals
- `perception:scenarios:write` - Create scenarios

**Use Cases**:
- Software engineers
- Data engineers
- ML engineers

### 3.5 ANALYST

**Purpose**: Human analysts who consume intelligence and provide feedback.

| Attribute | Value |
|-----------|-------|
| Code | `analyst` |
| Level | 60 |
| Can Approve | No |
| Requires Governance | No |

**Permissions**:
- `capsule:*:read` - Read CAPSULE data
- `perception:*:read` - Read PERCEPTION signals
- `odyssey:*:read` - Read ODYSSEY intelligence
- `perception:feedback:write` - Submit feedback

**Use Cases**:
- Business analysts
- Research analysts
- Data scientists (read-only)

### 3.6 GOVERNED_ACTOR

**Purpose**: Autonomous agents (AI/ML systems) that operate under governance constraints.

| Attribute | Value |
|-----------|-------|
| Code | `governed_actor` |
| Level | 50 |
| Can Approve | No |
| Requires Governance | **Yes** |

**Permissions**:
- All read operations
- Write operations subject to PLATO governance approval

**Special Behavior**:
- All write operations must pass PLATO policy evaluation
- Actions are logged with governance correlation
- May trigger human-in-the-loop approval workflows

**Use Cases**:
- AI trading agents
- ML model inference services
- Automated decision systems

### 3.7 SERVICE

**Purpose**: Service accounts for inter-service communication.

| Attribute | Value |
|-----------|-------|
| Code | `service` |
| Level | 40 |
| Can Approve | No |
| Requires Governance | No |

**Permissions**:
- Scoped based on service-to-service contracts
- Typically: read all, write to specific resources

**Use Cases**:
- NEXUS → PLATO integration
- PERCEPTION → CAPSULE data flow
- Internal service mesh communication

### 3.8 VIEWER

**Purpose**: Read-only access for viewing data without modification rights.

| Attribute | Value |
|-----------|-------|
| Code | `viewer` |
| Level | 10 |
| Can Approve | No |
| Requires Governance | No |

**Permissions**:
- `*:*:read` - Read access to all resources

**Use Cases**:
- External stakeholders
- Auditors
- Dashboard viewers

---

## 4. Permission Scopes

### 4.1 Scope Format

```
{service}:{resource}:{action}

Examples:
- capsule:capsules:read      → Read capsules from CAPSULE service
- odyssey:experiments:write  → Create experiments in ODYSSEY
- plato:governance:approve   → Approve governance workflows
- *:*:read                   → Read everything
```

### 4.2 Services

| Service | Code | Description |
|---------|------|-------------|
| CAPSULE | `capsule` | Time-aware data capsules |
| PERCEPTION | `perception` | Signal processing and events |
| ODYSSEY | `odyssey` | Strategic cognition |
| PLATO | `plato` | Governance and specifications |
| NEXUS | `nexus` | Evolution and integration |
| SYNAPSE | `synapse` | Action execution |

### 4.3 Common Resources

| Resource | Services | Description |
|----------|----------|-------------|
| `capsules` | CAPSULE | Data capsules |
| `events` | PERCEPTION | Raw events |
| `signals` | PERCEPTION | Processed signals |
| `scenarios` | PERCEPTION | Signal scenarios |
| `experiments` | ODYSSEY | Strategic experiments |
| `paths` | ODYSSEY | Decision paths |
| `actors` | ODYSSEY | Actor models |
| `specs` | PLATO | Specifications |
| `artifacts` | PLATO | Code artifacts |
| `proofs` | PLATO | Evidence proofs |
| `plans` | PLATO | Execution plans |
| `governance` | PLATO | Governance workflows |
| `evolution` | NEXUS | Learning signals |
| `synthesis` | NEXUS | Strategic options |
| `actions` | SYNAPSE | Executed actions |

### 4.4 Actions

| Action | Description |
|--------|-------------|
| `read` | View/query resources |
| `write` | Create/update resources |
| `delete` | Remove resources |
| `execute` | Run plans/experiments |
| `approve` | Approve governance workflows |
| `admin` | Administrative operations |
| `override` | Override policy violations |
| `subject` | Subject to governance (special) |

---

## 5. Cross-Service Mapping

### 5.1 Service Permission Matrix

| Role | PERCEPTION | CAPSULE | ODYSSEY | PLATO | NEXUS | SYNAPSE |
|------|------------|---------|---------|-------|-------|---------|
| ADMIN | Full | Full | Full | Full | Full | Full |
| APPROVER | Read | Read | Read | Read + Approve | Read | Read |
| OPERATOR | Read + Monitor | Read + Monitor | Read + Monitor | Read + Execute | Read + Monitor | Read + Monitor |
| DEVELOPER | Read + Write | Read | Read + Write | Read + Write | Read | Read |
| ANALYST | Read | Read | Read | Read | Read | Read |
| GOVERNED_ACTOR | Read | Read + Write* | Read + Write* | Read | Read | Read + Write* |
| SERVICE | Contract | Contract | Contract | Contract | Contract | Contract |
| VIEWER | Read | Read | Read | Read | Read | Read |

*Subject to governance approval

### 5.2 Legacy Role Mapping

| Legacy Role | Service | Maps To |
|-------------|---------|---------|
| READER | NEXUS | VIEWER |
| WRITER | NEXUS | DEVELOPER |
| ADMIN | NEXUS | ADMIN |
| USER | PLATO | DEVELOPER |
| ADMIN | PLATO | ADMIN |
| APPROVER | PLATO | APPROVER |
| SERVICE | PLATO | SERVICE |
| ENGINEER | PLATO | DEVELOPER |

---

## 6. Implementation Guide

### 6.1 Java Implementation

```java
// Extract principal from JWT
ButterflyPrincipal principal = JwtClaimsExtractor.extractPrincipal(jwtClaims);

// Check role
if (principal.hasRole(ButterflyRole.ADMIN)) {
    // Admin operation
}

// Check specific permission
if (principal.canPerform("capsule", "capsules", "write")) {
    // Create capsule
}

// Check if governed actor
if (principal.requiresGovernanceApproval()) {
    // Route through PLATO approval
}

// Propagate to downstream service
Map<String, String> headers = SecurityContextPropagator.toHttpHeaders(principal);

// Propagate to Kafka
SecurityContextPropagator.toKafkaHeaders(principal, kafkaRecord.headers());
```

### 6.2 Spring Security Configuration

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                // Admin endpoints
                .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                
                // Governance approval
                .pathMatchers(HttpMethod.POST, "/api/v1/governance/approve")
                    .hasAnyRole("ADMIN", "APPROVER")
                
                // Write operations
                .pathMatchers(HttpMethod.POST, "/api/v1/**")
                    .hasAnyRole("ADMIN", "DEVELOPER", "SERVICE", "GOVERNED_ACTOR")
                
                // Read operations
                .pathMatchers(HttpMethod.GET, "/api/v1/**")
                    .authenticated()
                
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt())
            .build();
    }
}
```

### 6.3 JWT Claims Structure

```json
{
  "sub": "user-123",
  "iss": "butterfly-auth",
  "aud": ["butterfly-api"],
  "exp": 1701610800,
  "iat": 1701607200,
  "tenant": "tenant-abc",
  "roles": ["developer", "analyst"],
  "scope": "capsule:*:read perception:*:read plato:specs:write"
}
```

---

## 7. Migration from Legacy Roles

### 7.1 Migration Strategy

1. **Phase 1**: Deploy common security module
2. **Phase 2**: Update services to recognize both legacy and new roles
3. **Phase 3**: Update JWT issuer to emit new role format
4. **Phase 4**: Remove legacy role handling

### 7.2 Backward Compatibility

The `ButterflyRole.fromLegacyRole()` method provides automatic mapping:

```java
// Legacy "WRITER" role from NEXUS maps to DEVELOPER
ButterflyRole role = ButterflyRole.fromLegacyRole("WRITER");
// role == ButterflyRole.DEVELOPER
```

### 7.3 Migration Checklist

- [ ] Deploy `butterfly-common` with security module
- [ ] Update PERCEPTION security config
- [ ] Update CAPSULE security config
- [ ] Update ODYSSEY security config
- [ ] Update PLATO security config
- [ ] Update NEXUS security config
- [ ] Update SYNAPSE security config
- [ ] Update JWT token issuer
- [ ] Verify cross-service authorization
- [ ] Remove legacy role handling

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Security Architecture](../architecture/security-architecture.md) | Overall security design |
| [Security Baseline](../SECURITY_BASELINE.md) | Compliance controls |
| [API Authentication](../api/authentication.md) | Auth patterns |
| [butterfly-common Security](../../butterfly-common/src/main/java/com/z254/butterfly/common/security/) | Implementation |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2025-12-04 | Security Team | Initial RBAC model |

---

**Document Maintained By:** BUTTERFLY Security Team, 254STUDIOZ  
**Security Contact:** security@254studioz.com

