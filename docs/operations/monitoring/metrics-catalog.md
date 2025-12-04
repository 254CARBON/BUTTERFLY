# BUTTERFLY Metrics Catalog

> Complete reference of all metrics exposed by BUTTERFLY services

**Last Updated**: 2025-12-04  
**Target Audience**: SREs, DevOps engineers

---

## Overview

All BUTTERFLY services expose Prometheus metrics at `/actuator/prometheus`. This document catalogs all available metrics.

---

## Common Metrics (All Services)

### HTTP Request Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `http_server_requests_seconds` | Histogram | `method`, `uri`, `status`, `exception` | HTTP request duration |
| `http_server_requests_seconds_count` | Counter | `method`, `uri`, `status`, `exception` | Total request count |
| `http_server_requests_active` | Gauge | `method`, `uri` | Active requests |

### JVM Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `jvm_memory_used_bytes` | Gauge | `area`, `id` | Memory used |
| `jvm_memory_max_bytes` | Gauge | `area`, `id` | Memory max |
| `jvm_gc_pause_seconds` | Summary | `action`, `cause` | GC pause duration |
| `jvm_gc_memory_allocated_bytes_total` | Counter | | Memory allocated |
| `jvm_threads_live` | Gauge | | Live threads |
| `jvm_threads_daemon` | Gauge | | Daemon threads |
| `jvm_classes_loaded` | Gauge | | Loaded classes |

### Application Info

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `application_started_time_seconds` | Gauge | `application` | Startup time |
| `application_ready_time_seconds` | Gauge | `application` | Ready time |

### Process Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `process_cpu_usage` | Gauge | | CPU usage (0-1) |
| `process_uptime_seconds` | Gauge | | Process uptime |
| `process_files_open` | Gauge | | Open file descriptors |
| `process_files_max` | Gauge | | Max file descriptors |

---

## Service-Specific Metrics

### CAPSULE

#### Capsule Operations

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `capsule_created_total` | Counter | `vantage_mode`, `resolution` | Capsules created |
| `capsule_queries_total` | Counter | `vantage_mode`, `result` | Capsule queries |
| `capsule_query_duration_seconds` | Histogram | `vantage_mode` | Query latency |
| `capsule_size_bytes` | Histogram | `vantage_mode` | Capsule size |
| `capsule_ttl_seconds` | Gauge | `resolution` | Capsule TTL |

#### Time Travel

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `capsule_time_travel_requests_total` | Counter | `vantage_mode` | Time travel requests |
| `capsule_time_travel_duration_seconds` | Histogram | `vantage_mode` | Time travel latency |

#### Cache

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `capsule_cache_hits_total` | Counter | `cache` | Cache hits |
| `capsule_cache_misses_total` | Counter | `cache` | Cache misses |
| `capsule_cache_size` | Gauge | `cache` | Cache entries |

### ODYSSEY

#### Graph Operations

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `odyssey_graph_queries_total` | Counter | `query_type` | Graph queries |
| `odyssey_graph_query_duration_seconds` | Histogram | `query_type` | Query latency |
| `odyssey_graph_traversal_depth` | Histogram | `query_type` | Traversal depth |
| `odyssey_graph_results_count` | Histogram | `query_type` | Results per query |

#### Entity Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `odyssey_entities_total` | Gauge | `entity_type` | Total entities |
| `odyssey_relationships_total` | Gauge | `relation_type` | Total relationships |
| `odyssey_actors_total` | Gauge | `actor_type` | Total actors |

#### Path Finding

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `odyssey_paths_found_total` | Counter | | Paths found |
| `odyssey_path_finding_duration_seconds` | Histogram | | Path finding latency |

### PERCEPTION

#### Event Detection

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `perception_events_detected_total` | Counter | `event_type`, `source` | Events detected |
| `perception_events_processed_total` | Counter | `event_type`, `result` | Events processed |
| `perception_event_detection_duration_seconds` | Histogram | `event_type` | Detection latency |

#### Signal Processing

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `perception_signals_received_total` | Counter | `source` | Signals received |
| `perception_signals_processed_total` | Counter | `source`, `result` | Signals processed |
| `perception_signal_processing_duration_seconds` | Histogram | `source` | Processing latency |

#### Trust Scoring

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `perception_trust_scores` | Histogram | `source` | Trust score distribution |
| `perception_low_trust_events_total` | Counter | `source` | Low trust events |

#### Stream Processing

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `perception_stream_lag` | Gauge | `consumer_group`, `topic` | Consumer lag |
| `perception_stream_throughput` | Counter | `topic` | Messages/second |

### PLATO

#### Engine Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `plato_engine_executions_total` | Counter | `engine`, `status` | Engine executions |
| `plato_engine_execution_duration_seconds` | Histogram | `engine` | Execution latency |
| `plato_engine_queue_depth` | Gauge | `engine` | Queue depth |
| `plato_engine_active_tasks` | Gauge | `engine` | Active tasks |

#### Plan Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `plato_plans_created_total` | Counter | `engine` | Plans created |
| `plato_plans_completed_total` | Counter | `engine`, `status` | Plans completed |
| `plato_plan_duration_seconds` | Histogram | `engine` | Plan duration |

#### Governance

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `plato_governance_checks_total` | Counter | `policy`, `result` | Policy checks |
| `plato_governance_violations_total` | Counter | `policy` | Violations |

### NEXUS

#### Gateway Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `nexus_requests_total` | Counter | `service`, `method`, `status` | Requests routed |
| `nexus_request_duration_seconds` | Histogram | `service` | Request latency |
| `nexus_upstream_latency_seconds` | Histogram | `service` | Upstream latency |

#### Circuit Breaker

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `resilience4j_circuitbreaker_state` | Gauge | `name` | CB state (0=closed, 1=open, 2=half-open) |
| `resilience4j_circuitbreaker_calls_total` | Counter | `name`, `kind` | CB calls |
| `resilience4j_circuitbreaker_failure_rate` | Gauge | `name` | Failure rate |

#### Rate Limiting

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `nexus_rate_limit_total` | Counter | `client`, `result` | Rate limit checks |
| `nexus_rate_limit_rejected_total` | Counter | `client` | Rejected requests |

#### Authentication

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `nexus_auth_attempts_total` | Counter | `method`, `result` | Auth attempts |
| `nexus_jwt_validations_total` | Counter | `result` | JWT validations |

### SYNAPSE

#### Action Execution

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `synapse_action_execution_total` | Counter | `action_type`, `status` | Actions executed |
| `synapse_action_execution_duration_seconds` | Histogram | `action_type` | Execution latency |
| `synapse_execution_queue_depth` | Gauge | | Pending actions in queue |
| `synapse_action_risk_score` | Histogram | `action_type` | Risk score distribution |

#### Safety Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `synapse_safety_violations_total` | Counter | `violation_type` | Safety violations |
| `synapse_safety_kill_switch_active` | Gauge | | Kill switch status (0/1) |
| `synapse_safety_paused` | Gauge | | Safety pause status (0/1) |
| `synapse_high_risk_actions_total` | Counter | `action_type` | High-risk action requests |

#### Connector Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `synapse_connector_requests_total` | Counter | `connector`, `status` | Connector requests |
| `synapse_connector_latency_seconds` | Histogram | `connector` | Connector latency |
| `synapse_connector_errors_total` | Counter | `connector`, `error_type` | Connector errors |

#### Approval Workflow

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `synapse_approval_requests_total` | Counter | `status` | Approval requests |
| `synapse_approval_wait_duration_seconds` | Histogram | | Time waiting for approval |

---

## Kafka Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `kafka_consumer_records_consumed_total` | Counter | `topic` | Records consumed |
| `kafka_consumer_lag_records` | Gauge | `topic`, `partition` | Consumer lag |
| `kafka_producer_records_sent_total` | Counter | `topic` | Records sent |
| `kafka_producer_record_error_total` | Counter | `topic` | Send errors |

---

## Database Metrics

### Cassandra

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `cassandra_session_requests_total` | Counter | `type` | Session requests |
| `cassandra_session_request_duration_seconds` | Histogram | `type` | Request latency |
| `cassandra_session_errors_total` | Counter | `type` | Session errors |

### HikariCP (PostgreSQL)

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `hikaricp_connections_active` | Gauge | `pool` | Active connections |
| `hikaricp_connections_idle` | Gauge | `pool` | Idle connections |
| `hikaricp_connections_pending` | Gauge | `pool` | Pending connections |
| `hikaricp_connections_timeout_total` | Counter | `pool` | Connection timeouts |

### Redis

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `redis_commands_total` | Counter | `command` | Redis commands |
| `redis_command_duration_seconds` | Histogram | `command` | Command latency |

---

## SLI/SLO Metrics

### Availability

```promql
# Service availability (target: 99.9%)
sum(rate(http_server_requests_seconds_count{status!~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))
```

### Latency

```promql
# P99 latency (target: < 500ms)
histogram_quantile(0.99, 
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))
```

### Error Rate

```promql
# Error rate (target: < 0.1%)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m])) * 100
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Monitoring Overview](README.md) | Monitoring strategy |
| [Dashboards](dashboards.md) | Grafana dashboards |
| [Alerting](alerting.md) | Alert configuration |

