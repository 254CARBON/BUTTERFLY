# BUTTERFLY Operations Documentation

> Deployment, monitoring, and operational procedures for the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: DevOps engineers, SREs, Platform teams

---

## Overview

This section covers all operational aspects of running BUTTERFLY in production, including deployment strategies, monitoring, runbooks, and capacity planning.

---

## Section Contents

### Deployment

| Document | Description |
|----------|-------------|
| [Deployment Overview](deployment/README.md) | Deployment strategies |
| [Docker Deployment](deployment/docker.md) | Container-based deployment |
| [Kubernetes Deployment](deployment/kubernetes.md) | K8s orchestration |
| [Production Checklist](deployment/production-checklist.md) | Pre-production validation |

### Monitoring

| Document | Description |
|----------|-------------|
| [Monitoring Overview](monitoring/README.md) | Observability strategy |
| [Metrics Catalog](monitoring/metrics-catalog.md) | All metrics documented |
| [Dashboards](monitoring/dashboards.md) | Grafana dashboards |
| [Alerting](monitoring/alerting.md) | Alert configuration |

### Runbooks

| Document | Description |
|----------|-------------|
| [Runbooks Index](runbooks/README.md) | All operational runbooks |
| [Incident Response](runbooks/incident-response.md) | Incident handling |
| [Disaster Recovery](runbooks/disaster-recovery.md) | DR procedures |
| [Scaling](runbooks/scaling.md) | Scaling operations |
| [Common Issues](runbooks/common-issues.md) | Troubleshooting |

### Planning

| Document | Description |
|----------|-------------|
| [Capacity Planning](capacity-planning.md) | Resource planning |

---

## Quick Reference

### Service Ports

| Service | Default Port | Health Endpoint |
|---------|--------------|-----------------|
| CAPSULE | 8080 | `/actuator/health` |
| ODYSSEY | 8081 | `/actuator/health` |
| PERCEPTION | 8082 | `/actuator/health` |
| PLATO | 8086 | `/actuator/health` |
| NEXUS | 8083 | `/actuator/health` |

### Infrastructure Ports

| Component | Default Port |
|-----------|--------------|
| Kafka | 9092 |
| Schema Registry | 8081 |
| Cassandra | 9042 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| JanusGraph | 8182 |
| ClickHouse | 8123, 9000 |

### Key Metrics Endpoints

| Service | Metrics URL |
|---------|-------------|
| CAPSULE | `http://capsule:8080/actuator/prometheus` |
| ODYSSEY | `http://odyssey:8081/actuator/prometheus` |
| PERCEPTION | `http://perception:8082/actuator/prometheus` |
| PLATO | `http://plato:8086/actuator/prometheus` |
| NEXUS | `http://nexus:8083/actuator/prometheus` |

---

## Operational Requirements

### Minimum Production Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU (total) | 16 cores | 32+ cores |
| Memory (total) | 64 GB | 128+ GB |
| Storage | 500 GB SSD | 2+ TB NVMe |
| Network | 1 Gbps | 10 Gbps |

### High Availability Requirements

| Component | Minimum Replicas | Notes |
|-----------|------------------|-------|
| CAPSULE | 2 | Behind load balancer |
| ODYSSEY | 2 | Behind load balancer |
| PERCEPTION | 3 | Stream processing HA |
| PLATO | 2 | Behind load balancer |
| NEXUS | 3 | Gateway HA |
| Kafka | 3 | Clustered |
| Cassandra | 3 | RF=3 |
| PostgreSQL | 2 | Primary + replica |
| Redis | 3 | Sentinel or cluster |

---

## On-Call Guide

### Escalation Path

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Escalation Path                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Alert Triggered                                                             │
│       │                                                                      │
│       ▼                                                                      │
│  L1: On-Call Engineer (15 min response)                                     │
│       │                                                                      │
│       ├── Resolved → Post-incident review                                   │
│       │                                                                      │
│       ▼                                                                      │
│  L2: Senior Engineer (30 min escalation)                                    │
│       │                                                                      │
│       ├── Resolved → Post-incident review                                   │
│       │                                                                      │
│       ▼                                                                      │
│  L3: Engineering Lead + SRE (1 hour escalation)                             │
│       │                                                                      │
│       ├── Resolved → Post-incident review + ADR if needed                   │
│       │                                                                      │
│       ▼                                                                      │
│  L4: CTO / VP Engineering (Major incident)                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Severity Levels

| Severity | Response Time | Description |
|----------|---------------|-------------|
| P1 - Critical | 15 minutes | Complete service outage |
| P2 - High | 1 hour | Major feature unavailable |
| P3 - Medium | 4 hours | Degraded performance |
| P4 - Low | 24 hours | Minor issue, workaround exists |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Architecture Overview](../architecture/ecosystem-overview.md) | System architecture |
| [Security Architecture](../architecture/security-architecture.md) | Security design |
| [Technology Stack](../architecture/technology-stack.md) | Tech decisions |

