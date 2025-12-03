# BUTTERFLY Runbooks

> Operational procedures for incident response and maintenance

**Last Updated**: 2025-12-03  
**Target Audience**: On-call engineers, SREs

---

## Overview

This section contains operational runbooks for managing BUTTERFLY in production. Each runbook provides step-by-step procedures for common operational scenarios.

---

## Runbook Catalog

| Runbook | Description | Urgency |
|---------|-------------|---------|
| [Incident Response](incident-response.md) | Handle production incidents | Critical |
| [Disaster Recovery](disaster-recovery.md) | Recover from major failures | Critical |
| [Scaling](scaling.md) | Scale services up or down | Normal |
| [Common Issues](common-issues.md) | Troubleshoot frequent problems | Normal |

---

## Quick Reference

### Emergency Contacts

| Role | Contact | Hours |
|------|---------|-------|
| On-Call Primary | PagerDuty | 24/7 |
| On-Call Secondary | PagerDuty (escalation) | 24/7 |
| Engineering Lead | Slack: @eng-lead | Business hours |
| SRE Team | Slack: #sre-team | 24/7 |

### Critical URLs

| Resource | URL |
|----------|-----|
| Grafana | https://grafana.butterfly.example.com |
| Prometheus | https://prometheus.butterfly.example.com |
| PagerDuty | https://butterfly.pagerduty.com |
| Status Page | https://status.butterfly.example.com |

### Service Health Quick Check

```bash
# Check all services
for svc in capsule odyssey perception plato nexus; do
  curl -s "http://${svc}:8080/actuator/health" | jq '.status'
done

# Kubernetes
kubectl get pods -n butterfly -o wide
kubectl top pods -n butterfly
```

---

## Runbook Template

When creating new runbooks, use this template:

```markdown
# [Procedure Name]

## Overview
Brief description of when to use this runbook.

## Prerequisites
- Access requirements
- Tools needed
- Knowledge prerequisites

## Symptoms
- What alerts fire
- What users report
- Observable symptoms

## Impact
- User impact
- Business impact
- Severity level

## Procedure

### Step 1: [Action]
\`\`\`bash
command
\`\`\`

Expected output:
\`\`\`
output
\`\`\`

### Step 2: [Action]
...

## Verification
How to confirm the issue is resolved.

## Escalation
When and how to escalate.

## Post-Incident
- Metrics to review
- Documentation to update
- Follow-up actions
```

---

## Severity Definitions

### P1 - Critical

- Complete service outage
- Data loss or corruption
- Security breach
- **Response**: 15 minutes, all hands

### P2 - High

- Major feature unavailable
- Significant performance degradation
- **Response**: 1 hour, on-call + backup

### P3 - Medium

- Minor feature unavailable
- Moderate performance impact
- **Response**: 4 hours, on-call

### P4 - Low

- Minor issue with workaround
- No user impact
- **Response**: Next business day

---

## Communication Templates

### Incident Start

```
🚨 INCIDENT STARTED

Severity: P[1-4]
Service: [service name]
Impact: [user impact description]
Status: Investigating

Lead: @[name]
Channel: #incident-[date]-[short-name]
```

### Status Update

```
📊 INCIDENT UPDATE

Status: [Investigating/Identified/Monitoring/Resolved]
Duration: [X] minutes
Update: [what changed]

Next update in: [X] minutes
```

### Incident Resolution

```
✅ INCIDENT RESOLVED

Duration: [X] minutes
Root Cause: [brief description]
Resolution: [what fixed it]

Post-mortem: [link] (within 48 hours)
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Operations Overview](../README.md) | Operations index |
| [Alerting](../monitoring/alerting.md) | Alert configuration |
| [Production Checklist](../deployment/production-checklist.md) | Go-live checklist |

