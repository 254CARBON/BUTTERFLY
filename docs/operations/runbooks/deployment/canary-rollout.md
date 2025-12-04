# Canary Rollout Runbook

**Last Updated**: 2025-12-04  
**Target Audience**: Platform Engineers, SREs  
**Estimated Duration**: 1-4 hours (including observation period)

---

## Overview

Canary deployment gradually shifts traffic from the current version to a new version, allowing real-world validation with minimal risk. This runbook covers canary rollouts for BUTTERFLY services.

### Benefits
- Gradual risk exposure
- Real production validation
- Automatic rollback on failures
- A/B testing capability

### Traffic Flow

```
                    ┌─────────────────┐
                    │   Ingress/LB    │
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    │  Traffic Split  │
                    │   (Weighted)    │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
    90% traffic        10% traffic         Prometheus
         │                   │             monitoring
    ┌────┴────┐        ┌────┴────┐              │
    │ Stable  │        │ Canary  │◄─────────────┘
    │ v1.2.3  │        │ v1.2.4  │         Compare
    │ 3 pods  │        │ 1 pod   │         metrics
    └─────────┘        └─────────┘
```

---

## Prerequisites

### Before Starting

- [ ] New version tested in staging
- [ ] Canary analysis criteria defined
- [ ] Prometheus metrics configured
- [ ] Slack/PagerDuty notifications configured
- [ ] Team notified in #butterfly-deployments

### Canary Success Criteria

| Metric | Threshold | Action if Breached |
|--------|-----------|-------------------|
| Error Rate | < 1% increase | Auto-rollback |
| P95 Latency | < 20% increase | Auto-rollback |
| P99 Latency | < 50% increase | Manual review |
| Success Rate | > 99% | Auto-rollback if below |

---

## Procedure

### Step 1: Prepare Canary Deployment

```bash
# Set environment variables
export SERVICE=perception  # or capsule, odyssey, plato, nexus, synapse
export NAMESPACE=butterfly
export NEW_VERSION=1.2.4
export CANARY_WEIGHT=10  # Start with 10% traffic

# Verify current deployment
kubectl get deploy -n ${NAMESPACE} -l app.kubernetes.io/name=${SERVICE}
```

### Step 2: Deploy Canary Version

```bash
# Deploy canary with single replica
helm upgrade ${SERVICE} butterfly/${SERVICE} \
  -n ${NAMESPACE} \
  --set image.tag=${NEW_VERSION} \
  --set canary.enabled=true \
  --set canary.weight=${CANARY_WEIGHT} \
  --set canary.replicas=1 \
  -f values-prod.yaml \
  --wait

# Or using kubectl apply
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${SERVICE}-canary
  namespace: ${NAMESPACE}
  labels:
    app.kubernetes.io/name: ${SERVICE}
    app.kubernetes.io/version: ${NEW_VERSION}
    track: canary
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: ${SERVICE}
      track: canary
  template:
    metadata:
      labels:
        app.kubernetes.io/name: ${SERVICE}
        track: canary
    spec:
      containers:
      - name: ${SERVICE}
        image: ghcr.io/254-studioz/butterfly-${SERVICE}:${NEW_VERSION}
        ports:
        - containerPort: 8080
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
EOF
```

### Step 3: Configure Traffic Split (Nginx Ingress)

```bash
# Create canary ingress
cat <<EOF | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${SERVICE}-canary
  namespace: ${NAMESPACE}
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "${CANARY_WEIGHT}"
spec:
  ingressClassName: nginx
  rules:
  - host: ${SERVICE}.butterfly.io
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: ${SERVICE}-canary
            port:
              number: 8080
EOF
```

### Step 4: Monitor Canary (Critical Phase)

#### Automated Monitoring Script

```bash
#!/bin/bash
# canary-monitor.sh

SERVICE=$1
DURATION_MINUTES=${2:-30}
CHECK_INTERVAL=60

echo "Monitoring canary for ${SERVICE} for ${DURATION_MINUTES} minutes..."

STABLE_ERROR_RATE=$(curl -sf "http://prometheus:9090/api/v1/query?query=sum(rate(http_server_requests_seconds_count{service=\"${SERVICE}\",status=~\"5..\",track=\"stable\"}[5m]))/sum(rate(http_server_requests_seconds_count{service=\"${SERVICE}\",track=\"stable\"}[5m]))" | jq -r '.data.result[0].value[1]')

for i in $(seq 1 $((DURATION_MINUTES * 60 / CHECK_INTERVAL))); do
  # Get canary error rate
  CANARY_ERROR_RATE=$(curl -sf "http://prometheus:9090/api/v1/query?query=sum(rate(http_server_requests_seconds_count{service=\"${SERVICE}\",status=~\"5..\",track=\"canary\"}[5m]))/sum(rate(http_server_requests_seconds_count{service=\"${SERVICE}\",track=\"canary\"}[5m]))" | jq -r '.data.result[0].value[1]')
  
  # Get canary latency P95
  CANARY_LATENCY=$(curl -sf "http://prometheus:9090/api/v1/query?query=histogram_quantile(0.95,sum(rate(http_server_requests_seconds_bucket{service=\"${SERVICE}\",track=\"canary\"}[5m]))by(le))" | jq -r '.data.result[0].value[1]')
  
  echo "[$(date)] Canary error rate: ${CANARY_ERROR_RATE}, P95 latency: ${CANARY_LATENCY}s"
  
  # Check thresholds
  if (( $(echo "${CANARY_ERROR_RATE} > 0.01" | bc -l) )); then
    echo "ERROR: Canary error rate exceeds threshold!"
    exit 1
  fi
  
  sleep ${CHECK_INTERVAL}
done

echo "Canary monitoring completed successfully!"
```

#### Manual Monitoring Checklist

```bash
# Check canary pod health
kubectl get pods -n ${NAMESPACE} -l track=canary

# Check canary logs
kubectl logs -n ${NAMESPACE} -l track=canary --tail=100

# Compare metrics in Grafana
# Dashboard: BUTTERFLY Ecosystem SLO Dashboard
# Filter by: track=canary vs track=stable
```

### Step 5: Progressive Traffic Increase

If canary is healthy, gradually increase traffic:

```bash
# Increase to 25%
kubectl annotate ingress ${SERVICE}-canary -n ${NAMESPACE} \
  nginx.ingress.kubernetes.io/canary-weight=25 --overwrite

# Wait and monitor (15 minutes)
sleep 900

# Increase to 50%
kubectl annotate ingress ${SERVICE}-canary -n ${NAMESPACE} \
  nginx.ingress.kubernetes.io/canary-weight=50 --overwrite

# Wait and monitor (15 minutes)
sleep 900

# Increase to 75%
kubectl annotate ingress ${SERVICE}-canary -n ${NAMESPACE} \
  nginx.ingress.kubernetes.io/canary-weight=75 --overwrite

# Wait and monitor (15 minutes)
sleep 900

# Full rollout (100%)
kubectl annotate ingress ${SERVICE}-canary -n ${NAMESPACE} \
  nginx.ingress.kubernetes.io/canary-weight=100 --overwrite
```

### Step 6: Promote Canary to Stable

After successful validation at 100%:

```bash
# Update stable deployment to new version
helm upgrade ${SERVICE} butterfly/${SERVICE} \
  -n ${NAMESPACE} \
  --set image.tag=${NEW_VERSION} \
  --set canary.enabled=false \
  -f values-prod.yaml \
  --wait

# Remove canary deployment
kubectl delete deploy ${SERVICE}-canary -n ${NAMESPACE}
kubectl delete ingress ${SERVICE}-canary -n ${NAMESPACE}
kubectl delete svc ${SERVICE}-canary -n ${NAMESPACE}
```

---

## Rollback Procedure

### Automatic Rollback (via monitoring)

```bash
# Stop all canary traffic immediately
kubectl annotate ingress ${SERVICE}-canary -n ${NAMESPACE} \
  nginx.ingress.kubernetes.io/canary-weight=0 --overwrite

# Scale down canary
kubectl scale deploy ${SERVICE}-canary -n ${NAMESPACE} --replicas=0
```

### Manual Rollback

```bash
# Delete canary resources
kubectl delete deploy ${SERVICE}-canary -n ${NAMESPACE}
kubectl delete ingress ${SERVICE}-canary -n ${NAMESPACE}
kubectl delete svc ${SERVICE}-canary -n ${NAMESPACE}

# Verify stable is handling all traffic
kubectl get endpoints ${SERVICE} -n ${NAMESPACE}
```

---

## Canary Weights by Phase

| Phase | Weight | Duration | Rollback Trigger |
|-------|--------|----------|------------------|
| Initial | 5-10% | 15-30 min | Any error rate > 1% |
| Early | 25% | 15 min | Error rate > 0.5% increase |
| Mid | 50% | 15 min | Latency > 20% increase |
| Late | 75% | 15 min | Any anomaly |
| Complete | 100% | - | Promote to stable |

---

## Service-Specific Considerations

### PERCEPTION (Sensory Layer)
- Monitor Kafka consumer lag during canary
- Check Apache Ignite cache hit rates
- Watch for increased PostgreSQL connections

### CAPSULE (Memory Layer)
- Monitor Cassandra read/write latency separately
- Check tenant isolation correctness
- Verify Redis cache consistency

### ODYSSEY (Cognition Layer)
- JanusGraph query latency critical
- Monitor path traversal times
- Check forecasting accuracy

### PLATO (Governance Layer)
- Spec validation correctness
- Plan generation latency
- Proof engine performance

### NEXUS (Integration Layer)
- Gateway overhead must stay < 50ms
- Circuit breaker states
- Cross-service correlation

### SYNAPSE (Execution Engine)
- Action execution latency
- Tool invocation success rate
- Workflow completion rate

---

## Checklist

### Pre-Canary
- [ ] Version tested in staging
- [ ] Metrics baseline recorded
- [ ] Alert thresholds configured
- [ ] Rollback procedure reviewed
- [ ] Team notified

### During Canary
- [ ] Canary deployed (10%)
- [ ] Health checks passing
- [ ] Error rate within threshold
- [ ] Latency within threshold
- [ ] Progressive increase following schedule

### Post-Canary
- [ ] 100% traffic on new version
- [ ] Canary resources cleaned up
- [ ] Stable deployment updated
- [ ] Deployment documented

---

## Troubleshooting

### Canary Not Receiving Traffic

```bash
# Check ingress configuration
kubectl get ingress ${SERVICE}-canary -n ${NAMESPACE} -o yaml

# Verify canary annotation
kubectl describe ingress ${SERVICE}-canary -n ${NAMESPACE} | grep canary

# Check endpoints
kubectl get endpoints ${SERVICE}-canary -n ${NAMESPACE}
```

### Metrics Not Differentiating Stable vs Canary

```bash
# Ensure pods have correct labels
kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=${SERVICE} --show-labels

# Check Prometheus targets
curl -sf http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.service == "${SERVICE}")'
```

### High Error Rate on Canary

1. **Immediate**: Rollback canary traffic to 0%
2. **Investigate**: Check logs, compare with stable
3. **Root Cause**: Config diff, dependency issue, resource limits?

