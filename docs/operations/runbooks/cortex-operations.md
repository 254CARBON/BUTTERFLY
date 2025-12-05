# CORTEX Operations Runbook

## Overview

This runbook covers operational procedures for the CORTEX AI Agent Platform.

## Health Monitoring

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall health |
| `/actuator/health/cortex` | CORTEX-specific health |
| `/actuator/health/llm` | LLM provider connectivity |
| `/actuator/health/coordination` | Coordinator status |

### Key Metrics

| Metric | Alert Threshold | Description |
|--------|-----------------|-------------|
| `butterfly_agent_task_success_total` | Success rate < 90% | Task completion rate |
| `butterfly_agent_task_duration_seconds_p95` | > 120s | Task duration |
| `butterfly_agent_tokens_total` | > 80% budget | Token consumption |
| `llm_circuit_breaker_state` | OPEN | LLM availability |

### Grafana Dashboard

Access: `https://grafana.internal/d/cortex-agent`

## Incident Response

### INC-001: High Task Failure Rate

**Symptoms:**
- `butterfly_agent_task_failure_total` increasing
- Success rate below 90%
- Error logs showing repeated failures

**Diagnosis:**
```bash
# Check recent failures
kubectl logs -l app=cortex --tail=100 | grep -i "error\|fail"

# Check LLM provider status
curl http://cortex:8080/actuator/health/llm

# Check task queue
curl http://cortex:8080/api/v1/admin/queues/tasks
```

**Resolution:**
1. Check LLM provider status (OpenAI status page)
2. Verify network connectivity to LLM endpoints
3. Check token budgets are not exhausted
4. Review recent configuration changes
5. Restart pods if necessary:
   ```bash
   kubectl rollout restart deployment/cortex
   ```

### INC-002: LLM Provider Unavailable

**Symptoms:**
- `llm_circuit_breaker_state` = OPEN
- `llm_requests_failed_total` increasing
- Logs showing timeout/connection errors

**Diagnosis:**
```bash
# Check circuit breaker state
curl http://cortex:8080/actuator/health | jq '.components.llm'

# Check LLM metrics
curl http://cortex:8080/actuator/prometheus | grep llm_

# Test LLM connectivity
curl -X POST http://cortex:8080/api/v1/admin/llm/health-check
```

**Resolution:**
1. Verify primary LLM provider status
2. Force fallback if needed:
   ```bash
   curl -X POST http://cortex:8080/api/v1/admin/llm/force-fallback
   ```
3. Check fallback model availability
4. Increase circuit breaker timeout if intermittent:
   ```yaml
   cortex:
     llm:
       circuit-breaker:
         wait-duration-in-open-state: 120s
   ```

### INC-003: Token Budget Exhausted

**Symptoms:**
- Tasks rejected with "budget exceeded" errors
- `butterfly_agent_tokens_total` at 100%
- Alerts for budget threshold

**Diagnosis:**
```bash
# Check budget status
curl http://cortex:8080/api/v1/admin/budgets/status

# Check per-agent usage
curl http://cortex:8080/api/v1/admin/budgets/by-agent
```

**Resolution:**
1. Identify high-consumption agents
2. Increase budget temporarily:
   ```bash
   curl -X POST http://cortex:8080/api/v1/admin/budgets/{agentId}/increase \
     -d '{"additionalTokens": 50000}'
   ```
3. Review agent task efficiency
4. Reset budget for new window:
   ```bash
   curl -X POST http://cortex:8080/api/v1/admin/budgets/{agentId}/reset
   ```

### INC-004: Multi-Agent Coordination Failure

**Symptoms:**
- Handoffs timing out
- Tasks stuck in "waiting for handoff"
- Coordination events not flowing

**Diagnosis:**
```bash
# Check coordinator health
curl http://cortex:8080/actuator/health/coordination

# Check Kafka topic lag
kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
  --describe --group cortex-coordination-consumer

# Check stuck tasks
curl http://cortex:8080/api/v1/admin/tasks/stuck
```

**Resolution:**
1. Check Kafka connectivity
2. Check coordinator leader election
3. Force task recovery:
   ```bash
   curl -X POST http://cortex:8080/api/v1/admin/tasks/{taskId}/recover
   ```
4. Restart coordinators:
   ```bash
   kubectl rollout restart deployment/cortex-coordinator
   ```

## Scaling

### Horizontal Scaling

```bash
# Scale agent workers
kubectl scale deployment/cortex --replicas=5

# Scale coordinators (maintain odd number)
kubectl scale deployment/cortex-coordinator --replicas=3
```

### Vertical Scaling

Update resource limits:
```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "2000m"
```

## Configuration

### Token Budget Adjustment

```yaml
cortex:
  agent:
    default-token-budget: 100000  # Per hour
    burst-allowance: 0.2           # 20% extra for critical
    warning-threshold: 0.8         # Alert at 80%
```

### LLM Configuration

```yaml
cortex:
  llm:
    primary-provider: openai
    fallback-provider: anthropic
    local-fallback: llama
    timeout-seconds: 30
    circuit-breaker:
      failure-rate-threshold: 50
      wait-duration-in-open-state: 60s
```

### Coordination Configuration

```yaml
cortex:
  coordination:
    handoff-timeout-seconds: 60
    max-agents-per-task: 5
    heartbeat-interval-ms: 5000
```

## Maintenance

### Graceful Shutdown

```bash
# Drain tasks before shutdown
curl -X POST http://cortex:8080/api/v1/admin/drain

# Wait for drain completion
curl http://cortex:8080/api/v1/admin/drain/status

# Then proceed with shutdown
kubectl scale deployment/cortex --replicas=0
```

### Log Rotation

Logs are automatically rotated via Logstash. Check configuration in `logback-spring-butterfly.xml`.

### Database Maintenance

Not applicable - CORTEX is stateless. State stored in CAPSULE.

## Emergency Procedures

### Kill Switch - Stop All Agents

```bash
# Immediate halt of all agent activity
curl -X POST http://cortex:8080/api/v1/admin/kill-switch/activate

# Resume operations
curl -X POST http://cortex:8080/api/v1/admin/kill-switch/deactivate
```

### Force Cancel All Tasks

```bash
curl -X POST http://cortex:8080/api/v1/admin/tasks/cancel-all
```

### Clear Token Budgets

```bash
curl -X POST http://cortex:8080/api/v1/admin/budgets/reset-all
```

## Contacts

| Role | Contact |
|------|---------|
| Primary On-Call | PagerDuty: cortex-oncall |
| Escalation | #butterfly-platform Slack |
| LLM Provider Issues | Support tickets via provider portal |

## Related Documentation

- [CORTEX Service](../../services/cortex.md)
- [Agent Architecture](../../architecture/agent-architecture.md)
- [CORTEX Integration](../../integration/cortex-integration.md)
