# BUTTERFLY Capacity Planning

> Resource planning and sizing guidelines for BUTTERFLY deployments

**Last Updated**: 2025-12-03  
**Target Audience**: Platform architects, SREs

---

## Overview

This guide provides capacity planning guidelines for sizing BUTTERFLY deployments based on expected workload.

---

## Workload Profiles

### Small (Development/Staging)

| Characteristic | Value |
|----------------|-------|
| Events/second | < 100 |
| Entities tracked | < 10,000 |
| Concurrent users | < 50 |
| Data retention | 30 days |

### Medium (Production)

| Characteristic | Value |
|----------------|-------|
| Events/second | 100 - 1,000 |
| Entities tracked | 10,000 - 100,000 |
| Concurrent users | 50 - 500 |
| Data retention | 90 days |

### Large (Enterprise)

| Characteristic | Value |
|----------------|-------|
| Events/second | 1,000 - 10,000 |
| Entities tracked | 100,000 - 1,000,000 |
| Concurrent users | 500 - 5,000 |
| Data retention | 1 year |

---

## Service Sizing

### CAPSULE

| Profile | Replicas | CPU | Memory | Notes |
|---------|----------|-----|--------|-------|
| Small | 1 | 1 core | 2 GB | Single instance OK |
| Medium | 2 | 2 cores | 4 GB | HA pair |
| Large | 3-5 | 4 cores | 8 GB | Scale with write load |

**Key Drivers:**
- Capsule creation rate
- Query complexity
- Time range queries

### ODYSSEY

| Profile | Replicas | CPU | Memory | Notes |
|---------|----------|-----|--------|-------|
| Small | 1 | 1 core | 2 GB | |
| Medium | 2 | 2 cores | 4 GB | HA pair |
| Large | 3-5 | 4 cores | 8 GB | Scale with graph size |

**Key Drivers:**
- Graph size (nodes + edges)
- Traversal depth
- Query complexity

### PERCEPTION

| Profile | Replicas | CPU | Memory | Notes |
|---------|----------|-----|--------|-------|
| Small | 1 | 2 cores | 4 GB | Stream processing needs CPU |
| Medium | 3 | 4 cores | 8 GB | Parallel processing |
| Large | 5-10 | 8 cores | 16 GB | Scale with event rate |

**Key Drivers:**
- Event ingestion rate
- Signal processing complexity
- Concurrent data sources

### PLATO

| Profile | Replicas | CPU | Memory | Notes |
|---------|----------|-----|--------|-------|
| Small | 1 | 2 cores | 4 GB | Engine processing |
| Medium | 2 | 4 cores | 8 GB | |
| Large | 3-5 | 8 cores | 16 GB | Scale with plan complexity |

**Key Drivers:**
- Concurrent plan executions
- Plan complexity
- Engine utilization

### NEXUS

| Profile | Replicas | CPU | Memory | Notes |
|---------|----------|-----|--------|-------|
| Small | 1 | 1 core | 2 GB | Gateway overhead low |
| Medium | 3 | 2 cores | 4 GB | HA + load distribution |
| Large | 5-10 | 2 cores | 4 GB | Scale horizontally |

**Key Drivers:**
- Request rate
- Concurrent connections
- Response aggregation complexity

---

## Infrastructure Sizing

### Cassandra

| Profile | Nodes | CPU/Node | Memory/Node | Storage/Node |
|---------|-------|----------|-------------|--------------|
| Small | 1 | 2 cores | 8 GB | 100 GB SSD |
| Medium | 3 | 4 cores | 16 GB | 500 GB SSD |
| Large | 5-9 | 8 cores | 32 GB | 1 TB NVMe |

**Sizing Formula:**
```
Storage = (daily_writes * avg_row_size * retention_days * RF) * 1.5
Memory = storage_per_node / 4 (for caching)
```

### PostgreSQL

| Profile | Type | CPU | Memory | Storage |
|---------|------|-----|--------|---------|
| Small | Single | 2 cores | 4 GB | 50 GB SSD |
| Medium | Primary + Replica | 4 cores | 8 GB | 200 GB SSD |
| Large | Primary + 2 Replicas | 8 cores | 16 GB | 500 GB NVMe |

**Sizing Formula:**
```
Storage = (rows * avg_row_size) + indexes + WAL
Memory = (active_dataset * 1.2) + (connections * 10 MB)
```

### Redis

| Profile | Type | Memory | Notes |
|---------|------|--------|-------|
| Small | Single | 2 GB | Development only |
| Medium | Sentinel (3 nodes) | 4 GB/node | HA |
| Large | Cluster (6+ nodes) | 8 GB/node | Sharding |

**Sizing Formula:**
```
Memory = (num_keys * avg_key_size) + (num_keys * avg_value_size) + overhead
```

### JanusGraph

| Profile | Nodes | CPU/Node | Memory/Node | Storage |
|---------|-------|----------|-------------|---------|
| Small | 1 | 2 cores | 4 GB | 50 GB |
| Medium | 3 | 4 cores | 8 GB | 200 GB |
| Large | 5+ | 8 cores | 16 GB | 500 GB |

**Sizing Formula:**
```
Vertices: ~300 bytes + properties
Edges: ~100 bytes + properties
Indexes: ~50 bytes per indexed property
```

### Kafka

| Profile | Brokers | CPU/Broker | Memory/Broker | Storage/Broker |
|---------|---------|------------|---------------|----------------|
| Small | 1 | 2 cores | 4 GB | 100 GB |
| Medium | 3 | 4 cores | 8 GB | 500 GB |
| Large | 5+ | 8 cores | 16 GB | 2 TB |

**Sizing Formula:**
```
Storage = (message_rate * avg_message_size * retention_seconds * RF) / brokers
Memory = (num_partitions * segment_size) + OS_cache
```

### ClickHouse

| Profile | Nodes | CPU/Node | Memory/Node | Storage/Node |
|---------|-------|----------|-------------|--------------|
| Small | 1 | 4 cores | 16 GB | 200 GB SSD |
| Medium | 3 | 8 cores | 32 GB | 1 TB SSD |
| Large | 6+ | 16 cores | 64 GB | 4 TB NVMe |

**Sizing Formula:**
```
Storage = (raw_data * compression_ratio * retention)
Memory = (concurrent_queries * query_memory) + cache
```

---

## Network Capacity

### Bandwidth Requirements

| Profile | Internal | External |
|---------|----------|----------|
| Small | 100 Mbps | 10 Mbps |
| Medium | 1 Gbps | 100 Mbps |
| Large | 10 Gbps | 1 Gbps |

### Connection Estimates

| Component | Connections/Service |
|-----------|---------------------|
| Cassandra | 50-100 |
| PostgreSQL | 20-50 |
| Redis | 50-100 |
| Kafka | 10-20 |

---

## Scaling Triggers

### Horizontal Scaling Indicators

| Metric | Threshold | Action |
|--------|-----------|--------|
| CPU > 70% sustained | 5 minutes | Add replica |
| Memory > 80% | 10 minutes | Add replica |
| Request latency P99 > 500ms | 5 minutes | Add replica |
| Queue depth growing | 5 minutes | Add replica |

### Vertical Scaling Indicators

| Metric | Threshold | Action |
|--------|-----------|--------|
| OOM events | Any | Increase memory |
| CPU throttling | Frequent | Increase CPU |
| Disk I/O wait > 20% | Sustained | Upgrade storage |

---

## Capacity Planning Checklist

- [ ] Define workload profile (Small/Medium/Large)
- [ ] Estimate peak event rate
- [ ] Calculate storage requirements
- [ ] Size database clusters
- [ ] Size Kafka cluster
- [ ] Calculate network bandwidth
- [ ] Plan for 2x growth headroom
- [ ] Document scaling thresholds
- [ ] Configure autoscaling rules
- [ ] Test with load simulation

---

## Cost Estimation

### Cloud Cost Factors

```
Monthly Cost ≈ 
  (Compute × hours × rate) +
  (Storage × GB × rate) +
  (Network egress × GB × rate) +
  (Managed services)
```

### Sample Monthly Costs (AWS)

| Profile | Compute | Storage | Total |
|---------|---------|---------|-------|
| Small | $500 | $100 | ~$600 |
| Medium | $2,000 | $500 | ~$2,500 |
| Large | $10,000 | $2,000 | ~$12,000 |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Operations Overview](README.md) | Operations index |
| [Scaling Runbook](runbooks/scaling.md) | Scaling procedures |
| [Monitoring](monitoring/README.md) | Observability |

