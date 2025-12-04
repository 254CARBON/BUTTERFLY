# Blue-Green Deployment Runbook

**Last Updated**: 2025-12-04  
**Target Audience**: Platform Engineers, SREs  
**Estimated Duration**: 15-30 minutes per service

---

## Overview

Blue-Green deployment is a release strategy that maintains two identical production environments. This runbook covers the procedure for performing blue-green deployments of BUTTERFLY services.

### Benefits
- Zero-downtime deployments
- Instant rollback capability
- Validation of new release before traffic switch

### Architecture

```
                    ┌─────────────────┐
                    │   Ingress/LB    │
                    │  (Traffic Split)│
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              │              ▼
    ┌─────────────────┐     │    ┌─────────────────┐
    │  Blue (Active)  │     │    │ Green (Standby) │
    │   v1.2.3        │◄────┼───►│    v1.2.4       │
    │   3 replicas    │     │    │   3 replicas    │
    └─────────────────┘     │    └─────────────────┘
                            │
                    ┌───────┴───────┐
                    │   Shared DB   │
                    │   (w/ schema  │
                    │   compatibility)│
                    └───────────────┘
```

---

## Prerequisites

### Before Starting

- [ ] New version tested in staging
- [ ] Database migrations are backward compatible
- [ ] Monitoring dashboards accessible
- [ ] Rollback procedure reviewed
- [ ] Team notified in #butterfly-deployments

### Required Access

- Kubernetes cluster admin or deploy role
- Helm repository access
- Grafana/monitoring dashboards
- Alertmanager access (to silence/manage alerts)

---

## Procedure

### Step 1: Prepare Green Environment

```bash
# Set environment variables
export SERVICE=perception  # or capsule, odyssey, plato, nexus, synapse
export NAMESPACE=butterfly
export NEW_VERSION=1.2.4
export OLD_VERSION=1.2.3

# Verify current state
kubectl get deploy -n ${NAMESPACE} -l app.kubernetes.io/name=${SERVICE}
helm list -n ${NAMESPACE} | grep ${SERVICE}
```

### Step 2: Deploy Green (Standby) Version

```bash
# Deploy new version as "green" release
helm upgrade ${SERVICE}-green butterfly/${SERVICE} \
  -n ${NAMESPACE} \
  --install \
  --set image.tag=${NEW_VERSION} \
  --set replicaCount=3 \
  --set service.name=${SERVICE}-green \
  --set deployment.labels.version=green \
  -f values-prod.yaml \
  --wait
```

### Step 3: Validate Green Environment

```bash
# Health check green pods
kubectl exec -n ${NAMESPACE} deploy/${SERVICE}-green -- \
  curl -sf http://localhost:8080/actuator/health

# Run smoke tests against green
kubectl run smoke-test --rm -it --image=curlimages/curl -- \
  curl -sf http://${SERVICE}-green.${NAMESPACE}.svc.cluster.local:8080/api/v1/health

# Check logs for errors
kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=${SERVICE},version=green --tail=100

# Verify metrics are being collected
kubectl exec -n ${NAMESPACE} deploy/${SERVICE}-green -- \
  curl -sf http://localhost:8080/actuator/prometheus | head -20
```

### Step 4: Traffic Switch

#### Option A: Instant Switch (Recommended for BUTTERFLY)

```bash
# Update service selector to point to green
kubectl patch service ${SERVICE} -n ${NAMESPACE} -p \
  '{"spec":{"selector":{"version":"green"}}}'

# Verify traffic is going to green
kubectl get endpoints ${SERVICE} -n ${NAMESPACE}
```

#### Option B: Gradual Switch via Ingress (Alternative)

```yaml
# Apply canary ingress annotation
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${SERVICE}-canary
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"  # Start with 10%
spec:
  # ... route to green service
```

### Step 5: Post-Switch Validation

```bash
# Monitor error rates
echo "Check Grafana: Error Rate for ${SERVICE}"
echo "Dashboard: BUTTERFLY Ecosystem SLO Dashboard"

# Check for increased latency
kubectl exec -n ${NAMESPACE} deploy/${SERVICE}-green -- \
  curl -sf http://localhost:8080/actuator/metrics/http.server.requests

# Verify no 5xx errors
kubectl logs -n ${NAMESPACE} -l version=green --since=5m | grep -i error

# Run integration tests
# ./scripts/integration-test.sh ${SERVICE} prod
```

### Step 6: Cleanup Blue (Old Version)

**Only after confirming green is stable (recommended: wait 15-30 minutes)**

```bash
# Scale down blue deployment
kubectl scale deploy ${SERVICE}-blue -n ${NAMESPACE} --replicas=0

# Delete blue release (after extended validation)
helm uninstall ${SERVICE}-blue -n ${NAMESPACE}

# Rename green to become the new blue for next deployment
kubectl label deploy ${SERVICE}-green -n ${NAMESPACE} version=blue --overwrite
```

---

## Rollback Procedure

### Instant Rollback

If issues are detected after traffic switch:

```bash
# Switch traffic back to blue
kubectl patch service ${SERVICE} -n ${NAMESPACE} -p \
  '{"spec":{"selector":{"version":"blue"}}}'

# Verify rollback
kubectl get endpoints ${SERVICE} -n ${NAMESPACE}

# Scale down problematic green
kubectl scale deploy ${SERVICE}-green -n ${NAMESPACE} --replicas=0
```

### Full Rollback with Helm

```bash
# Rollback to previous release
helm rollback ${SERVICE} -n ${NAMESPACE}

# Or rollback to specific revision
helm rollback ${SERVICE} 3 -n ${NAMESPACE}
```

---

## Service-Specific Notes

### PERCEPTION
- Ensure Apache Ignite cache is warmed before switching
- Check Kafka consumer lag after switch

### CAPSULE
- Verify Cassandra connection pool after switch
- Check tenant cache invalidation

### ODYSSEY
- JanusGraph connections may take 30s to establish
- Monitor graph traversal latency

### PLATO
- Ensure spec registry sync is complete
- Check proof engine initialization

### NEXUS
- All downstream service connections must be validated
- Check circuit breaker states

### SYNAPSE
- Action executor threads should stabilize
- Verify tool registry is loaded

---

## Checklist

### Pre-Deployment
- [ ] Staging tests passed
- [ ] Database migration compatible
- [ ] Config changes reviewed
- [ ] Team notified

### During Deployment
- [ ] Green deployed successfully
- [ ] Health checks passing
- [ ] Metrics being collected
- [ ] No errors in logs

### Post-Deployment
- [ ] Traffic switched
- [ ] Error rate stable
- [ ] Latency acceptable
- [ ] No alert fires

### Cleanup
- [ ] Blue scaled down (after 30 min)
- [ ] Old release deleted (after 2 hours)
- [ ] Deployment documented

---

## Troubleshooting

### Green Pods Not Starting

```bash
# Check pod events
kubectl describe pods -n ${NAMESPACE} -l version=green

# Check resource limits
kubectl top pods -n ${NAMESPACE}

# Check for image pull issues
kubectl get events -n ${NAMESPACE} --field-selector reason=Failed
```

### Traffic Not Switching

```bash
# Verify service selector
kubectl get svc ${SERVICE} -n ${NAMESPACE} -o yaml | grep selector -A5

# Check endpoint binding
kubectl get endpoints ${SERVICE} -n ${NAMESPACE} -o yaml
```

### High Error Rate After Switch

1. **Immediate**: Rollback to blue
2. **Investigate**: Check logs, metrics
3. **Root Cause**: Database, config, dependencies?

