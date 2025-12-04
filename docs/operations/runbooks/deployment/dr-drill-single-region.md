# Disaster Recovery Drill: Single-Region High Availability

**Last Updated**: 2025-12-04  
**Target Audience**: Platform Engineers, SREs, Incident Response Team  
**Drill Duration**: 2-4 hours  
**Recommended Frequency**: Monthly

---

## Overview

This runbook covers the disaster recovery drill procedure for validating single-region high availability of the BUTTERFLY platform. The drill tests failover capabilities, data integrity, and recovery procedures.

### Drill Objectives

1. **Failover Validation**: Verify services survive node/pod failures
2. **Data Integrity**: Confirm data consistency after failures
3. **Recovery Time**: Measure RTO (Recovery Time Objective)
4. **Communication**: Test incident communication procedures

### Architecture Under Test

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster (Single Region)          │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Zone A     │  │   Zone B     │  │   Zone C     │          │
│  │              │  │              │  │              │          │
│  │ ┌──────────┐ │  │ ┌──────────┐ │  │ ┌──────────┐ │          │
│  │ │PERCEPTION│ │  │ │PERCEPTION│ │  │ │PERCEPTION│ │          │
│  │ └──────────┘ │  │ └──────────┘ │  │ └──────────┘ │          │
│  │ ┌──────────┐ │  │ ┌──────────┐ │  │ ┌──────────┐ │          │
│  │ │ CAPSULE  │ │  │ │ CAPSULE  │ │  │ │ CAPSULE  │ │          │
│  │ └──────────┘ │  │ └──────────┘ │  │ └──────────┘ │          │
│  │ ┌──────────┐ │  │ ┌──────────┐ │  │ ┌──────────┐ │          │
│  │ │ ODYSSEY  │ │  │ │ ODYSSEY  │ │  │ │ ODYSSEY  │ │          │
│  │ └──────────┘ │  │ └──────────┘ │  │ └──────────┘ │          │
│  │     ...      │  │     ...      │  │     ...      │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Stateful Services (HA Configured)            │  │
│  │  Cassandra (3 nodes) │ PostgreSQL (Primary+Replica)      │  │
│  │  Kafka (3 brokers)   │ Redis Sentinel (3 nodes)          │  │
│  │  JanusGraph (2+)     │ Apache Ignite (3 nodes)           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

### Before the Drill

- [ ] Schedule drill with stakeholders
- [ ] Notify support team and customers (if applicable)
- [ ] Ensure monitoring dashboards are accessible
- [ ] Prepare incident communication channels
- [ ] Backup critical data (safety measure)
- [ ] Review rollback procedures

### Required Access

- Kubernetes cluster admin
- Database admin access (Cassandra, PostgreSQL)
- Monitoring system (Prometheus, Grafana)
- Alertmanager access
- Incident management system

---

## Drill Procedure

### Phase 0: Pre-Drill Setup (15 minutes)

```bash
# Set variables
export NAMESPACE=butterfly
export DRILL_ID="dr-drill-$(date +%Y%m%d-%H%M)"

# Create drill tracking
echo "Starting DR Drill: ${DRILL_ID}" > /tmp/${DRILL_ID}.log

# Record baseline metrics
kubectl get pods -n ${NAMESPACE} -o wide > /tmp/${DRILL_ID}-baseline-pods.txt
kubectl get nodes -o wide > /tmp/${DRILL_ID}-baseline-nodes.txt

# Verify all services healthy
./scripts/health-check-all.sh >> /tmp/${DRILL_ID}.log 2>&1

# Silence non-critical alerts
# (Configure in Alertmanager - adjust matchers as needed)
cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1alpha1
kind: AlertmanagerConfig
metadata:
  name: dr-drill-silence
  namespace: ${NAMESPACE}
spec:
  route:
    receiver: 'null'
    matchers:
    - name: severity
      value: warning
      matchType: =
  receivers:
  - name: 'null'
EOF
```

### Phase 1: Single Pod Failure (30 minutes)

**Objective**: Verify service continuity when individual pods fail.

```bash
echo "=== Phase 1: Single Pod Failure ===" >> /tmp/${DRILL_ID}.log
echo "Start time: $(date)" >> /tmp/${DRILL_ID}.log

# Select a random pod from each service
for SERVICE in perception capsule odyssey plato nexus synapse; do
  POD=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=${SERVICE} -o jsonpath='{.items[0].metadata.name}')
  
  echo "Killing pod: ${POD}" >> /tmp/${DRILL_ID}.log
  
  # Record pre-failure state
  kubectl get pod ${POD} -n ${NAMESPACE} -o yaml > /tmp/${DRILL_ID}-${SERVICE}-pre.yaml
  
  # Kill the pod
  kubectl delete pod ${POD} -n ${NAMESPACE} --grace-period=0 --force
  
  # Wait and verify replacement
  sleep 10
  
  # Check if new pod is running
  NEW_POD=$(kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=${SERVICE} -o jsonpath='{.items[0].metadata.name}')
  kubectl wait --for=condition=Ready pod/${NEW_POD} -n ${NAMESPACE} --timeout=120s
  
  echo "Replacement pod ready: ${NEW_POD}" >> /tmp/${DRILL_ID}.log
done
```

**Validation Checklist:**
- [ ] All pods replaced within 2 minutes
- [ ] No 5xx errors during failover
- [ ] Traffic continued uninterrupted
- [ ] Metrics show brief spike, then normal

### Phase 2: Zone/Node Failure Simulation (45 minutes)

**Objective**: Verify service survives loss of an availability zone.

```bash
echo "=== Phase 2: Zone Failure Simulation ===" >> /tmp/${DRILL_ID}.log
echo "Start time: $(date)" >> /tmp/${DRILL_ID}.log

# Identify nodes in one zone
ZONE_A_NODES=$(kubectl get nodes -l topology.kubernetes.io/zone=zone-a -o jsonpath='{.items[*].metadata.name}')

# Cordon all nodes in zone (simulate zone unavailability)
for NODE in ${ZONE_A_NODES}; do
  echo "Cordoning node: ${NODE}" >> /tmp/${DRILL_ID}.log
  kubectl cordon ${NODE}
done

# Drain pods from zone (graceful evacuation)
for NODE in ${ZONE_A_NODES}; do
  echo "Draining node: ${NODE}" >> /tmp/${DRILL_ID}.log
  kubectl drain ${NODE} \
    --ignore-daemonsets \
    --delete-emptydir-data \
    --force \
    --timeout=120s || true
done

# Wait for rescheduling
echo "Waiting for pod rescheduling..." >> /tmp/${DRILL_ID}.log
sleep 60

# Verify all services running in remaining zones
kubectl get pods -n ${NAMESPACE} -o wide >> /tmp/${DRILL_ID}.log

# Check service health
for SERVICE in perception capsule odyssey plato nexus synapse; do
  HEALTH=$(kubectl exec -n ${NAMESPACE} deploy/${SERVICE} -- curl -sf http://localhost:8080/actuator/health || echo "FAILED")
  echo "${SERVICE} health: ${HEALTH}" >> /tmp/${DRILL_ID}.log
done
```

**Validation Checklist:**
- [ ] All services maintained minimum replicas
- [ ] PDB respected (no more than 1 pod unavailable at a time)
- [ ] Traffic rerouted successfully
- [ ] No data loss reported

### Phase 3: Database Failover (45 minutes)

**Objective**: Verify database failover and data integrity.

#### Cassandra Failover

```bash
echo "=== Phase 3a: Cassandra Node Failure ===" >> /tmp/${DRILL_ID}.log

# Identify Cassandra seed node
CASSANDRA_POD=$(kubectl get pods -n ${NAMESPACE} -l app=cassandra -o jsonpath='{.items[0].metadata.name}')

# Check cluster status before
kubectl exec -n ${NAMESPACE} ${CASSANDRA_POD} -- nodetool status >> /tmp/${DRILL_ID}.log

# Kill one Cassandra node
kubectl delete pod ${CASSANDRA_POD} -n ${NAMESPACE} --grace-period=0 --force

# Wait for cluster to stabilize
sleep 60

# Check cluster status after
NEW_CASSANDRA_POD=$(kubectl get pods -n ${NAMESPACE} -l app=cassandra -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n ${NAMESPACE} ${NEW_CASSANDRA_POD} -- nodetool status >> /tmp/${DRILL_ID}.log

# Verify CAPSULE can still read/write
kubectl exec -n ${NAMESPACE} deploy/capsule -- curl -sf http://localhost:8081/api/v1/health/db >> /tmp/${DRILL_ID}.log
```

#### PostgreSQL Failover

```bash
echo "=== Phase 3b: PostgreSQL Failover ===" >> /tmp/${DRILL_ID}.log

# If using PostgreSQL with streaming replication
# Trigger manual failover
kubectl exec -n ${NAMESPACE} postgresql-0 -- pg_ctl promote -D /data/pgdata || true

# Wait for failover
sleep 30

# Verify PERCEPTION can connect
kubectl exec -n ${NAMESPACE} deploy/perception -- curl -sf http://localhost:8085/api/v1/health/db >> /tmp/${DRILL_ID}.log
```

**Validation Checklist:**
- [ ] Database cluster remains quorum
- [ ] Services reconnected automatically
- [ ] No data corruption detected
- [ ] Write operations resumed

### Phase 4: Kafka Broker Failure (30 minutes)

**Objective**: Verify message delivery during Kafka broker failure.

```bash
echo "=== Phase 4: Kafka Broker Failure ===" >> /tmp/${DRILL_ID}.log

# Check current topic state
kubectl exec -n ${NAMESPACE} kafka-0 -- kafka-topics.sh --describe --bootstrap-server localhost:9092 >> /tmp/${DRILL_ID}.log

# Kill one Kafka broker
kubectl delete pod kafka-1 -n ${NAMESPACE} --grace-period=0 --force

# Wait for leader election
sleep 30

# Check topic state after failover
kubectl exec -n ${NAMESPACE} kafka-0 -- kafka-topics.sh --describe --bootstrap-server localhost:9092 >> /tmp/${DRILL_ID}.log

# Verify consumers are still consuming
kubectl logs -n ${NAMESPACE} deploy/perception --tail=50 | grep -i kafka >> /tmp/${DRILL_ID}.log
```

**Validation Checklist:**
- [ ] Leader election completed
- [ ] No message loss (check consumer lag)
- [ ] Producers reconnected
- [ ] Consumers resumed

### Phase 5: Full Service Recovery (30 minutes)

**Objective**: Restore all systems to normal state.

```bash
echo "=== Phase 5: Full Recovery ===" >> /tmp/${DRILL_ID}.log
echo "Start time: $(date)" >> /tmp/${DRILL_ID}.log

# Uncordon all nodes
for NODE in ${ZONE_A_NODES}; do
  echo "Uncordoning node: ${NODE}" >> /tmp/${DRILL_ID}.log
  kubectl uncordon ${NODE}
done

# Rebalance pods
kubectl rollout restart deploy -n ${NAMESPACE}

# Wait for all pods to be ready
kubectl wait --for=condition=Ready pods --all -n ${NAMESPACE} --timeout=300s

# Verify pod distribution across zones
kubectl get pods -n ${NAMESPACE} -o wide | awk '{print $7}' | sort | uniq -c >> /tmp/${DRILL_ID}.log

# Remove alert silence
kubectl delete alertmanagerconfig dr-drill-silence -n ${NAMESPACE} || true

# Final health check
echo "=== Final Health Check ===" >> /tmp/${DRILL_ID}.log
./scripts/health-check-all.sh >> /tmp/${DRILL_ID}.log 2>&1
```

---

## Recovery Time Objectives (RTO)

| Failure Scenario | Target RTO | Acceptance Criteria |
|------------------|------------|---------------------|
| Single Pod | < 2 min | New pod running, traffic resumed |
| Zone Failure | < 5 min | Services running in other zones |
| Database Node | < 5 min | Cluster quorum, reads/writes working |
| Kafka Broker | < 3 min | Leader elected, messages flowing |
| Full Recovery | < 15 min | All services healthy, metrics normal |

---

## Post-Drill Analysis

### Metrics to Collect

```bash
# Generate drill report
cat << EOF > /tmp/${DRILL_ID}-report.md
# DR Drill Report: ${DRILL_ID}

**Date**: $(date)
**Duration**: [Start Time] - [End Time]
**Participants**: [List participants]

## Executive Summary
[Summary of drill results]

## Metrics

### Availability During Drill
| Service | Min Availability | Max Latency P95 |
|---------|-----------------|-----------------|
| PERCEPTION | [%] | [ms] |
| CAPSULE | [%] | [ms] |
| ODYSSEY | [%] | [ms] |
| PLATO | [%] | [ms] |
| NEXUS | [%] | [ms] |
| SYNAPSE | [%] | [ms] |

### Recovery Times
| Phase | Target RTO | Actual RTO | Status |
|-------|------------|------------|--------|
| Pod Failure | 2 min | [actual] | [Pass/Fail] |
| Zone Failure | 5 min | [actual] | [Pass/Fail] |
| DB Failover | 5 min | [actual] | [Pass/Fail] |
| Kafka Failover | 3 min | [actual] | [Pass/Fail] |

## Issues Identified
1. [Issue 1]
2. [Issue 2]

## Action Items
- [ ] [Action 1]
- [ ] [Action 2]

## Lessons Learned
[Document lessons learned]
EOF

echo "Report template created: /tmp/${DRILL_ID}-report.md"
```

### Review Meeting Agenda

1. **Drill Overview** (5 min)
   - What was tested
   - Who participated

2. **Results Review** (15 min)
   - Success/failure of each phase
   - RTO achievement

3. **Issues Discussion** (20 min)
   - Problems encountered
   - Root causes

4. **Action Items** (10 min)
   - Assign owners
   - Set deadlines

5. **Process Improvements** (10 min)
   - Runbook updates
   - Automation opportunities

---

## Troubleshooting

### Pods Not Rescheduling

```bash
# Check PDB constraints
kubectl get pdb -n ${NAMESPACE}

# Check node resources
kubectl describe nodes | grep -A5 "Allocated resources"

# Force eviction if needed
kubectl delete pod <pod-name> -n ${NAMESPACE} --grace-period=0 --force
```

### Database Cluster Degraded

```bash
# Cassandra - check cluster
kubectl exec -n ${NAMESPACE} cassandra-0 -- nodetool status

# PostgreSQL - check replication
kubectl exec -n ${NAMESPACE} postgresql-0 -- psql -c "SELECT * FROM pg_stat_replication;"
```

### Services Not Recovering

```bash
# Check deployment status
kubectl get deploy -n ${NAMESPACE}

# Check for pending pods
kubectl get pods -n ${NAMESPACE} --field-selector=status.phase=Pending

# Check events
kubectl get events -n ${NAMESPACE} --sort-by=.lastTimestamp | tail -20
```

---

## Drill Schedule

| Drill Type | Frequency | Next Scheduled |
|------------|-----------|----------------|
| Single Pod | Weekly (automated) | [Date] |
| Zone Failure | Monthly | [Date] |
| Database Failover | Monthly | [Date] |
| Full DR Drill | Quarterly | [Date] |

