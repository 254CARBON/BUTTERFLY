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

---

## On-Call Workflow

### Rotation Schedule

| Role | Coverage | Handoff Time |
|------|----------|--------------|
| Primary On-Call | 7 days | Monday 09:00 UTC |
| Secondary On-Call | 7 days | Monday 09:00 UTC |
| Incident Commander | P1/P2 incidents | As needed |

### On-Call Responsibilities

**Primary On-Call:**
- First responder for all alerts
- Acknowledge alerts within 5 minutes
- Initial triage and mitigation
- Escalate as needed

**Secondary On-Call:**
- Backup for primary
- Available for escalation
- Domain expertise support
- Takes over if primary unavailable

**Incident Commander (P1/P2 only):**
- Coordinates response efforts
- Manages communication
- Ensures timeline documentation
- Post-incident reporting

### Starting Your Shift

1. **Review open alerts**
   ```bash
   # Check Alertmanager for active alerts
   curl -s http://alertmanager:9093/api/v2/alerts | jq '.[] | {name: .labels.alertname, severity: .labels.severity, status: .status.state}'
   ```

2. **Review recent incidents**
   - Check #incidents Slack channel
   - Review incident log in wiki

3. **Verify contact information**
   - PagerDuty app installed and notifications working
   - Slack notifications enabled
   - Phone number current

4. **Confirm handoff from outgoing on-call**
   - Any ongoing issues?
   - Any scheduled changes/maintenance?
   - Any things to watch?

### Shift Handoff Checklist

**Outgoing On-Call:**

```markdown
## Shift Handoff: [DATE]

### Active Incidents
- [ ] None OR List with current status

### Ongoing Issues / Watches
- [ ] None OR List things to monitor

### Scheduled Maintenance
- [ ] None OR List upcoming maintenance windows

### Recent Changes
- [ ] Deployments in last 24h: [list]
- [ ] Config changes: [list]

### Notes
- [Any other relevant information]

Handoff completed: [time]
Outgoing: @[name]
Incoming: @[name]
```

**Incoming On-Call:**
1. Review handoff notes
2. Acknowledge receipt in channel
3. Test PagerDuty notification
4. Review dashboards for baseline

---

## Alertmanager Configuration

### Alert Routing

```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default-receiver'
  routes:
    # Critical alerts → PagerDuty immediately
    - match:
        severity: critical
      receiver: 'pagerduty-critical'
      continue: true
    
    # Warning alerts → Slack + PagerDuty
    - match:
        severity: warning
      receiver: 'pagerduty-warning'
      continue: true
    
    # Service-specific routing
    - match:
        team: perception
      receiver: 'perception-team'
    - match:
        team: capsule
      receiver: 'capsule-team'
    
    # Info alerts → Slack only
    - match:
        severity: info
      receiver: 'slack-info'

receivers:
  - name: 'default-receiver'
    slack_configs:
      - channel: '#butterfly-alerts'
        send_resolved: true
  
  - name: 'pagerduty-critical'
    pagerduty_configs:
      - service_key: '<P1_SERVICE_KEY>'
        severity: critical
  
  - name: 'pagerduty-warning'
    pagerduty_configs:
      - service_key: '<P2_SERVICE_KEY>'
        severity: warning
  
  - name: 'slack-info'
    slack_configs:
      - channel: '#butterfly-alerts-info'
        send_resolved: true
  
  - name: 'perception-team'
    slack_configs:
      - channel: '#perception-team'
    email_configs:
      - to: 'perception-team@company.com'
  
  - name: 'capsule-team'
    slack_configs:
      - channel: '#capsule-team'
```

### Notification Channels

| Channel | Purpose | Recipients |
|---------|---------|------------|
| #butterfly-alerts | All critical/warning alerts | On-call, team leads |
| #butterfly-alerts-info | Info/watch alerts | Interested parties |
| #incidents | Active incident discussions | All engineers |
| PagerDuty (Critical) | P1 alerts | Primary → Secondary → Engineering Lead |
| PagerDuty (Warning) | P2/P3 alerts | Primary → Secondary |

---

## Escalation Matrix

### By Severity

| Severity | Initial Response | 15 min | 30 min | 1 hour |
|----------|-----------------|--------|--------|--------|
| P1 | Primary | + Secondary | + Engineering Lead | + CTO |
| P2 | Primary | + Secondary | + Team Lead | Engineering Lead |
| P3 | Primary | Secondary | Team Lead | - |
| P4 | Primary | - | - | - |

### By Service/Domain

| Service | Primary Expert | Secondary Expert | Team Lead |
|---------|---------------|------------------|-----------|
| PERCEPTION | @perception-oncall | @data-team | @perception-lead |
| CAPSULE | @capsule-oncall | @backend-team | @capsule-lead |
| ODYSSEY | @odyssey-oncall | @graph-team | @odyssey-lead |
| PLATO | @plato-oncall | @governance-team | @plato-lead |
| NEXUS | @nexus-oncall | @platform-team | @nexus-lead |
| Infrastructure | @infra-oncall | @sre-team | @infra-lead |

### Escalation Procedure

1. **Attempt self-resolution** (15-30 min depending on severity)

2. **Page secondary on-call**
   ```
   PagerDuty → Escalate → Secondary On-Call
   OR
   /pd page @secondary-oncall "Need help with [incident]"
   ```

3. **Page domain expert**
   ```
   /pd page @[service]-oncall "Domain expertise needed for [incident]"
   ```

4. **Page engineering leadership** (P1 only, or per escalation matrix)
   ```
   /pd page @engineering-lead "P1 incident requires leadership attention"
   ```

### Out-of-Hours Escalation

- Primary on-call is 24/7
- Secondary on-call is best-effort outside business hours
- Engineering Lead available for P1 only
- CTO page requires P1 + customer impact

---

## Alert Silencing During Maintenance

### Before Planned Maintenance

1. **Create silence in Alertmanager**
   ```bash
   # Silence all alerts for a service
   curl -X POST http://alertmanager:9093/api/v2/silences \
     -H "Content-Type: application/json" \
     -d '{
       "matchers": [
         {"name": "service", "value": "capsule-service", "isRegex": false}
       ],
       "startsAt": "2024-01-15T10:00:00Z",
       "endsAt": "2024-01-15T12:00:00Z",
       "createdBy": "your-name",
       "comment": "Planned maintenance: database migration"
     }'
   ```

2. **Notify team**
   ```
   🔕 MAINTENANCE SILENCE ACTIVE
   
   Service: capsule-service
   Start: 2024-01-15 10:00 UTC
   End: 2024-01-15 12:00 UTC
   Reason: Database migration
   
   Silence ID: [returned ID]
   ```

### After Maintenance

1. **Remove silence** (if not auto-expired)
   ```bash
   curl -X DELETE http://alertmanager:9093/api/v2/silence/[SILENCE_ID]
   ```

2. **Verify service health**
3. **Notify team maintenance complete**

---

## Tools and Access

### Required Access for On-Call

| Tool | Purpose | Access Request |
|------|---------|----------------|
| PagerDuty | Alert management | IT ticket |
| Grafana | Dashboards, metrics | LDAP auto-provision |
| kubectl | Kubernetes access | Platform team |
| AWS Console | Infrastructure | IAM request |
| Slack | Communication | IT ticket |

### Quick Links

| Resource | URL |
|----------|-----|
| Grafana | https://grafana.internal/d/butterfly-overview |
| Alertmanager | https://alertmanager.internal |
| PagerDuty | https://company.pagerduty.com |
| Status Page | https://status.company.com/admin |
| Runbooks | https://docs.internal/runbooks |
| On-Call Schedule | https://company.pagerduty.com/schedules |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Runbooks Index](README.md) | All runbooks |
| [Common Issues](common-issues.md) | Troubleshooting |
| [Disaster Recovery](disaster-recovery.md) | DR procedures |
| [SLO Breach](slo-breach.md) | SLO violation response |
| [Connector Failures](connector-failures.md) | Connector troubleshooting |
| [DLQ Replay](dlq-replay.md) | Dead letter queue handling |
| [Observability Guide](../../OBSERVABILITY_GUIDE.md) | Monitoring overview |

