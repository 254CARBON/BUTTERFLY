# Common Issues Troubleshooting

> Solutions for frequently encountered problems

**Last Updated**: 2025-12-03  
**Target Audience**: On-call engineers, developers

---

## Overview

This guide covers common issues and their solutions for BUTTERFLY services.

---

## Quick Diagnosis

### Health Check Commands

```bash
# All services health
for svc in capsule odyssey perception plato nexus; do
  echo "=== $svc ==="
  curl -s "http://${svc}:8080/actuator/health" | jq '.status'
done

# Pod status
kubectl get pods -n butterfly -o wide

# Recent events
kubectl get events -n butterfly --sort-by=.lastTimestamp | tail -20

# Resource usage
kubectl top pods -n butterfly
```

---

## Service Issues

### Service Not Starting

#### Symptoms
- Pod in `CrashLoopBackOff` or `Error` state
- Health check failing

#### Diagnosis

```bash
# Check pod status
kubectl describe pod [pod-name] -n butterfly

# Check logs
kubectl logs [pod-name] -n butterfly --previous

# Check events
kubectl get events -n butterfly --field-selector involvedObject.name=[pod-name]
```

#### Common Causes & Solutions

| Cause | Solution |
|-------|----------|
| Database unreachable | Check database connectivity, credentials |
| Out of memory | Increase memory limits, check for leaks |
| Missing config | Verify ConfigMap/Secret mounted |
| Port conflict | Check for duplicate services |

### High Latency

#### Symptoms
- P99 latency > 500ms
- Slow API responses

#### Diagnosis

```bash
# Check service metrics
curl -s http://[service]:8080/actuator/prometheus | grep http_server_requests

# Check downstream latency
curl -s http://nexus:8083/actuator/metrics/http.client.requests | jq

# Check database latency
# Grafana: Database panel
```

#### Solutions

| Cause | Solution |
|-------|----------|
| Database slow | Check indexes, query optimization |
| JVM GC pressure | Increase heap, tune GC |
| Network issues | Check inter-pod connectivity |
| Resource contention | Scale horizontally |

### High Error Rate

#### Symptoms
- 5xx errors increasing
- Error alerts firing

#### Diagnosis

```bash
# Check error logs
kubectl logs -n butterfly -l app=[service] | grep -i error | tail -50

# Check error metrics
curl -s http://[service]:8080/actuator/prometheus | grep "status=\"5"

# Check exception breakdown
# Grafana: Error breakdown panel
```

#### Solutions

| Cause | Solution |
|-------|----------|
| Downstream failure | Check circuit breaker, dependency health |
| Resource exhaustion | Scale up/out, check connections |
| Bad deployment | Rollback to previous version |
| Data issue | Check input validation, data quality |

---

## Database Issues

### Cassandra

#### Connection Failures

```bash
# Check Cassandra status
kubectl exec -it cassandra-0 -n butterfly -- nodetool status

# Check from service
kubectl logs -n butterfly -l app=capsule | grep -i cassandra

# Test connectivity
kubectl exec -it capsule-xxx -n butterfly -- nc -zv cassandra 9042
```

**Solutions:**
- Restart Cassandra nodes if unresponsive
- Check network policies
- Verify credentials in secrets

#### Timeout Errors

```bash
# Check compaction status
kubectl exec -it cassandra-0 -n butterfly -- nodetool compactionstats

# Check pending tasks
kubectl exec -it cassandra-0 -n butterfly -- nodetool tpstats
```

**Solutions:**
- Wait for compaction to complete
- Tune read/write timeouts
- Add nodes if consistently overloaded

### PostgreSQL

#### Connection Pool Exhausted

```bash
# Check active connections
kubectl exec -it postgres-0 -n butterfly -- psql -U butterfly -c \
  "SELECT count(*) FROM pg_stat_activity WHERE state = 'active'"

# Check waiting connections
kubectl exec -it postgres-0 -n butterfly -- psql -U butterfly -c \
  "SELECT count(*) FROM pg_stat_activity WHERE wait_event IS NOT NULL"
```

**Solutions:**
- Increase connection pool size
- Add PgBouncer for connection pooling
- Check for connection leaks in application

#### Slow Queries

```bash
# Find slow queries
kubectl exec -it postgres-0 -n butterfly -- psql -U butterfly -c \
  "SELECT query, calls, mean_time FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 10"
```

**Solutions:**
- Add missing indexes
- Optimize query plans
- Increase work_mem for complex queries

### Redis

#### Memory Full

```bash
# Check memory usage
kubectl exec -it redis-0 -n butterfly -- redis-cli info memory

# Check eviction stats
kubectl exec -it redis-0 -n butterfly -- redis-cli info stats | grep evicted
```

**Solutions:**
- Set appropriate maxmemory and eviction policy
- Clear unnecessary keys
- Scale Redis cluster

---

## Kafka Issues

### Consumer Lag

#### Symptoms
- High consumer lag metrics
- Processing falling behind

#### Diagnosis

```bash
# Check consumer groups
kubectl exec -it kafka-0 -n butterfly -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups

# Check topic details
kubectl exec -it kafka-0 -n butterfly -- kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic rim.fast-path
```

#### Solutions

| Cause | Solution |
|-------|----------|
| Slow processing | Optimize consumer logic |
| Too few consumers | Scale consumer replicas |
| Unbalanced partitions | Rebalance partitions |
| Network issues | Check connectivity |

### Producer Errors

```bash
# Check producer metrics
curl -s http://[service]:8080/actuator/prometheus | grep kafka_producer

# Check broker logs
kubectl logs -n butterfly kafka-0 | grep -i error | tail -20
```

**Solutions:**
- Check broker health
- Increase producer timeouts
- Check for full disks

---

## JVM Issues

### Out of Memory

#### Symptoms
- OOMKilled pods
- `java.lang.OutOfMemoryError`

#### Diagnosis

```bash
# Check memory metrics
curl -s http://[service]:8080/actuator/metrics/jvm.memory.used | jq

# Get heap dump (if pod still running)
kubectl exec -it [pod] -n butterfly -- \
  jcmd 1 GC.heap_dump /tmp/heapdump.hprof
kubectl cp [pod]:/tmp/heapdump.hprof ./heapdump.hprof -n butterfly
```

#### Solutions

| Cause | Solution |
|-------|----------|
| Heap too small | Increase -Xmx |
| Memory leak | Analyze heap dump, fix leak |
| Too many objects | Review object creation, caching |

### Frequent GC Pauses

#### Symptoms
- High GC pause times
- Inconsistent latency

#### Diagnosis

```bash
# Check GC metrics
curl -s http://[service]:8080/actuator/prometheus | grep jvm_gc

# Enable GC logging
# Add to JAVA_OPTS: -Xlog:gc*:file=/tmp/gc.log
```

#### Solutions
- Tune GC parameters
- Switch to G1GC or ZGC
- Reduce object allocation rate
- Increase heap size

---

## Network Issues

### Connection Timeouts

```bash
# Check network connectivity
kubectl exec -it [pod] -n butterfly -- nc -zv [target-service] [port]

# Check DNS resolution
kubectl exec -it [pod] -n butterfly -- nslookup [service].butterfly.svc.cluster.local

# Check network policies
kubectl get networkpolicies -n butterfly
```

**Solutions:**
- Verify network policies allow traffic
- Check service DNS entries
- Verify target service is running

### Circuit Breaker Open

```bash
# Check circuit breaker status
curl -s http://nexus:8083/actuator/circuitbreakers | jq

# Check circuit breaker metrics
curl -s http://nexus:8083/actuator/prometheus | grep resilience4j_circuitbreaker
```

**Solutions:**
- Fix underlying service issue
- Manually reset circuit breaker (temporary):
  ```bash
  curl -X POST http://nexus:8083/actuator/circuitbreakers/[name]/reset
  ```
- Tune circuit breaker thresholds

---

## Authentication Issues

### JWT Validation Failures

```bash
# Check auth logs
kubectl logs -n butterfly -l app=nexus | grep -i "jwt\|auth\|token" | tail -50
```

**Solutions:**
- Verify JWT secret matches across services
- Check token expiration
- Validate token issuer/audience

### API Key Rejected

**Solutions:**
- Verify API key is active
- Check rate limits not exceeded
- Confirm key has required scopes

---

## Quick Fixes

### Restart Service

```bash
kubectl rollout restart deployment/[service] -n butterfly
```

### Rollback Deployment

```bash
kubectl rollout undo deployment/[service] -n butterfly
```

### Clear Redis Cache

```bash
kubectl exec -it redis-0 -n butterfly -- redis-cli FLUSHDB
```

### Reset Kafka Consumer Offset

```bash
kubectl exec -it kafka-0 -n butterfly -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group [consumer-group] \
  --reset-offsets --to-latest \
  --topic [topic] --execute
```

---

## Escalation Triggers

Escalate to senior engineer if:

- Issue persists > 30 minutes after standard remediation
- Root cause unclear after diagnosis
- Data integrity concerns
- Multiple services affected
- Customer-visible P1/P2 impact

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Incident Response](incident-response.md) | Incident handling |
| [Scaling](scaling.md) | Scaling procedures |
| [Monitoring](../monitoring/README.md) | Observability |

