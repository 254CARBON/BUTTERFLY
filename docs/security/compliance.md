# BUTTERFLY Compliance Documentation

> Regulatory compliance and audit mappings

**Last Updated**: 2025-12-03  
**Target Audience**: Compliance teams, auditors, security

---

## Overview

BUTTERFLY is designed to meet enterprise compliance requirements. This document maps BUTTERFLY controls to common compliance frameworks.

---

## Compliance Summary

| Framework | Status | Scope |
|-----------|--------|-------|
| SOC2 Type II | ✅ Compliant | Full ecosystem |
| GDPR | ✅ Compliant | Data processing |
| OWASP ASVS Level 2 | ✅ Compliant | Application security |
| CIS Benchmarks | ✅ Implemented | Infrastructure |
| NIST CSF | ✅ Aligned | Security program |

---

## SOC2 Type II

### Trust Service Criteria Mapping

#### Security (CC)

| Criteria | Control | Implementation |
|----------|---------|----------------|
| CC6.1 | Logical access controls | RBAC, JWT authentication |
| CC6.2 | Authentication mechanisms | Multi-factor, API keys |
| CC6.3 | Access authorization | Role-based permissions |
| CC6.6 | Boundary protection | Network policies, WAF |
| CC6.7 | Threat detection | SIEM, IDS/IPS |
| CC7.1 | Configuration management | Infrastructure as Code |
| CC7.2 | Change management | GitOps, PR reviews |
| CC7.3 | Vulnerability management | Dependency scanning |
| CC7.4 | Incident response | Runbooks, escalation |
| CC8.1 | Change authorization | CI/CD approvals |

#### Availability (A)

| Criteria | Control | Implementation |
|----------|---------|----------------|
| A1.1 | Capacity planning | Auto-scaling, monitoring |
| A1.2 | Recovery procedures | DR runbooks, backups |
| A1.3 | Testing recovery | DR drills quarterly |

#### Processing Integrity (PI)

| Criteria | Control | Implementation |
|----------|---------|----------------|
| PI1.1 | Processing accuracy | Input validation |
| PI1.2 | Processing completeness | Transaction logging |
| PI1.3 | Processing timeliness | SLA monitoring |

#### Confidentiality (C)

| Criteria | Control | Implementation |
|----------|---------|----------------|
| C1.1 | Data classification | Classification policy |
| C1.2 | Confidentiality protection | Encryption at rest |

#### Privacy (P)

| Criteria | Control | Implementation |
|----------|---------|----------------|
| P1.1 | Privacy notice | Privacy policy |
| P3.1 | Consent management | Opt-in mechanisms |
| P4.1 | Data collection | Minimal data collection |
| P5.1 | Data use | Purpose limitation |
| P6.1 | Data disclosure | Audit logging |
| P7.1 | Data quality | Validation, correction |
| P8.1 | Inquiry and complaint | Support channels |

---

## GDPR Compliance

### Article Mapping

| Article | Requirement | Implementation |
|---------|-------------|----------------|
| Art. 5 | Data processing principles | Minimal collection, purpose limitation |
| Art. 6 | Lawful basis | Consent management, legitimate interest |
| Art. 7 | Consent | Explicit consent capture |
| Art. 12-14 | Transparency | Privacy notices |
| Art. 15 | Right of access | Data export API |
| Art. 16 | Right to rectification | User data update |
| Art. 17 | Right to erasure | Data deletion API |
| Art. 20 | Data portability | Standard export format |
| Art. 25 | Data protection by design | Security architecture |
| Art. 30 | Records of processing | Processing activity log |
| Art. 32 | Security of processing | Encryption, access controls |
| Art. 33 | Breach notification | Incident response process |
| Art. 35 | DPIA | Privacy impact assessment |

### Data Subject Rights API

```bash
# Right to access
GET /api/v1/gdpr/subject/{subjectId}/data

# Right to erasure
DELETE /api/v1/gdpr/subject/{subjectId}

# Right to portability
GET /api/v1/gdpr/subject/{subjectId}/export
```

---

## OWASP ASVS Level 2

### Verification Requirements

#### V1: Architecture

| ID | Requirement | Status |
|----|-------------|--------|
| V1.1 | Security architecture | ✅ Documented |
| V1.2 | Authentication architecture | ✅ Implemented |
| V1.4 | Access control architecture | ✅ RBAC |
| V1.5 | Input/output architecture | ✅ Validation |

#### V2: Authentication

| ID | Requirement | Status |
|----|-------------|--------|
| V2.1 | Password security | ✅ Argon2id |
| V2.2 | Authentication security | ✅ JWT, MFA |
| V2.5 | Credential recovery | ✅ Secure flow |
| V2.7 | API authentication | ✅ API keys |

#### V3: Session Management

| ID | Requirement | Status |
|----|-------------|--------|
| V3.1 | Session management | ✅ Secure tokens |
| V3.2 | Session binding | ✅ User binding |
| V3.3 | Session termination | ✅ Explicit logout |

#### V4: Access Control

| ID | Requirement | Status |
|----|-------------|--------|
| V4.1 | General access control | ✅ RBAC |
| V4.2 | Operation level access | ✅ Method-level |
| V4.3 | Data access | ✅ Tenant isolation |

#### V5: Validation

| ID | Requirement | Status |
|----|-------------|--------|
| V5.1 | Input validation | ✅ Bean validation |
| V5.2 | Sanitization | ✅ Output encoding |
| V5.3 | Output encoding | ✅ XSS prevention |

#### V6: Cryptography

| ID | Requirement | Status |
|----|-------------|--------|
| V6.1 | Data classification | ✅ Implemented |
| V6.2 | Algorithms | ✅ Modern algorithms |
| V6.3 | Random values | ✅ SecureRandom |

#### V7: Error Handling

| ID | Requirement | Status |
|----|-------------|--------|
| V7.1 | Log content | ✅ No sensitive data |
| V7.2 | Log processing | ✅ Centralized |
| V7.4 | Error handling | ✅ Safe errors |

#### V8: Data Protection

| ID | Requirement | Status |
|----|-------------|--------|
| V8.1 | General data protection | ✅ Encryption |
| V8.2 | Client data protection | ✅ HTTPS |
| V8.3 | Sensitive data | ✅ Classification |

#### V9: Communication

| ID | Requirement | Status |
|----|-------------|--------|
| V9.1 | TLS | ✅ TLS 1.3 |
| V9.2 | Server security | ✅ HSTS |

---

## CIS Benchmarks

### Kubernetes (CIS Kubernetes Benchmark)

| Section | Control | Status |
|---------|---------|--------|
| 1.1 | API server | ✅ Hardened |
| 1.2 | Controller manager | ✅ Hardened |
| 2.1 | etcd | ✅ Encrypted |
| 4.1 | Worker node | ✅ Hardened |
| 5.1 | RBAC | ✅ Implemented |
| 5.2 | Pod security | ✅ Policies |
| 5.3 | Network policies | ✅ Implemented |
| 5.7 | Secrets | ✅ Encrypted |

### Docker (CIS Docker Benchmark)

| Section | Control | Status |
|---------|---------|--------|
| 2.1 | Host configuration | ✅ Hardened |
| 4.1 | Container images | ✅ Scanned |
| 5.1 | Container runtime | ✅ Non-root |
| 5.2 | AppArmor/seccomp | ✅ Enabled |

---

## NIST Cybersecurity Framework

### Function Mapping

#### Identify (ID)

| Category | Activities | Status |
|----------|-----------|--------|
| ID.AM | Asset Management | ✅ CMDB, inventory |
| ID.BE | Business Environment | ✅ Risk assessment |
| ID.GV | Governance | ✅ Security policies |
| ID.RA | Risk Assessment | ✅ Annual review |
| ID.RM | Risk Management | ✅ Risk register |

#### Protect (PR)

| Category | Activities | Status |
|----------|-----------|--------|
| PR.AC | Access Control | ✅ RBAC, MFA |
| PR.AT | Awareness Training | ✅ Security training |
| PR.DS | Data Security | ✅ Encryption |
| PR.IP | Information Protection | ✅ Policies |
| PR.MA | Maintenance | ✅ Patch management |
| PR.PT | Protective Technology | ✅ WAF, IDS |

#### Detect (DE)

| Category | Activities | Status |
|----------|-----------|--------|
| DE.AE | Anomalies and Events | ✅ SIEM |
| DE.CM | Continuous Monitoring | ✅ Observability |
| DE.DP | Detection Processes | ✅ Alerting |

#### Respond (RS)

| Category | Activities | Status |
|----------|-----------|--------|
| RS.RP | Response Planning | ✅ IR plan |
| RS.CO | Communications | ✅ Notification |
| RS.AN | Analysis | ✅ Forensics |
| RS.MI | Mitigation | ✅ Runbooks |
| RS.IM | Improvements | ✅ Post-mortems |

#### Recover (RC)

| Category | Activities | Status |
|----------|-----------|--------|
| RC.RP | Recovery Planning | ✅ DR plan |
| RC.IM | Improvements | ✅ DR tests |
| RC.CO | Communications | ✅ Status page |

---

## Audit Evidence

### Available Evidence

| Evidence Type | Location | Frequency |
|---------------|----------|-----------|
| Access logs | SIEM | Real-time |
| Configuration records | Git repos | Per change |
| Vulnerability scans | Scanning tool | Weekly |
| Penetration tests | Security reports | Annual |
| Policy documents | Documentation | As updated |
| Training records | HR system | Per training |
| Incident reports | Ticketing | Per incident |
| Change records | Jira/GitHub | Per change |

### Audit Request Process

1. Submit audit request to security@butterfly.example.com
2. Schedule kickoff meeting
3. Provide secure access to evidence portal
4. Conduct walkthrough sessions
5. Respond to evidence requests
6. Review draft findings
7. Receive final report

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Security Overview](README.md) | Security index |
| [Security Model](security-model.md) | Technical controls |
| [Hardening Guide](hardening.md) | Implementation |

