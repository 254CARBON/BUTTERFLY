# Incident Response Runbook

> Procedures for handling production incidents

**Last Updated**: 2025-12-03  
**Target Audience**: On-call engineers

---

## Overview

This runbook guides you through responding to production incidents in BUTTERFLY.

---

## Incident Response Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Incident Response Flow                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Alert Triggered                                                             │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ 1. ACKNOWLEDGE (5 min)                                                  ││
│  │    - Acknowledge alert in PagerDuty                                     ││
│  │    - Join incident channel                                              ││
│  │    - Initial assessment                                                 ││
│  └────────────────────────────────────┬────────────────────────────────────┘│
│                                       │                                      │
│                                       ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ 2. ASSESS (15 min)                                                      ││
│  │    - Determine scope and impact                                         ││
│  │    - Assign severity                                                    ││
│  │    - Identify affected services                                         ││
│  │    - Start incident timeline                                            ││
│  └────────────────────────────────────┬────────────────────────────────────┘│
│                                       │                                      │
│                                       ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ 3. COMMUNICATE                                                          ││
│  │    - Post incident start message                                        ││
│  │    - Update status page                                                 ││
│  │    - Notify stakeholders                                                ││
│  └────────────────────────────────────┬────────────────────────────────────┘│
│                                       │                                      │
│                                       ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ 4. MITIGATE                                                             ││
│  │    - Apply immediate fixes                                              ││
│  │    - Restore service                                                    ││
│  │    - Verify recovery                                                    ││
│  └────────────────────────────────────┬────────────────────────────────────┘│
│                                       │                                      │
│                                       ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │ 5. RESOLVE & DOCUMENT                                                   ││
│  │    - Confirm service restored                                           ││
│  │    - Close incident                                                     ││
│  │    - Schedule post-mortem                                               ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Acknowledge

### Actions

1. **Acknowledge the alert** in PagerDuty within 5 minutes
2. **Join the incident channel** (auto-created or create `#incident-YYYY-MM-DD-name`)
3. **Announce yourself**:
   ```
   I'm [name], acknowledging this incident. Starting assessment.
   ```

### Quick Assessment Questions

- What service(s) are affected?
- What symptoms are users seeing?
- When did the issue start?
- Were there any recent deployments or changes?

---

## Step 2: Assess

### Determine Scope

```bash
# Check service health
kubectl get pods -n butterfly
curl -s http://nexus:8083/actuator/health | jq

# Check recent deployments
kubectl rollout history deployment -n butterfly

# Check logs for errors
kubectl logs -n butterfly -l app=capsule --tail=100 | grep -i error

# Check metrics
# Open Grafana: Service Overview dashboard
```

### Assign Severity

| Severity | Criteria | Response |
|----------|----------|----------|
| P1 | Complete outage, data loss | All hands, every 15 min updates |
| P2 | Major feature down | On-call + backup, every 30 min updates |
| P3 | Degraded performance | On-call, every hour updates |
| P4 | Minor issue | Next business day |

### Document Timeline

```markdown
## Incident Timeline

| Time (UTC) | Event |
|------------|-------|
| HH:MM | Alert triggered: [alert name] |
| HH:MM | On-call acknowledged |
| HH:MM | [observations] |
```

---

## Step 3: Communicate

### Post Incident Start Message

```
🚨 INCIDENT STARTED

Severity: P[X]
Service: [affected services]
Impact: [user-visible impact]
Status: Investigating

Lead: @[your name]
Channel: #incident-YYYY-MM-DD-name
```

### Update Status Page

1. Go to status page admin
2. Create incident
3. Set affected components
4. Post initial update

### Stakeholder Notification (P1/P2)

- Engineering Lead
- Product Owner
- Support Team
- Customer Success (if customer-facing)

---

## Step 4: Mitigate

### Common Mitigation Actions

#### Service Restart

```bash
# Kubernetes
kubectl rollout restart deployment/[service] -n butterfly

# Verify restart
kubectl rollout status deployment/[service] -n butterfly
```

#### Rollback Deployment

```bash
# Check history
kubectl rollout history deployment/[service] -n butterfly

# Rollback to previous
kubectl rollout undo deployment/[service] -n butterfly

# Rollback to specific revision
kubectl rollout undo deployment/[service] -n butterfly --to-revision=2
```

#### Scale Up

```bash
kubectl scale deployment/[service] -n butterfly --replicas=5
```

#### Traffic Shift

```bash
# Route traffic away from problematic instance
kubectl label pod [pod-name] serving=false --overwrite
```

#### Circuit Breaker Override

```bash
# Force circuit breaker closed (via actuator)
curl -X POST http://nexus:8083/actuator/circuitbreakers/[name]/close
```

### Document Actions

```markdown
| Time (UTC) | Action | Result |
|------------|--------|--------|
| HH:MM | Restarted capsule service | Service came back but issue persists |
| HH:MM | Rolled back to previous version | Service restored |
```

---

## Step 5: Resolve & Document

### Verify Resolution

```bash
# Check health
curl -s http://nexus:8083/actuator/health | jq '.status'

# Check error rate
# Verify in Grafana: Error rate should be back to baseline

# Check latency
# Verify in Grafana: P99 latency should be back to baseline

# Test critical paths
curl -X POST http://nexus:8083/api/v1/capsules -d '...'
```

### Close Incident

1. **Update status page**: Mark incident as resolved
2. **Post resolution message**:
   ```
   ✅ INCIDENT RESOLVED
   
   Duration: [X] hours [Y] minutes
   Root Cause: [brief description]
   Resolution: [what fixed it]
   
   Post-mortem: [scheduled for DATE]
   ```
3. **Archive incident channel** (after 24 hours)

### Schedule Post-Mortem

- Within 48 hours for P1/P2
- Within 1 week for P3/P4
- Create post-mortem doc from template

---

## Service-Specific Procedures

### CAPSULE Down

```bash
# Check Cassandra
kubectl exec -it cassandra-0 -n butterfly -- nodetool status

# Check Redis
kubectl exec -it redis-0 -n butterfly -- redis-cli ping

# Check CAPSULE logs
kubectl logs -n butterfly -l app=capsule --tail=200
```

### ODYSSEY Down

```bash
# Check JanusGraph
curl http://janusgraph:8182/

# Check graph connectivity
kubectl logs -n butterfly -l app=odyssey --tail=200 | grep -i graph
```

### PERCEPTION Down

```bash
# Check Kafka
kubectl exec -it kafka-0 -n butterfly -- kafka-topics.sh --list --bootstrap-server localhost:9092

# Check PostgreSQL
kubectl exec -it postgres-0 -n butterfly -- psql -U butterfly -c "SELECT 1"

# Check consumer lag
kubectl exec -it kafka-0 -n butterfly -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups
```

### PLATO Down

```bash
# Check Cassandra (same as CAPSULE)
# Check engine queue depth in logs
kubectl logs -n butterfly -l app=plato --tail=200 | grep -i queue
```

### NEXUS Down

```bash
# Check circuit breaker states
curl http://nexus:8083/actuator/circuitbreakers | jq

# Check upstream services
for svc in capsule odyssey perception plato; do
  curl -s "http://${svc}:8080/actuator/health" | jq '.status'
done
```

---

## Escalation

### When to Escalate

- Cannot identify root cause within 30 minutes
- Mitigation attempts unsuccessful
- Need domain expertise
- Customer-facing P1

### How to Escalate

1. **Page secondary on-call** via PagerDuty
2. **Update incident channel**:
   ```
   ⬆️ ESCALATING
   
   Reason: [why escalating]
   Paging: @secondary-oncall
   ```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Runbooks Index](README.md) | All runbooks |
| [Common Issues](common-issues.md) | Troubleshooting |
| [Disaster Recovery](disaster-recovery.md) | DR procedures |

