# Production Checklist

> Pre-launch validation checklist for BUTTERFLY deployments

**Last Updated**: 2025-12-03  
**Target Audience**: DevOps engineers, Release managers

---

## Overview

Complete this checklist before any production deployment. All items marked as **Required** must be completed.

---

## Infrastructure

### Compute Resources

- [ ] **Required**: CPU and memory allocations match capacity planning
- [ ] **Required**: Pod anti-affinity configured for HA services
- [ ] **Required**: Horizontal Pod Autoscaler configured
- [ ] **Required**: Resource limits set on all containers
- [ ] Node pools have sufficient capacity for scale events

### Storage

- [ ] **Required**: Persistent volumes use appropriate storage class
- [ ] **Required**: Volume snapshots enabled for databases
- [ ] **Required**: Storage capacity monitoring configured
- [ ] Backup retention policy documented and tested

### Networking

- [ ] **Required**: TLS certificates valid and auto-renewal configured
- [ ] **Required**: Network policies restrict inter-pod communication
- [ ] **Required**: Ingress configured with appropriate timeouts
- [ ] Load balancer health checks configured
- [ ] DNS entries created and verified

---

## Security

### Authentication & Authorization

- [ ] **Required**: JWT secrets rotated from defaults
- [ ] **Required**: API key salt configured
- [ ] **Required**: Service accounts use least-privilege
- [ ] **Required**: RBAC policies reviewed
- [ ] OAuth/OIDC integration tested (if applicable)

### Secrets Management

- [ ] **Required**: No secrets in container images
- [ ] **Required**: External secrets operator configured (production)
- [ ] **Required**: Secret rotation procedures documented
- [ ] Vault integration tested (if applicable)

### Network Security

- [ ] **Required**: HTTPS enforced (HTTP redirects or blocked)
- [ ] **Required**: Security headers configured (HSTS, CSP, etc.)
- [ ] **Required**: Rate limiting enabled
- [ ] WAF rules configured (if applicable)
- [ ] DDoS protection enabled (if applicable)

### Compliance

- [ ] **Required**: Audit logging enabled
- [ ] **Required**: Data retention policies configured
- [ ] **Required**: PII handling documented
- [ ] SOC2 controls mapped
- [ ] Penetration test completed (annual)

---

## Database

### Cassandra

- [ ] **Required**: Keyspace created with appropriate replication factor
- [ ] **Required**: Tables created with correct schema
- [ ] **Required**: Compaction strategy appropriate for workload
- [ ] **Required**: Backup strategy tested
- [ ] **Required**: Node count >= 3 for production
- [ ] nodetool status shows all nodes UN
- [ ] JVM heap configured appropriately

### PostgreSQL

- [ ] **Required**: Database and user created
- [ ] **Required**: Schema migrations applied
- [ ] **Required**: Connection pooling configured (PgBouncer)
- [ ] **Required**: Replication configured for HA
- [ ] **Required**: Backup tested with restore validation
- [ ] Vacuum settings tuned
- [ ] Connection limits appropriate

### Redis

- [ ] **Required**: Persistence configured (RDB + AOF)
- [ ] **Required**: Sentinel or Cluster mode for HA
- [ ] **Required**: Memory limit set with eviction policy
- [ ] Keyspace notifications enabled (if needed)

### JanusGraph

- [ ] **Required**: Storage backend configured
- [ ] **Required**: Index backend configured
- [ ] **Required**: Schema and indexes created
- [ ] Query timeout configured

### ClickHouse

- [ ] **Required**: Tables created with appropriate engine
- [ ] **Required**: Replication configured
- [ ] **Required**: Merge tree TTL configured for data retention
- [ ] Distributed tables configured (if clustered)

---

## Messaging

### Kafka

- [ ] **Required**: Topics created with appropriate partitions
- [ ] **Required**: Replication factor >= 3
- [ ] **Required**: Schema Registry schemas registered
- [ ] **Required**: Consumer groups configured
- [ ] **Required**: Retention policies set
- [ ] DLQ topics created
- [ ] Monitoring for consumer lag

---

## Application

### Service Configuration

- [ ] **Required**: All environment variables set
- [ ] **Required**: Spring profiles set correctly
- [ ] **Required**: Feature flags configured
- [ ] **Required**: Connection strings verified
- [ ] Logging level appropriate (INFO for production)

### Health Checks

- [ ] **Required**: Readiness probes configured
- [ ] **Required**: Liveness probes configured
- [ ] **Required**: Health endpoints return correct status
- [ ] Dependency health checks included

### API

- [ ] **Required**: API versioning strategy implemented
- [ ] **Required**: Error responses follow standard format
- [ ] **Required**: Rate limits documented
- [ ] OpenAPI spec published
- [ ] API deprecation notices in place (if applicable)

---

## Observability

### Metrics

- [ ] **Required**: Prometheus scraping configured
- [ ] **Required**: Key metrics exposed (request rate, latency, errors)
- [ ] **Required**: JVM metrics exposed
- [ ] **Required**: Business metrics exposed
- [ ] Custom dashboards created

### Logging

- [ ] **Required**: Structured logging (JSON format)
- [ ] **Required**: Log aggregation configured
- [ ] **Required**: Correlation IDs propagated
- [ ] **Required**: Sensitive data not logged
- [ ] Log retention policy configured

### Tracing

- [ ] **Required**: Distributed tracing enabled
- [ ] **Required**: Trace sampling configured
- [ ] Span attributes include relevant context
- [ ] Trace storage retention configured

### Alerting

- [ ] **Required**: P1 alerts configured (service down)
- [ ] **Required**: P2 alerts configured (degraded performance)
- [ ] **Required**: Alert routing configured (PagerDuty/Opsgenie)
- [ ] **Required**: Runbooks linked to alerts
- [ ] Alert noise evaluated and tuned

---

## Operations

### Documentation

- [ ] **Required**: Deployment procedure documented
- [ ] **Required**: Rollback procedure documented
- [ ] **Required**: On-call runbooks available
- [ ] **Required**: Escalation paths defined
- [ ] Architecture diagrams current

### Disaster Recovery

- [ ] **Required**: RTO/RPO documented
- [ ] **Required**: Backup strategy tested
- [ ] **Required**: Restore procedure tested
- [ ] **Required**: DR runbook available
- [ ] Cross-region failover tested (if applicable)

### Change Management

- [ ] **Required**: Deployment window communicated
- [ ] **Required**: Stakeholders notified
- [ ] **Required**: Rollback plan approved
- [ ] Change request approved (if required)

---

## Testing

### Functional

- [ ] **Required**: Integration tests passed
- [ ] **Required**: E2E tests passed
- [ ] **Required**: Contract tests passed
- [ ] Manual smoke test completed

### Performance

- [ ] **Required**: Load test completed
- [ ] **Required**: Performance meets SLOs
- [ ] Stress test completed
- [ ] Baseline established

### Security

- [ ] **Required**: Dependency scan completed
- [ ] **Required**: No critical vulnerabilities
- [ ] SAST scan completed
- [ ] DAST scan completed (if applicable)

---

## Post-Deployment

### Verification

- [ ] Health endpoints returning 200
- [ ] Logs showing expected behavior
- [ ] Metrics being collected
- [ ] Traces being collected
- [ ] No errors in first 15 minutes

### Communication

- [ ] Deployment status communicated
- [ ] Release notes published
- [ ] Known issues documented

---

## Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Engineering Lead | | | |
| SRE/DevOps Lead | | | |
| Security | | | |
| Product Owner | | | |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Docker Deployment](docker.md) | Docker setup |
| [Kubernetes Deployment](kubernetes.md) | K8s deployment |
| [Disaster Recovery](../runbooks/disaster-recovery.md) | DR procedures |

