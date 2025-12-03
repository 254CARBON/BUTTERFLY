# Failure-Mode Catalog

> Comprehensive catalog of critical failure modes, impacts, and recovery targets for the BUTTERFLY platform

**Last Updated**: 2025-12-03  
**Target Audience**: SREs, Platform engineers, On-call responders  
**Related**: [Disaster Recovery](runbooks/disaster-recovery.md) | [Chaos Experiments](../../chaos/README.md)

---

## Overview

This document catalogs all critical failure modes for the BUTTERFLY platform, their expected impacts, detection mechanisms, and recovery targets. Each failure mode has an associated chaos experiment for validation.

---

## Recovery Objectives Summary

| Objective | Target | Validation Method |
|-----------|--------|-------------------|
| **RTO** (Recovery Time Objective) | < 1 hour | DR drills |
| **RPO** (Recovery Point Objective) | < 15 minutes | Backup frequency |
| **Chaos Recovery** | < 60 seconds | Automated chaos suite |

---

## Failure Mode Categories

### 1. Kafka Failures

#### FM-KAFKA-001: Single Broker Loss

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Impact** | Degraded throughput, temporary consumer rebalance |
| **Recovery Target** | 30 seconds (automatic) |
| **Detection** | `kafka_server_replica_manager_under_replicated_partitions > 0` |
| **Chaos Experiment** | `kafka-broker-loss.yaml` |

**Symptoms:**
- Under-replicated partitions alert fires
- Consumer group rebalancing events
- Temporary increase in produce latency

**Mitigation:**
- Kafka configured with `min.insync.replicas=2` and `replication.factor=3`
- Consumers configured with `session.timeout.ms=10000`
- Automatic partition reassignment

**Recovery Steps:**
1. Monitor partition rebalance completion
2. Verify consumer lag returns to normal
3. If broker doesn't return, replace node

---

#### FM-KAFKA-002: Full Cluster Failure

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Impact** | All event-driven services unavailable |
| **Recovery Target** | 10 minutes (manual) |
| **Detection** | `kafka_controller_active_controller_count == 0` |
| **Chaos Experiment** | `kafka-cluster-failure.yaml` |

**Symptoms:**
- All Kafka health checks fail
- Services report Kafka connection errors
- No controller elected

**Mitigation:**
- Multi-AZ deployment
- Automated failover to DR region
- DLQ patterns preserve failed messages

**Recovery Steps:**
1. Attempt cluster restart in primary region
2. If unrecoverable, initiate DR failover
3. Replay from last known offsets
4. See [DR Runbook - Kafka Recovery](runbooks/disaster-recovery.md#kafka-recovery)

---

### 2. Database Failures

#### FM-CASS-001: Cassandra Single Node Loss

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Impact** | Reduced redundancy, auto-repair initiated |
| **Recovery Target** | 5 minutes (automatic) |
| **Detection** | `cassandra_node_status{status="down"} > 0` |
| **Chaos Experiment** | `cassandra-node-failure.yaml` |

**Symptoms:**
- Nodetool status shows node DN (Down/Normal)
- Hints accumulating on other nodes
- Slightly elevated read/write latencies

**Mitigation:**
- RF=3 across all keyspaces
- Hinted handoff enabled
- Automatic streaming on node recovery

**Recovery Steps:**
1. Monitor automatic repair via `nodetool status`
2. If node doesn't return in 10 minutes, investigate
3. Replace node if hardware failure confirmed

---

#### FM-CASS-002: Cassandra Quorum Loss

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Impact** | CAPSULE reads/writes fail |
| **Recovery Target** | 5 minutes (manual) |
| **Detection** | `cassandra_node_status{status="up"} < 2` |
| **Chaos Experiment** | `cassandra-quorum-loss.yaml` |

**Symptoms:**
- CAPSULE health checks failing
- `NoHostAvailableException` in logs
- WriteTimeoutException / ReadTimeoutException

**Mitigation:**
- Minimum 3-node cluster
- Cross-rack awareness
- Automatic gossip-based failure detection

**Recovery Steps:**
1. Identify failed nodes via `nodetool status`
2. Attempt restart of down nodes
3. If unrecoverable, restore from snapshot
4. See [DR Runbook - Cassandra Recovery](runbooks/disaster-recovery.md#cassandra-recovery)

---

#### FM-PG-001: PostgreSQL Primary Failure

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Impact** | PERCEPTION writes fail until failover |
| **Recovery Target** | 60 seconds (automatic with streaming replication) |
| **Detection** | `pg_up{instance="primary"} == 0` |
| **Chaos Experiment** | `postgres-primary-failure.yaml` |

**Symptoms:**
- PERCEPTION health check fails
- Connection refused errors
- Replica promotion in progress

**Mitigation:**
- Streaming replication with synchronous commit
- Patroni/pgpool for automatic failover
- Connection pooling with PgBouncer

**Recovery Steps:**
1. Verify replica promoted to primary
2. Update connection strings if manual failover
3. Investigate failed primary
4. Rebuild replica from new primary

---

#### FM-PG-002: PostgreSQL Data Corruption

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Impact** | Data integrity compromised |
| **Recovery Target** | 30 minutes (PITR restore) |
| **Detection** | `pg_stat_database_checksum_failures > 0` |
| **Chaos Experiment** | N/A (use backup validation) |

**Symptoms:**
- Checksum failures in logs
- Query result inconsistencies
- Index corruption errors

**Mitigation:**
- Data checksums enabled
- Continuous WAL archiving (15-minute RPO)
- Regular backup validation

**Recovery Steps:**
1. Stop affected service
2. Perform point-in-time recovery
3. Validate data integrity
4. Resume service
5. See [DR Runbook - Data Restoration](runbooks/disaster-recovery.md#data-restoration)

---

### 3. Cache Failures

#### FM-REDIS-001: Redis Sentinel Failover

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Impact** | Brief latency spike during failover |
| **Recovery Target** | 10 seconds (automatic) |
| **Detection** | `redis_sentinel_master_status{status="odown"} == 1` |
| **Chaos Experiment** | `redis-failover.yaml` |

**Symptoms:**
- Sentinel ODOWN notification
- Brief connection errors
- Cache miss spike

**Mitigation:**
- Sentinel with 3+ nodes
- Client retry with backoff
- Circuit breaker on cache operations

**Recovery Steps:**
1. Sentinel handles automatically
2. Monitor new master election
3. Verify replica sync

---

#### FM-REDIS-002: Redis Memory Exhaustion

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Impact** | Cache evictions, degraded performance |
| **Recovery Target** | Immediate (graceful degradation) |
| **Detection** | `redis_memory_used_bytes / redis_memory_max_bytes > 0.9` |
| **Chaos Experiment** | `redis-memory-pressure.yaml` |

**Symptoms:**
- High eviction rate
- Increased cache misses
- Slower response times

**Mitigation:**
- maxmemory-policy=volatile-lru
- Application fallback to database
- Alerting at 80% memory

**Recovery Steps:**
1. Identify memory consumers via `redis-cli memory doctor`
2. Flush non-critical caches if needed
3. Scale Redis memory or add nodes

---

#### FM-IGNITE-001: Ignite Grid Partition

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Impact** | Data inconsistency, split-brain risk |
| **Recovery Target** | 30 seconds (automatic healing) |
| **Detection** | `ignite_cluster_nodes < expected_count` |
| **Chaos Experiment** | `ignite-partition.yaml` |

**Symptoms:**
- Cluster topology change events
- PartitionLossPolicy triggered
- Inconsistent reads across partitions

**Mitigation:**
- Baseline topology configured
- IGNORE partition loss policy for cache operations
- Automatic rebalancing enabled

**Recovery Steps:**
1. Verify network connectivity restored
2. Monitor partition rebalancing
3. Validate data consistency
4. If split-brain, follow merge procedure

---

### 4. Network Failures

#### FM-NET-001: Cross-Service Network Partition

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Impact** | Circuit breakers trip, graceful degradation |
| **Recovery Target** | 30 seconds (circuit breaker) |
| **Detection** | `resilience4j_circuitbreaker_state{state="open"} == 1` |
| **Chaos Experiment** | `network-partition.yaml` |

**Symptoms:**
- Circuit breaker OPEN state
- Increased error rates between services
- Fallback responses returned

**Mitigation:**
- Resilience4j circuit breakers on all inter-service calls
- Fallback responses defined
- Bulkhead isolation

**Recovery Steps:**
1. Identify network issue source
2. Restore connectivity
3. Circuit breakers auto-recover in half-open state
4. Monitor error rates return to baseline

---

#### FM-NET-002: DNS Resolution Failure

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Impact** | All service discovery fails |
| **Recovery Target** | 60 seconds |
| **Detection** | `coredns_dns_request_count_total{rcode="SERVFAIL"}` spike |
| **Chaos Experiment** | `dns-failure.yaml` |

**Symptoms:**
- UnknownHostException in logs
- Service mesh routing failures
- External API calls fail

**Mitigation:**
- CoreDNS with multiple replicas
- DNS caching in applications
- Static fallback endpoints

**Recovery Steps:**
1. Check CoreDNS pod status
2. Restart CoreDNS if needed
3. Clear DNS caches
4. Verify resolution

---

### 5. Pod/Container Failures

#### FM-POD-001: Service Pod Crash

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Impact** | Temporary capacity reduction |
| **Recovery Target** | 45 seconds (HPA + restart) |
| **Detection** | `kube_pod_container_status_restarts_total` increase |
| **Chaos Experiment** | `pod-kill.yaml` |

**Symptoms:**
- Pod restart counter increments
- Brief availability dip
- Load shifted to remaining pods

**Mitigation:**
- Minimum 2 replicas per service
- PodDisruptionBudget configured
- HPA for automatic scaling

**Recovery Steps:**
1. Kubernetes auto-restarts pod
2. Verify pod reaches Ready state
3. Investigate crash cause from logs
4. Update OOMKilled limits if memory issue

---

#### FM-POD-002: CPU/Memory Exhaustion

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Impact** | Degraded performance, potential OOMKill |
| **Recovery Target** | 60 seconds (HPA scale-out) |
| **Detection** | `container_cpu_usage_seconds_total` near limit |
| **Chaos Experiment** | `pod-cpu-stress.yaml` |

**Symptoms:**
- High CPU throttling
- Increased response latency
- HPA scaling events

**Mitigation:**
- Resource requests/limits configured
- HPA with CPU/memory targets
- Bulkhead thread pool isolation

**Recovery Steps:**
1. HPA scales automatically
2. Monitor scaling effectiveness
3. Tune resource limits if needed
4. Investigate resource leak if recurring

---

### 6. External Dependency Failures

#### FM-EXT-001: External API Timeout

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Impact** | Affected features degrade gracefully |
| **Recovery Target** | Immediate (circuit breaker) |
| **Detection** | `http_client_requests_seconds_count{outcome="CLIENT_ERROR"}` |
| **Chaos Experiment** | `external-timeout.yaml` |

**Symptoms:**
- Increased latency on dependent operations
- Circuit breaker opens
- Fallback responses served

**Mitigation:**
- Timeout configuration per external call
- Circuit breaker with fallback
- Cached responses where applicable

**Recovery Steps:**
1. Circuit breaker handles automatically
2. Monitor external service status
3. Retry when circuit half-opens
4. Alert if extended outage

---

#### FM-EXT-002: Schema Registry Unavailable

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Impact** | Kafka serialization fails for new schemas |
| **Recovery Target** | 5 minutes |
| **Detection** | `kafka_schema_registry_up == 0` |
| **Chaos Experiment** | `schema-registry-failure.yaml` |

**Symptoms:**
- SerializationException in producers
- New message types cannot be sent
- Cached schemas continue working

**Mitigation:**
- Schema caching in producers/consumers
- Multi-replica Schema Registry
- Read-only fallback mode

**Recovery Steps:**
1. Restart Schema Registry pods
2. Verify schema cache intact
3. Test schema resolution
4. Replay failed messages from DLQ

---

## Failure Mode Priority Matrix

| Priority | Failure Modes | Chaos Test Frequency |
|----------|---------------|---------------------|
| P1 (Critical) | FM-KAFKA-002, FM-CASS-002, FM-PG-002, FM-NET-002 | Weekly |
| P2 (High) | FM-PG-001, FM-IGNITE-001, FM-NET-001, FM-EXT-002 | Weekly |
| P3 (Medium) | FM-KAFKA-001, FM-REDIS-002, FM-POD-002, FM-EXT-001 | Bi-weekly |
| P4 (Low) | FM-CASS-001, FM-REDIS-001, FM-POD-001 | Monthly |

---

## Chaos Experiment Mapping

| Experiment File | Failure Modes Tested | Pass Criteria |
|-----------------|---------------------|---------------|
| `kafka-broker-loss.yaml` | FM-KAFKA-001 | Recovery < 30s, no message loss |
| `kafka-cluster-failure.yaml` | FM-KAFKA-002 | DR failover < 10m |
| `cassandra-node-failure.yaml` | FM-CASS-001 | Read/write continuity |
| `cassandra-quorum-loss.yaml` | FM-CASS-002 | Recovery < 5m |
| `postgres-primary-failure.yaml` | FM-PG-001 | Failover < 60s |
| `redis-failover.yaml` | FM-REDIS-001 | Failover < 10s |
| `redis-memory-pressure.yaml` | FM-REDIS-002 | Graceful degradation |
| `ignite-partition.yaml` | FM-IGNITE-001 | Healing < 30s |
| `network-partition.yaml` | FM-NET-001 | Circuit breaker < 30s |
| `dns-failure.yaml` | FM-NET-002 | Recovery < 60s |
| `pod-kill.yaml` | FM-POD-001 | Restart < 45s |
| `pod-cpu-stress.yaml` | FM-POD-002 | HPA scale < 60s |
| `external-timeout.yaml` | FM-EXT-001 | Immediate fallback |
| `schema-registry-failure.yaml` | FM-EXT-002 | Recovery < 5m |

---

## Monitoring and Alerting

### Key Metrics for Failure Detection

```promql
# Kafka broker health
kafka_server_replica_manager_under_replicated_partitions > 0

# Cassandra cluster health
count(cassandra_node_status{status="up"}) < 2

# PostgreSQL replication lag
pg_replication_lag_seconds > 30

# Redis sentinel status
redis_sentinel_master_status{status="odown"} == 1

# Circuit breaker state
sum(resilience4j_circuitbreaker_state{state="open"}) > 0

# Pod restart rate
increase(kube_pod_container_status_restarts_total[5m]) > 3
```

### Alert Thresholds

| Alert | Threshold | Severity |
|-------|-----------|----------|
| KafkaUnderReplicated | > 0 for 5m | Warning |
| CassandraNodeDown | < 2 nodes for 2m | Critical |
| PostgresReplicationLag | > 60s | Critical |
| RedisFailover | odown for 30s | Warning |
| CircuitBreakerOpen | any open for 5m | Warning |
| HighPodRestarts | > 5 in 10m | Warning |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Disaster Recovery Runbook](runbooks/disaster-recovery.md) | Recovery procedures |
| [DLQ Operations](runbooks/dlq-operations.md) | Dead letter queue handling |
| [Incident Response](runbooks/incident-response.md) | Incident management |
| [Chaos Experiments](../../chaos/README.md) | Chaos test definitions |

