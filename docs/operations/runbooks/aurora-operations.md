# AURORA Operations Runbook

## Overview

This runbook covers operational procedures for the AURORA Self-Healing Platform.

## Health Monitoring

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall health |
| `/actuator/health/aurora` | AURORA-specific health |
| `/actuator/health/detectors` | Anomaly detectors status |
| `/actuator/health/rca` | RCA engine status |
| `/actuator/health/remediation` | Remediation executor status |

### Key Metrics

| Metric | Alert Threshold | Description |
|--------|-----------------|-------------|
| `butterfly_anomaly_true_positive_total` | Precision < 95% | Detection quality |
| `butterfly_remediation_duration_seconds_p50` | > 5 min | MTTR |
| `butterfly_remediation_failure_total` | > 5% rate | Remediation failures |
| `butterfly_aurora_incident_status{status="OPEN"}` | > 10 | Open incidents |

### Grafana Dashboard

Access: `https://grafana.internal/d/aurora-healing`

## Incident Response

### INC-001: High False Positive Rate

**Symptoms:**
- `butterfly_anomaly_false_positive_total` increasing
- Teams reporting alert fatigue
- Precision below 95%

**Diagnosis:**
```bash
# Check precision by detector
curl http://aurora:8080/api/v1/aurora/metrics/precision | jq '.byDetector'

# Check recent false positives
curl http://aurora:8080/api/v1/aurora/anomalies?falsePositive=true&limit=20

# Review detector thresholds
curl http://aurora:8080/api/v1/aurora/config/detectors
```

**Resolution:**
1. Identify problematic detector
2. Adjust sensitivity:
   ```bash
   curl -X PATCH http://aurora:8080/api/v1/aurora/config/detectors/{detectorId} \
     -d '{"sensitivity": 0.7}'
   ```
3. Retrain ML models if needed:
   ```bash
   curl -X POST http://aurora:8080/api/v1/aurora/admin/retrain/{detectorId}
   ```
4. Update baseline metrics

### INC-002: Remediation Failures

**Symptoms:**
- `butterfly_remediation_failure_total` increasing
- Incidents not being resolved
- Rollbacks occurring frequently

**Diagnosis:**
```bash
# Check recent failures
curl http://aurora:8080/api/v1/aurora/remediation/requests?status=FAILED&limit=10

# Check SYNAPSE connectivity
curl http://aurora:8080/actuator/health/synapse

# Check PLATO approvals
curl http://aurora:8080/api/v1/aurora/remediation/pending-approval
```

**Resolution:**
1. Verify SYNAPSE availability
2. Check playbook validity:
   ```bash
   curl http://aurora:8080/api/v1/aurora/playbooks/validate
   ```
3. Review failed remediation details
4. Manual intervention if needed:
   ```bash
   curl -X POST http://aurora:8080/api/v1/aurora/incidents/{id}/resolve \
     -d '{"resolution": "MANUAL", "note": "Resolved via manual intervention"}'
   ```

### INC-003: RCA Engine Degradation

**Symptoms:**
- Diagnosis taking longer than usual
- Lower confidence scores
- Fallback to rule-based RCA

**Diagnosis:**
```bash
# Check RCA health
curl http://aurora:8080/actuator/health/rca

# Check RCA metrics
curl http://aurora:8080/actuator/prometheus | grep rca_

# Check resource usage
kubectl top pods -l app=aurora,component=rca
```

**Resolution:**
1. Check RCA resource limits
2. Scale RCA horizontally:
   ```bash
   kubectl scale deployment/aurora-rca --replicas=3
   ```
3. Clear RCA cache if stale:
   ```bash
   curl -X POST http://aurora:8080/api/v1/aurora/admin/rca/clear-cache
   ```
4. Verify NEXUS connectivity for correlation data

### INC-004: Incident Backlog Growing

**Symptoms:**
- Open incident count increasing
- MTTR increasing
- Alert: "Incident backlog exceeded threshold"

**Diagnosis:**
```bash
# Check incident queue
curl http://aurora:8080/api/v1/aurora/incidents/open | jq 'length'

# Check remediation queue
curl http://aurora:8080/api/v1/aurora/remediation/requests?status=QUEUED

# Check concurrent remediation limit
curl http://aurora:8080/api/v1/aurora/config | jq '.remediation.maxConcurrent'
```

**Resolution:**
1. Increase concurrent remediation limit temporarily:
   ```bash
   curl -X PATCH http://aurora:8080/api/v1/aurora/config \
     -d '{"remediation": {"maxConcurrent": 10}}'
   ```
2. Prioritize critical incidents
3. Consider manual bulk resolution for low-severity
4. Scale AURORA remediation workers

## Scaling

### Horizontal Scaling

```bash
# Scale detection workers
kubectl scale deployment/aurora-detection --replicas=5

# Scale RCA workers
kubectl scale deployment/aurora-rca --replicas=3

# Scale remediation executors
kubectl scale deployment/aurora-remediation --replicas=3
```

### Vertical Scaling

```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "2000m"
```

## Configuration

### Detection Sensitivity

```yaml
aurora:
  detection:
    sensitivity: 0.8        # 0.0 - 1.0
    min-confidence: 0.75    # Minimum to trigger incident
    aggregation-window-seconds: 60
```

### Remediation Limits

```yaml
aurora:
  remediation:
    max-concurrent: 5
    auto-execute-max-severity: SEV3_MEDIUM
    require-approval-above: SEV2_HIGH
    health-check-timeout-seconds: 120
    cooldown-between-same-service: 300s
```

### Immunization Settings

```yaml
aurora:
  immunization:
    learning-enabled: true
    pattern-min-occurrences: 2
    pattern-max-age-days: 90
```

## Maintenance

### Graceful Shutdown

```bash
# Pause detection (stop new incidents)
curl -X POST http://aurora:8080/api/v1/aurora/admin/detection/pause

# Wait for pending remediations
curl http://aurora:8080/api/v1/aurora/remediation/requests?status=IN_PROGRESS

# Then proceed with shutdown
kubectl scale deployment/aurora --replicas=0
```

### Pattern Library Update

```bash
# Export current patterns
curl http://aurora:8080/api/v1/aurora/learning/patterns > patterns.json

# Import updated patterns
curl -X POST http://aurora:8080/api/v1/aurora/learning/patterns/import \
  -H "Content-Type: application/json" \
  -d @patterns.json
```

### Playbook Update

```bash
# Validate new playbook
curl -X POST http://aurora:8080/api/v1/aurora/playbooks/validate \
  -d @new-playbook.yaml

# Deploy playbook
curl -X POST http://aurora:8080/api/v1/aurora/playbooks \
  -d @new-playbook.yaml
```

## Emergency Procedures

### Kill Switch - Stop All Auto-Remediation

```bash
# Immediate halt of all autonomous remediation
curl -X POST http://aurora:8080/api/v1/aurora/admin/kill-switch/activate

# Status check
curl http://aurora:8080/api/v1/aurora/admin/kill-switch/status

# Resume operations
curl -X POST http://aurora:8080/api/v1/aurora/admin/kill-switch/deactivate
```

### Force Close All Incidents

```bash
# Close all non-critical incidents
curl -X POST http://aurora:8080/api/v1/aurora/admin/incidents/bulk-close \
  -d '{"maxSeverity": "SEV3_MEDIUM", "reason": "Emergency cleanup"}'
```

### Reset Immunization Data

```bash
# Clear learned patterns (use with caution)
curl -X POST http://aurora:8080/api/v1/aurora/admin/immunization/reset
```

### Rollback Recent Remediations

```bash
# List recent successful remediations
curl http://aurora:8080/api/v1/aurora/remediation/requests?status=COMPLETED&limit=10

# Rollback specific remediation
curl -X POST http://aurora:8080/api/v1/aurora/remediation/{requestId}/rollback
```

## Scheduled Maintenance Windows

During maintenance windows, configure AURORA to be aware:

```bash
# Set maintenance window
curl -X POST http://aurora:8080/api/v1/aurora/config/maintenance-window \
  -d '{
    "startTime": "2024-12-06T02:00:00Z",
    "endTime": "2024-12-06T04:00:00Z",
    "services": ["order-service", "payment-service"],
    "action": "SUPPRESS_ALERTS"
  }'
```

## Contacts

| Role | Contact |
|------|---------|
| Primary On-Call | PagerDuty: aurora-oncall |
| Escalation | #butterfly-platform Slack |
| Critical Incidents | aurora-critical@company.com |

## Related Documentation

- [AURORA Service](../../services/aurora.md)
- [Self-Healing Architecture](../../architecture/self-healing-architecture.md)
- [AURORA Integration](../../integration/aurora-integration.md)
- [Chaos Experiments](../../../chaos/experiments/)
