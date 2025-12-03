# {RUNBOOK_TITLE}

> **Purpose**: What this runbook is for.  
> **Audience**: Who should use it (e.g., SRE, on-call engineer).  
> **Related Services**: Modules and systems this runbook applies to.  
> **Last Updated**: {DATE}

---

## 1. Preconditions

- What must be true before you run these steps (access, tools, environment).
- Safety or legal constraints.

## 2. Symptoms

- How this issue is typically detected (alerts, dashboards, logs).
- Example alert names or metric thresholds.

## 3. Diagnosis

1. Step-by-step checks to confirm the problem.
2. Commands, dashboards, or queries to run.

**Example Commands:**
```bash
# Check service health
curl -s http://localhost:8080/actuator/health | jq .

# Check logs for errors
kubectl logs -l app=service-name --tail=100 | grep ERROR
```

## 4. Remediation Steps

1. Ordered list of actions to resolve the issue.
2. Include exact commands where possible.

**Example:**
```bash
# Restart the service
kubectl rollout restart deployment/service-name

# Clear cache if needed
redis-cli FLUSHDB
```

## 5. Verification

- How to confirm that the system is healthy again.
- Which dashboards or metrics to check.
- Expected recovery time.

## 6. Rollback / Fallback

- Steps to roll back changes if remediation fails.
- Fallback modes or degraded operation strategies.

**Example:**
```bash
# Rollback to previous version
kubectl rollout undo deployment/service-name
```

## 7. Post-Incident Actions

- Clean-up tasks.
- Incident review or follow-up tickets.
- Communication requirements.

## 8. References

- Links to design docs, specs, dashboards, and related runbooks.
- Contact information for escalation.

---

| Document | Description |
|----------|-------------|
| [Related Runbook 1](./related-runbook.md) | Description |
| [Architecture](../architecture/) | System design |
| [Monitoring Dashboard](link) | Grafana/Prometheus dashboard |

