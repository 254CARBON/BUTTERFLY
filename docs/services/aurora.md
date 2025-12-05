# AURORA Service Documentation

## Overview

AURORA is the Self-Healing Platform for the BUTTERFLY ecosystem. It automatically detects anomalies, diagnoses root causes, and executes remediations to maintain system health with minimal human intervention.

## Purpose

AURORA enables:
- **Autonomous healing** - Automatic detection and resolution of issues
- **Anomaly detection** - Multiple detection strategies (statistical, ML, rule-based)
- **Root cause analysis** - AI-powered diagnosis of underlying problems
- **Chaos immunization** - Learning from failures to prevent recurrence

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         AURORA                               │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   Anomaly    │  │     RCA      │  │ Remediation  │       │
│  │  Detectors   │  │    Engine    │  │  Executor    │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│         │                │                  │                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Incident    │  │  Playbook    │  │   Health     │       │
│  │   Manager    │  │   Registry   │  │   Checker    │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
            │                │                  │
            ▼                ▼                  ▼
      [PERCEPTION]       [PLATO]          [SYNAPSE]
```

## Key Components

### Anomaly Detectors
- Statistical detectors (Z-score, MAD)
- ML-based detectors (Isolation Forest, Autoencoder)
- Rule-based detectors (threshold, pattern)
- Ensemble voting for high confidence

### RCA Engine
- Correlates anomalies across services
- Uses NEXUS temporal data for context
- AI-powered root cause hypothesis generation
- Historical pattern matching

### Remediation Executor
- Executes playbooks from registry
- Integrates with SYNAPSE for actions
- Handles rollback on failure
- Post-remediation health verification

### Incident Manager
- Tracks incident lifecycle
- Aggregates related anomalies
- Manages escalation paths
- Records to CAPSULE for history

## API Endpoints

### Anomaly Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/aurora/anomalies` | GET | List detected anomalies |
| `/api/v1/aurora/anomalies/{id}` | GET | Get anomaly details |
| `/api/v1/aurora/anomalies/{id}/acknowledge` | POST | Acknowledge anomaly |

### Incident Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/aurora/incidents` | GET | List incidents |
| `/api/v1/aurora/incidents/{id}` | GET | Get incident details |
| `/api/v1/aurora/incidents/open` | GET | Get open incidents |
| `/api/v1/aurora/incidents/{id}/resolve` | POST | Manually resolve |

### Remediation

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/aurora/remediation/requests` | GET | List remediation requests |
| `/api/v1/aurora/remediation/{id}/execute` | POST | Manually trigger |
| `/api/v1/aurora/remediation/{id}/rollback` | POST | Rollback remediation |

### Learning & Immunization

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/aurora/learning/patterns` | GET | Get learned patterns |
| `/api/v1/aurora/immunization` | GET | Get immunization status |
| `/api/v1/aurora/playbooks` | GET | List playbooks |

## Kafka Topics

| Topic | Schema | Description |
|-------|--------|-------------|
| `aurora.anomaly.detection` | `AuroraAnomalyDetection.avsc` | Detected anomalies |
| `aurora.remediation.requests` | `AuroraRemediationRequest.avsc` | Remediation requests |
| `aurora.remediation.results` | `AuroraRemediationResult.avsc` | Remediation outcomes |

## Dependencies

- **PERCEPTION** - Metrics and event stream
- **PLATO** - Remediation approval for high-risk actions
- **SYNAPSE** - Remediation action execution
- **CAPSULE** - Incident history storage
- **NEXUS** - Temporal correlation data

## Configuration

```yaml
aurora:
  detection:
    sensitivity: 0.8
    min-confidence: 0.75
    aggregation-window-seconds: 60
  rca:
    timeout-seconds: 30
    fallback-to-rules: true
    lookback-hours: 168
  remediation:
    auto-execute-max-severity: SEV3_MEDIUM
    require-approval-above: SEV2_HIGH
    health-check-timeout-seconds: 120
    max-concurrent-remediations: 5
  immunization:
    learning-enabled: true
    pattern-min-occurrences: 2
```

## Health Checks

- `/actuator/health/aurora` - Overall AURORA health
- `/actuator/health/detectors` - Detector status
- `/actuator/health/rca` - RCA engine status
- `/actuator/health/remediation` - Remediation executor status

## Metrics

| Metric | Description |
|--------|-------------|
| `butterfly_anomaly_true_positive_total` | True positive detections |
| `butterfly_anomaly_false_positive_total` | False positive detections |
| `butterfly_remediation_duration_seconds` | MTTR histogram |
| `butterfly_remediation_success_total` | Successful remediations |
| `butterfly_remediation_failure_total` | Failed remediations |
| `butterfly_aurora_incident_status` | Incident counts by status |

## Remediation Types

| Type | Description | Auto-Execute |
|------|-------------|--------------|
| `SCALE_UP` | Increase replicas | Yes (Low risk) |
| `SCALE_DOWN` | Decrease replicas | Yes (Low risk) |
| `RESTART_SERVICE` | Rolling restart | Yes (Medium risk) |
| `CONFIG_ROLLBACK` | Revert configuration | Approval required |
| `DATABASE_FAILOVER` | Switch to replica | Approval required |
| `TRAFFIC_SHIFT` | Redirect traffic | Approval required |

## Security

- All remediation actions logged and auditable
- High-risk actions require PLATO governance approval
- Role-based access for manual interventions
- Kill-switch for emergency halt of all auto-remediations

## Related Documentation

- [Self-Healing Architecture](../architecture/self-healing-architecture.md)
- [AURORA Integration Guide](../integration/aurora-integration.md)
- [AURORA Operations Runbook](../operations/runbooks/aurora-operations.md)
