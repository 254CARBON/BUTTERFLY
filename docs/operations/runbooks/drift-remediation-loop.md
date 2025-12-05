# Drift Remediation Loop Runbook

> **Purpose**: Operational procedures for the ML model drift remediation loop.  
> **Audience**: SREs, ML Engineers, Platform Engineers  
> **Related Services**: PERCEPTION (drift detection, models), PLATO (remediation plans), SYNAPSE (action execution)  
> **Last Updated**: 2025-12-05

---

## 1. Preconditions

### Access Requirements

- [ ] Kubernetes cluster access (`kubectl` configured)
- [ ] PERCEPTION API access (port 8080)
- [ ] SYNAPSE API access (port 8080)
- [ ] PLATO API access (port 8080)
- [ ] Grafana dashboards access
- [ ] Prometheus query access

### Tools Required

```bash
# Verify tools installed
kubectl version --client
curl --version
jq --version
```

### Environment Variables

```bash
export PERCEPTION_URL=http://perception:8080
export SYNAPSE_URL=http://synapse:8080
export PLATO_URL=http://plato:8080
export PROMETHEUS_URL=http://prometheus:9090
export TENANT_ID=your-tenant-id
```

---

## 2. Symptoms

### Alert Triggers

| Alert | Severity | Description |
|-------|----------|-------------|
| `DriftRemediationLatencySLOBreach` | Warning | Drift detection to canary start P95 > 30s |
| `DriftRemediationPersistentHighSeverity` | Critical | High drift severity without remediation for 30m |
| `DriftRemediationRepeatedFailures` | Warning | More than 2 canary rollbacks in 1 hour |
| `DriftDetectionRateSpike` | Warning | Drift detection rate 3x higher than baseline |
| `CanaryDeploymentStalled` | Warning | Canary not advancing for 2+ hours |
| `DriftRemediationPlanExecutionFailure` | Warning | SYNAPSE failed to execute remediation plan |

### Dashboard Indicators

Check the **Drift Remediation** section in the [BUTTERFLY Ecosystem SLO Dashboard](../monitoring/dashboards/butterfly-ecosystem-slo-dashboard.json):

- Model Drift Severity gauge shows red (> 0.8)
- Active Canary Deployments count is 0 when drift is detected
- Canary Deployment Outcomes shows high rollback rate
- Drift Detection Rate spiking

---

## 3. Diagnosis

### 3.1 Check Current Drift Status

```bash
# List models with drift status
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/drift/models" | jq .

# Check specific model drift analysis
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/drift/models/MY_MODEL/analyze" | jq .
```

### 3.2 Check Active Canary Deployments

```bash
# List active deployments
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments?status=IN_PROGRESS" | jq .

# Get specific deployment details
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments/{deploymentId}" | jq .
```

### 3.3 Check PLATO Remediation Plans

```bash
# List recent drift remediation plans
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PLATO_URL/api/v1/plans?planType=DRIFT_REMEDIATION&limit=10" | jq .

# Check specific plan status
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PLATO_URL/api/v1/plans/{planId}" | jq .
```

### 3.4 Check SYNAPSE Actions

```bash
# Query actions for a specific model's RIM node
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$SYNAPSE_URL/api/v1/actions/nexus?rimNodeId=rim:model:MY_MODEL" | jq .

# Query actions by PLATO plan
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$SYNAPSE_URL/api/v1/actions/by-plato-plan/{planId}" | jq .
```

### 3.5 Prometheus Queries

```promql
# Current drift severity for all models
perception_drift_model_severity

# Drift detection rate (last 5 minutes)
sum(rate(perception_drift_model_detected_total[5m])) by (model)

# Active canary deployments
perception_models_deployment_canary_active

# Canary rollback rate (last hour)
increase(perception_models_deployment_canary_rollback_total[1h])

# Remediation loop latency P95
histogram_quantile(0.95, sum(rate(perception_drift_remediation_duration_seconds_bucket[15m])) by (le))

# Failed drift remediation plans
increase(synapse_decisions_failed_total{plan_type="DRIFT_REMEDIATION"}[1h])
```

### 3.6 Check Logs

```bash
# PERCEPTION drift detection logs
kubectl logs -n butterfly -l app=perception-api --tail=500 | grep -i drift

# SYNAPSE action execution logs
kubectl logs -n butterfly -l app=synapse-service --tail=500 | grep -i "drift-remediation"

# PLATO plan execution logs
kubectl logs -n butterfly -l app=plato-service --tail=500 | grep -i "DRIFT_REMEDIATION"
```

---

## 4. Remediation Steps

### 4.1 Trigger E2E Scenario Manually

To validate the full drift remediation loop:

```bash
cd /home/m/BUTTERFLY/apps/butterfly-e2e

# Run the drift remediation e2e scenario
./run-scenarios.sh drift-remediation-loop

# Or run with verbose output
./run-scenarios.sh drift-remediation-loop --verbose
```

### 4.2 Force Canary Rollback

If a canary deployment is failing and not auto-rolling back:

```bash
# Get deployment ID
DEPLOYMENT_ID=$(curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments?status=IN_PROGRESS" | \
  jq -r '.content[0].id')

# Force rollback
curl -X POST -H "X-Tenant-ID: $TENANT_ID" \
  -H "Content-Type: application/json" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments/$DEPLOYMENT_ID/rollback" \
  -d '{"reason": "Manual rollback due to persistent failures"}'
```

### 4.3 Pause Canary Deployment

To pause a canary for investigation:

```bash
curl -X POST -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments/$DEPLOYMENT_ID/pause"

# Resume when ready
curl -X POST -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments/$DEPLOYMENT_ID/resume"
```

### 4.4 Manually Advance Canary

If canary is healthy but stalled:

```bash
curl -X POST -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments/$DEPLOYMENT_ID/advance"
```

### 4.5 Disable Auto-Remediation

To temporarily disable automatic drift remediation:

```bash
# Via Kubernetes ConfigMap
kubectl patch configmap perception-config -n butterfly \
  --type merge -p '{"data":{"PERCEPTION_DRIFT_AUTO_REMEDIATION_ENABLED":"false"}}'

# Restart PERCEPTION to apply
kubectl rollout restart deployment/perception-api -n butterfly
```

Or via API (if supported):

```bash
curl -X PUT -H "X-Tenant-ID: $TENANT_ID" \
  -H "Content-Type: application/json" \
  "$PERCEPTION_URL/api/v1/config/drift" \
  -d '{"autoRemediationEnabled": false}'
```

### 4.6 Clear Drift Baseline

If baseline is corrupted or outdated:

```bash
# Clear baseline for a specific model
curl -X DELETE -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/drift/models/MY_MODEL/baseline"

# Re-register baseline with current production data
curl -X POST -H "X-Tenant-ID: $TENANT_ID" \
  -H "Content-Type: application/json" \
  "$PERCEPTION_URL/api/v1/drift/models/MY_MODEL/baseline" \
  -d '{
    "version": "1.0.0",
    "source": "production",
    "windowDays": 14
  }'
```

### 4.7 Manual Model Rollback

If you need to completely revert to a previous model version:

```bash
# Get current production version
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/versions?stage=PRODUCTION" | jq .

# Promote a previous version to production
curl -X POST -H "X-Tenant-ID: $TENANT_ID" \
  -H "Content-Type: application/json" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/versions/PREVIOUS_VERSION/promote" \
  -d '{"stage": "PRODUCTION", "reason": "Emergency rollback due to drift issues"}'
```

---

## 5. Verification

### 5.1 Verify System Health

```bash
# Check all services healthy
curl -s "$PERCEPTION_URL/actuator/health" | jq .status
curl -s "$SYNAPSE_URL/actuator/health" | jq .status
curl -s "$PLATO_URL/actuator/health" | jq .status
```

### 5.2 Verify Drift Status Normalized

```bash
# Drift severity should be below threshold
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/drift/models/MY_MODEL/status" | \
  jq '.severity < 0.5'
```

### 5.3 Verify Canary Health

```bash
# Check canary deployment is progressing
curl -s -H "X-Tenant-ID: $TENANT_ID" \
  "$PERCEPTION_URL/api/v1/models/MY_MODEL/deployments/$DEPLOYMENT_ID" | \
  jq '.status, .trafficPercentage'
```

### 5.4 Check Metrics

```promql
# Drift severity should be decreasing
perception_drift_model_severity{model="MY_MODEL"}

# No new rollbacks
increase(perception_models_deployment_canary_rollback_total{model="MY_MODEL"}[15m]) == 0

# Remediation latency within SLO
histogram_quantile(0.95, sum(rate(perception_drift_remediation_duration_seconds_bucket[15m])) by (le)) < 30
```

### 5.5 Dashboard Verification

Check the following in Grafana:

- [ ] Model Drift Severity gauge is green (< 0.5)
- [ ] Active Canary Deployments count matches expected
- [ ] Canary Deployment Outcomes shows successful promotions
- [ ] Drift Detection Rate is stable

---

## 6. Rollback / Fallback

### Complete Feature Disable

If the entire drift remediation system is causing issues:

```bash
# Disable drift detection entirely
kubectl set env deployment/perception-api -n butterfly \
  PERCEPTION_DRIFT_ENABLED=false

# Disable PLATO drift remediation plans
kubectl set env deployment/plato-service -n butterfly \
  PLATO_DRIFT_REMEDIATION_ENABLED=false

# Restart services
kubectl rollout restart deployment/perception-api -n butterfly
kubectl rollout restart deployment/plato-service -n butterfly
```

### Restore Previous Configuration

```bash
# Restore from ConfigMap backup (if available)
kubectl apply -f /path/to/backup/perception-config.yaml -n butterfly
kubectl rollout restart deployment/perception-api -n butterfly
```

---

## 7. Post-Incident Actions

### 7.1 Document the Incident

- Record timeline of events
- Document root cause
- Note any manual interventions performed

### 7.2 Update Thresholds if Needed

If thresholds are too sensitive or too lax:

```yaml
# Update in perception ConfigMap
perception:
  drift:
    model:
      psi-threshold-warning: 0.12  # Adjust as needed
      psi-threshold-critical: 0.28
      min-severity-for-auto-remediation: 0.75
```

### 7.3 Review Canary Configuration

If canary deployments are failing consistently:

```yaml
# Update canary settings
perception:
  models:
    canary:
      initial-percentage: 3  # Start smaller
      evaluation-window: "2h"  # Longer evaluation
      minimum-predictions: 2000  # More samples
      error-rate-threshold: 0.03  # Tighter threshold
```

### 7.4 Create Follow-up Tickets

- [ ] Root cause analysis ticket
- [ ] Threshold tuning review
- [ ] Model quality investigation (if repeated failures)
- [ ] Data pipeline review (if data drift)

---

## 8. References

### Documentation

| Document | Description |
|----------|-------------|
| [Drift Governance](../../../PERCEPTION/docs/DRIFT_GOVERNANCE.md) | Drift detection thresholds and event schemas |
| [SYNAPSE Decision Execution](../../../SYNAPSE/docs/decision-execution.md) | How SYNAPSE executes remediation actions |
| [Ecosystem SLO Alerts](../monitoring/alerts/ecosystem-slo-alerts.yml) | Alert definitions |
| [SLO Breach Runbook](./slo-breach.md) | General SLO breach procedures |

### Dashboards

| Dashboard | URL |
|-----------|-----|
| BUTTERFLY Ecosystem SLO | `/d/butterfly-ecosystem-slo` |
| PERCEPTION Drift Monitoring | `/d/perception-drift` |
| SYNAPSE Action Execution | `/d/synapse-actions` |

### Kafka Topics

| Topic | Content |
|-------|---------|
| `perception.signals` | MODEL_DRIFT signals |
| `synapse.decisions.events` | Remediation plan lifecycle |
| `synapse.decisions.feedback` | Terminal outcomes |

### API Quick Reference

```bash
# Drift Analysis
GET  $PERCEPTION_URL/api/v1/drift/models
GET  $PERCEPTION_URL/api/v1/drift/models/{name}/analyze
GET  $PERCEPTION_URL/api/v1/drift/models/{name}/status
POST $PERCEPTION_URL/api/v1/drift/models/{name}/baseline

# Model Deployments
POST $PERCEPTION_URL/api/v1/models/{name}/deployments/canary
POST $PERCEPTION_URL/api/v1/models/{name}/deployments/{id}/advance
POST $PERCEPTION_URL/api/v1/models/{name}/deployments/{id}/rollback
POST $PERCEPTION_URL/api/v1/models/{name}/deployments/{id}/pause
POST $PERCEPTION_URL/api/v1/models/{name}/deployments/{id}/resume

# SYNAPSE Action Trail
GET  $SYNAPSE_URL/api/v1/actions/nexus?rimNodeId={nodeId}
GET  $SYNAPSE_URL/api/v1/actions/by-plato-plan/{planId}
```

---

## Escalation

| Time Since Alert | Action |
|------------------|--------|
| 0-15 min | On-call engineer investigates |
| 15-30 min | Escalate to ML platform team |
| 30-60 min | Escalate to ML model owners |
| 1+ hour | Escalate to engineering management |

### Contacts

| Role | Contact |
|------|---------|
| ML Platform On-Call | #ml-platform-oncall |
| PERCEPTION Team | #perception-support |
| SYNAPSE Team | #synapse-support |
