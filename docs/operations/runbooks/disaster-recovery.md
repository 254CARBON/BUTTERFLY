# Disaster Recovery Runbook

> Procedures for recovering from catastrophic failures

**Last Updated**: 2025-12-03  
**Target Audience**: SREs, Platform engineers

---

## Overview

This runbook covers disaster recovery procedures for major infrastructure failures affecting BUTTERFLY.

---

## Recovery Objectives

| Objective | Target | Notes |
|-----------|--------|-------|
| **RTO** (Recovery Time Objective) | 4 hours | Time to restore service |
| **RPO** (Recovery Point Objective) | 1 hour | Maximum data loss |

---

## Disaster Scenarios

| Scenario | Severity | RTO | Procedure |
|----------|----------|-----|-----------|
| Single service failure | Medium | 15 min | [Service Recovery](#service-recovery) |
| Database failure | High | 1 hour | [Database Recovery](#database-recovery) |
| Kafka cluster failure | High | 30 min | [Kafka Recovery](#kafka-recovery) |
| Complete region failure | Critical | 4 hours | [Region Failover](#region-failover) |
| Data corruption | Critical | 2 hours | [Data Restoration](#data-restoration) |

---

## Prerequisites

### Access Requirements

- [ ] Kubernetes cluster admin access
- [ ] Database admin credentials
- [ ] Backup storage access (S3/GCS)
- [ ] DR region access

### Tools Required

```bash
# Verify tools installed
kubectl version
aws s3 ls s3://butterfly-backups/
cqlsh --version
psql --version
```

---

## Service Recovery

### Single Service Down

```bash
# 1. Check service status
kubectl get pods -n butterfly -l app=[service]

# 2. Check events
kubectl describe pod -n butterfly -l app=[service]

# 3. Try restart
kubectl rollout restart deployment/[service] -n butterfly

# 4. If persistent, rollback
kubectl rollout undo deployment/[service] -n butterfly

# 5. Verify recovery
curl http://[service]:8080/actuator/health
```

### All Services Down

```bash
# 1. Check node status
kubectl get nodes

# 2. Check infrastructure
kubectl get pods -n butterfly
kubectl get events -n butterfly --sort-by=.lastTimestamp

# 3. Restart all services (in order)
kubectl rollout restart deployment/cassandra -n butterfly
sleep 60  # Wait for Cassandra
kubectl rollout restart deployment/kafka -n butterfly
sleep 30  # Wait for Kafka
kubectl rollout restart deployment/capsule -n butterfly
kubectl rollout restart deployment/odyssey -n butterfly
kubectl rollout restart deployment/perception -n butterfly
kubectl rollout restart deployment/plato -n butterfly
kubectl rollout restart deployment/nexus -n butterfly

# 4. Verify all healthy
kubectl get pods -n butterfly
```

---

## Database Recovery

### Cassandra Recovery

#### Single Node Failure

```bash
# 1. Check cluster status
kubectl exec -it cassandra-0 -n butterfly -- nodetool status

# 2. If node is down, it should auto-recover
# Wait 10 minutes for streaming to complete

# 3. If node won't recover, replace it
kubectl delete pod cassandra-[X] -n butterfly
# StatefulSet will recreate, streaming will repair
```

#### Full Cluster Restore from Backup

```bash
# 1. List available backups
aws s3 ls s3://butterfly-backups/cassandra/

# 2. Download latest backup
aws s3 sync s3://butterfly-backups/cassandra/[date]/ /tmp/cassandra-backup/

# 3. Stop all services using Cassandra
kubectl scale deployment/capsule --replicas=0 -n butterfly
kubectl scale deployment/plato --replicas=0 -n butterfly

# 4. Scale down Cassandra
kubectl scale statefulset/cassandra --replicas=0 -n butterfly

# 5. Clear data volumes (DESTRUCTIVE)
kubectl delete pvc -l app=cassandra -n butterfly

# 6. Scale up Cassandra
kubectl scale statefulset/cassandra --replicas=3 -n butterfly
kubectl wait --for=condition=ready pod/cassandra-0 -n butterfly --timeout=300s

# 7. Restore schema
kubectl exec -it cassandra-0 -n butterfly -- cqlsh -f /tmp/schema.cql

# 8. Restore data using sstableloader
kubectl cp /tmp/cassandra-backup/ cassandra-0:/tmp/restore -n butterfly
kubectl exec -it cassandra-0 -n butterfly -- sstableloader \
  -d cassandra-0.cassandra.butterfly.svc.cluster.local \
  /tmp/restore/[keyspace]/[table]

# 9. Restart services
kubectl scale deployment/capsule --replicas=2 -n butterfly
kubectl scale deployment/plato --replicas=2 -n butterfly
```

### PostgreSQL Recovery

#### Primary Failure with Replica

```bash
# 1. Promote replica to primary
kubectl exec -it postgres-replica-0 -n butterfly -- \
  pg_ctl promote -D /var/lib/postgresql/data

# 2. Update connection strings
kubectl set env deployment/perception \
  POSTGRES_HOST=postgres-replica-0.postgres.butterfly.svc.cluster.local \
  -n butterfly

# 3. Verify connections
kubectl logs -n butterfly -l app=perception | grep -i postgres
```

#### Full Restore from Backup

```bash
# 1. List backups
aws s3 ls s3://butterfly-backups/postgres/

# 2. Download backup
aws s3 cp s3://butterfly-backups/postgres/[date]/backup.sql.gz /tmp/

# 3. Stop services
kubectl scale deployment/perception --replicas=0 -n butterfly

# 4. Restore
gunzip -c /tmp/backup.sql.gz | kubectl exec -i postgres-0 -n butterfly -- \
  psql -U butterfly perception

# 5. Restart services
kubectl scale deployment/perception --replicas=3 -n butterfly
```

### Redis Recovery

#### Sentinel Failover (Automatic)

```bash
# 1. Check sentinel status
kubectl exec -it redis-sentinel-0 -n butterfly -- redis-cli -p 26379 sentinel masters

# 2. If automatic failover didn't occur, force it
kubectl exec -it redis-sentinel-0 -n butterfly -- redis-cli -p 26379 \
  sentinel failover mymaster
```

#### Full Restore

```bash
# 1. Download RDB backup
aws s3 cp s3://butterfly-backups/redis/dump.rdb /tmp/

# 2. Stop Redis
kubectl scale statefulset/redis --replicas=0 -n butterfly

# 3. Restore RDB file
kubectl cp /tmp/dump.rdb redis-0:/data/dump.rdb -n butterfly

# 4. Start Redis
kubectl scale statefulset/redis --replicas=3 -n butterfly
```

---

## Kafka Recovery

### Single Broker Failure

```bash
# 1. Check broker status
kubectl exec -it kafka-0 -n butterfly -- kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092

# 2. If broker is down, restart
kubectl delete pod kafka-[X] -n butterfly

# 3. Verify partition leadership
kubectl exec -it kafka-0 -n butterfly -- kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic rim.fast-path
```

### Full Cluster Recovery

```bash
# 1. Stop all consumers
kubectl scale deployment/perception --replicas=0 -n butterfly
kubectl scale deployment/capsule --replicas=0 -n butterfly

# 2. Scale down Kafka
kubectl scale statefulset/kafka --replicas=0 -n butterfly

# 3. Clear corrupted data if needed
kubectl delete pvc -l app=kafka -n butterfly

# 4. Scale up Kafka
kubectl scale statefulset/kafka --replicas=3 -n butterfly
kubectl wait --for=condition=ready pod/kafka-0 -n butterfly --timeout=300s

# 5. Recreate topics
kubectl exec -it kafka-0 -n butterfly -- kafka-topics.sh \
  --create --topic rim.fast-path \
  --partitions 12 --replication-factor 3 \
  --bootstrap-server localhost:9092

# 6. Restart consumers
kubectl scale deployment/perception --replicas=3 -n butterfly
kubectl scale deployment/capsule --replicas=2 -n butterfly
```

---

## Region Failover

### Activate DR Region

```bash
# 1. Update DNS to point to DR region
# This is typically done via your DNS provider's interface or API

# 2. Verify DR cluster is healthy
kubectl --context=dr-cluster get pods -n butterfly

# 3. Update client configurations if needed
kubectl --context=dr-cluster set env deployment/nexus \
  REGION=dr \
  -n butterfly

# 4. Verify data replication status
# Check Cassandra consistency
kubectl --context=dr-cluster exec -it cassandra-0 -n butterfly -- \
  nodetool describecluster

# 5. Announce failover
echo "Region failover complete. DR region is now primary."
```

### Failback to Primary

```bash
# 1. Ensure primary region is recovered
kubectl --context=primary-cluster get nodes
kubectl --context=primary-cluster get pods -n butterfly

# 2. Resync data from DR to primary
# (Use database-specific replication)

# 3. Update DNS back to primary
# Typically via DNS provider

# 4. Monitor for issues
# Watch error rates in Grafana
```

---

## Data Restoration

### Point-in-Time Recovery

```bash
# 1. Identify target restore point
aws s3 ls s3://butterfly-backups/cassandra/ --recursive | grep [date]

# 2. Follow full cluster restore procedure above
# with the selected backup

# 3. Replay Kafka messages from offset
# (if message replay is needed)
kubectl exec -it kafka-0 -n butterfly -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group [consumer-group] \
  --reset-offsets --to-datetime [timestamp] \
  --topic rim.fast-path --execute
```

---

## Verification Checklist

After any DR procedure:

- [ ] All pods running and healthy
- [ ] Health endpoints returning 200
- [ ] Metrics flowing to Prometheus
- [ ] Logs appearing in aggregator
- [ ] Sample API requests succeeding
- [ ] Consumer lag not increasing
- [ ] No error spikes in dashboards

---

## Post-Recovery Actions

1. **Document the incident** with timeline
2. **Schedule post-mortem** within 48 hours
3. **Update runbooks** if procedures need refinement
4. **Review and test backups** to prevent recurrence
5. **Communicate to stakeholders** the status

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Incident Response](incident-response.md) | Incident handling |
| [Runbooks Index](README.md) | All runbooks |
| [Production Checklist](../deployment/production-checklist.md) | Verification |

