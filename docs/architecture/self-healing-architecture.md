# Self-Healing Architecture

## Overview

The AURORA Self-Healing Architecture enables the BUTTERFLY ecosystem to automatically detect, diagnose, and remediate issues with minimal human intervention.

## Design Principles

1. **Detect Early** - Anomaly detection before user impact
2. **Diagnose Accurately** - AI-powered root cause analysis
3. **Remediate Safely** - Governance-bounded autonomous actions
4. **Learn Continuously** - Improve from every incident

## Healing Loop

```
┌─────────────────────────────────────────────────────────────┐
│                    Self-Healing Loop                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│       ┌───────────────────────────────────────────┐         │
│       │                                           │         │
│       ▼                                           │         │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐      │         │
│  │  Detect  │──▶│ Diagnose │──▶│ Remediate│──────┘         │
│  └──────────┘   └──────────┘   └──────────┘                 │
│       ▲              │              │                        │
│       │              │              │                        │
│       │              ▼              ▼                        │
│       │         ┌──────────┐  ┌──────────┐                  │
│       │         │  Learn   │◀─│  Verify  │                  │
│       │         └──────────┘  └──────────┘                  │
│       │              │                                       │
│       └──────────────┘                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Detection Layer

### Detection Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| **Statistical** | Z-score, MAD, percentile | Metric anomalies |
| **ML-based** | Isolation Forest, LSTM | Complex patterns |
| **Rule-based** | Threshold, pattern match | Known issues |
| **Ensemble** | Combined voting | High confidence |

### Detection Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│                   Detection Pipeline                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  PERCEPTION                                                  │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Metric Stream (latency, errors, CPU, memory, etc.)   │   │
│  └──────────────────────────────────────────────────────┘   │
│       │                                                      │
│       ▼                                                      │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐              │
│  │ Statistical│ │  ML-based  │ │ Rule-based │              │
│  │  Detector  │ │  Detector  │ │  Detector  │              │
│  └────────────┘ └────────────┘ └────────────┘              │
│       │              │              │                        │
│       └──────────────┼──────────────┘                        │
│                      ▼                                       │
│            ┌──────────────────┐                             │
│            │  Ensemble Voter  │                             │
│            └──────────────────┘                             │
│                      │                                       │
│                      ▼                                       │
│            ┌──────────────────┐                             │
│            │ Anomaly Published │                            │
│            └──────────────────┘                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Diagnosis Layer

### Root Cause Analysis (RCA)

```
┌─────────────────────────────────────────────────────────────┐
│                    RCA Process                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Anomaly                                                     │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────┐                                       │
│  │ Context Gathering│◀──── CAPSULE (historical incidents)   │
│  └──────────────────┘◀──── NEXUS (temporal correlations)    │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────┐                                       │
│  │ Pattern Matching │◀──── Known pattern library            │
│  └──────────────────┘                                       │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────┐                                       │
│  │ AI Hypothesis    │◀──── LLM-powered analysis             │
│  │ Generation       │                                       │
│  └──────────────────┘                                       │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────┐                                       │
│  │ Confidence Score │                                       │
│  └──────────────────┘                                       │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────┐                                       │
│  │ Root Cause +     │                                       │
│  │ Remediation Rec  │                                       │
│  └──────────────────┘                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### RCA Confidence Levels

| Confidence | Action |
|------------|--------|
| > 90% | Auto-execute remediation |
| 70-90% | Auto-execute with monitoring |
| 50-70% | Require human approval |
| < 50% | Escalate to human |

## Remediation Layer

### Remediation Types

```
┌─────────────────────────────────────────────────────────────┐
│                   Remediation Types                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  LOW RISK (Auto-Execute)                                    │
│  ├── Scale up replicas                                      │
│  ├── Clear cache                                            │
│  ├── Restart pod (rolling)                                  │
│  └── Adjust rate limits                                     │
│                                                              │
│  MEDIUM RISK (Auto with Monitoring)                         │
│  ├── Service restart                                        │
│  ├── Connection pool reset                                  │
│  ├── Feature flag toggle                                    │
│  └── Config hot-reload                                      │
│                                                              │
│  HIGH RISK (Approval Required)                              │
│  ├── Database failover                                      │
│  ├── Traffic redirect                                       │
│  ├── Full service deployment                                │
│  └── Data migration                                         │
│                                                              │
│  CRITICAL (Human Only)                                      │
│  ├── Data deletion                                          │
│  ├── Security credential rotation                           │
│  └── Disaster recovery                                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Remediation Execution

```
┌─────────────────────────────────────────────────────────────┐
│                Remediation Execution Flow                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Remediation Request                                        │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────┐   No     ┌──────────────┐                │
│  │ Risk Check   │─────────▶│    Queue     │                │
│  │ (Auto OK?)   │          │  for Approval│                │
│  └──────────────┘          └──────────────┘                │
│       │ Yes                                                  │
│       ▼                                                      │
│  ┌──────────────┐                                           │
│  │    PLATO     │                                           │
│  │  Governance  │                                           │
│  └──────────────┘                                           │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────┐                                           │
│  │   SYNAPSE    │                                           │
│  │   Execute    │                                           │
│  └──────────────┘                                           │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────┐   Fail   ┌──────────────┐                │
│  │ Health Check │─────────▶│   Rollback   │                │
│  └──────────────┘          └──────────────┘                │
│       │ Pass                                                 │
│       ▼                                                      │
│  ┌──────────────┐                                           │
│  │  Complete +  │                                           │
│  │    Learn     │                                           │
│  └──────────────┘                                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Learning Layer (Chaos Immunization)

### Pattern Learning

```
┌─────────────────────────────────────────────────────────────┐
│                    Pattern Learning                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Resolved Incident                                          │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Extract Pattern                                       │   │
│  │ - Symptoms (what was observed)                        │   │
│  │ - Root cause (what caused it)                         │   │
│  │ - Remediation (what fixed it)                         │   │
│  │ - Duration (MTTR)                                     │   │
│  └──────────────────────────────────────────────────────┘   │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────┐                                           │
│  │ Match to     │                                           │
│  │ Existing?    │                                           │
│  └──────────────┘                                           │
│       │ Yes              │ No                                │
│       ▼                  ▼                                   │
│  ┌──────────────┐   ┌──────────────┐                       │
│  │ Reinforce    │   │   Create     │                       │
│  │  Pattern     │   │ New Pattern  │                       │
│  └──────────────┘   └──────────────┘                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Immunization Benefits

1. **Faster Detection** - Known patterns trigger immediately
2. **Faster Diagnosis** - Skip RCA for known issues
3. **Faster Remediation** - Cached playbook execution
4. **Higher Confidence** - More data = better decisions

## Metrics & SLOs

| Metric | Target | Description |
|--------|--------|-------------|
| **MTTR** | < 5 min | Mean time to recover |
| **Detection Latency** | < 30 sec | Time to detect anomaly |
| **Precision** | > 95% | True positive rate |
| **Auto-Resolution** | > 80% | % resolved without human |
| **Rollback Rate** | < 5% | % of failed remediations |

## Safety Controls

### Kill Switches

1. **Global** - Halt all autonomous remediation
2. **Service-level** - Halt for specific service
3. **Type-level** - Halt specific remediation type
4. **Time-based** - Halt during change freeze windows

### Rate Limits

```yaml
aurora:
  safety:
    max-concurrent-remediations: 5
    max-remediations-per-hour: 20
    cooldown-between-same-service: 300s
    max-consecutive-failures: 3
```

## Related Documentation

- [AURORA Service](../services/aurora.md)
- [AURORA Integration](../integration/aurora-integration.md)
- [Chaos Experiments](../../chaos/experiments/)
