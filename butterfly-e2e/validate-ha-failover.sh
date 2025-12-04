#!/bin/bash
# =============================================================================
# BUTTERFLY HA Failover Validation Script
# =============================================================================
# Validates high availability failover scenarios by running chaos experiments
# and verifying system recovery within acceptable bounds.
#
# Prerequisites:
#   - Chaos Mesh installed in the cluster
#   - HA topology deployed (k8s/kustomize/overlays/ha)
#   - All services running with minimum replica counts
#
# Usage:
#   ./validate-ha-failover.sh [OPTIONS]
#
# Options:
#   --namespace NAMESPACE   Kubernetes namespace (default: butterfly)
#   --category CATEGORY     Experiment category (service|infra|network|all)
#   --dry-run               Validate configuration without running experiments
#   --report FILE           Output JSON report file
#   --verbose               Enable verbose output
#   --help                  Show this help message
#
# Exit Codes:
#   0 - All failover validations passed
#   1 - One or more validations failed
#   2 - Configuration error
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHAOS_DIR="${SCRIPT_DIR}/../chaos/experiments"
K8S_CHAOS_DIR="${SCRIPT_DIR}/../k8s/chaos/experiments"

# Configuration
NAMESPACE="${NAMESPACE:-butterfly}"
CATEGORY="${CATEGORY:-all}"
DRY_RUN=false
REPORT_FILE=""
VERBOSE=false
NEXUS_URL="${NEXUS_URL:-http://localhost:8084}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

# Recovery thresholds (seconds)
MAX_RECOVERY_TIME_SERVICE=60
MAX_RECOVERY_TIME_INFRA=120
MAX_RECOVERY_TIME_NETWORK=90

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Results tracking
TOTAL_EXPERIMENTS=0
PASSED_EXPERIMENTS=0
FAILED_EXPERIMENTS=0
declare -A EXPERIMENT_RESULTS

log_info() { echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_section() { echo -e "\n${CYAN}=== $1 ===${NC}"; }

show_help() {
    head -30 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --namespace) NAMESPACE="$2"; shift 2 ;;
        --category) CATEGORY="$2"; shift 2 ;;
        --dry-run) DRY_RUN=true; shift ;;
        --report) REPORT_FILE="$2"; shift 2 ;;
        --verbose) VERBOSE=true; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 2 ;;
    esac
done

# =============================================================================
# Helper Functions
# =============================================================================

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl not found"
        exit 2
    fi
    
    # Check Chaos Mesh
    if ! kubectl get crd podchaos.chaos-mesh.org &> /dev/null; then
        log_error "Chaos Mesh CRDs not found. Install Chaos Mesh first."
        exit 2
    fi
    
    # Check HA deployment
    local replicas
    for service in capsule perception-api odyssey plato nexus synapse; do
        replicas=$(kubectl get deployment "$service" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
        if [ "$replicas" -lt 2 ]; then
            log_warn "Service $service has only $replicas replicas. HA requires 2+."
        fi
    done
    
    log_info "Prerequisites check completed"
}

wait_for_pods_ready() {
    local app="$1"
    local timeout="${2:-120}"
    
    log_info "Waiting for $app pods to be ready..."
    kubectl wait --for=condition=ready pod -l "app=$app" -n "$NAMESPACE" --timeout="${timeout}s" 2>/dev/null || return 1
    return 0
}

check_service_health() {
    local service="$1"
    local port="${2:-8080}"
    
    local health
    health=$(kubectl exec -n "$NAMESPACE" deploy/"$service" -- curl -sf "http://localhost:${port}/actuator/health" 2>/dev/null | jq -r '.status' || echo "DOWN")
    
    [ "$health" = "UP" ]
}

measure_recovery_time() {
    local service="$1"
    local max_wait="${2:-$MAX_RECOVERY_TIME_SERVICE}"
    local start_time
    start_time=$(date +%s)
    
    local elapsed=0
    while [ $elapsed -lt $max_wait ]; do
        if check_service_health "$service"; then
            echo $(($(date +%s) - start_time))
            return 0
        fi
        sleep 2
        elapsed=$(($(date +%s) - start_time))
    done
    
    echo "$max_wait"
    return 1
}

check_temporal_slice() {
    local result
    result=$(curl -sf "${NEXUS_URL}/api/v1/temporal/slice" \
        -H "Content-Type: application/json" \
        -d '{"entityId": "rim:entity:test:ha-validation", "timeframe": {"lookback": "PT1M"}}' 2>/dev/null)
    
    if [ -n "$result" ]; then
        echo "$result" | jq -r '.metadata.status // "ERROR"'
    else
        echo "ERROR"
    fi
}

check_golden_loop() {
    local result
    result=$(curl -sf "${NEXUS_URL}/api/v1/temporal/golden-loop/health" 2>/dev/null)
    
    if [ -n "$result" ]; then
        echo "$result" | jq -r '.status // "ERROR"'
    else
        echo "ERROR"
    fi
}

# =============================================================================
# Chaos Experiment Functions
# =============================================================================

run_experiment() {
    local name="$1"
    local file="$2"
    local max_recovery="$3"
    
    ((TOTAL_EXPERIMENTS++))
    
    log_section "Experiment: $name"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] Would run: $file"
        EXPERIMENT_RESULTS["$name"]="SKIP:0:dry-run"
        return 0
    fi
    
    # Apply chaos experiment
    log_info "Applying chaos experiment..."
    if ! kubectl apply -f "$file" -n "$NAMESPACE" 2>/dev/null; then
        log_error "Failed to apply experiment"
        EXPERIMENT_RESULTS["$name"]="FAIL:0:apply-failed"
        ((FAILED_EXPERIMENTS++))
        return 1
    fi
    
    # Wait for experiment to take effect
    sleep 5
    
    # Wait for chaos experiment to complete (or duration to pass)
    local duration
    duration=$(grep -E "duration:" "$file" | head -1 | awk '{print $2}' | tr -d '"')
    duration_seconds=$(echo "$duration" | sed 's/s//' | sed 's/m/*60/' | bc)
    log_info "Experiment duration: $duration (${duration_seconds}s)"
    
    sleep "$((duration_seconds + 5))"
    
    # Delete the chaos experiment
    log_info "Cleaning up chaos experiment..."
    kubectl delete -f "$file" -n "$NAMESPACE" 2>/dev/null || true
    
    # Measure recovery time
    log_info "Measuring recovery time (max: ${max_recovery}s)..."
    local recovery_time
    recovery_time=$(measure_recovery_time "nexus" "$max_recovery") || true
    
    # Verify system functionality
    log_info "Verifying system functionality..."
    local temporal_status
    temporal_status=$(check_temporal_slice)
    
    local golden_status
    golden_status=$(check_golden_loop)
    
    # Evaluate results
    if [ "$recovery_time" -lt "$max_recovery" ] && [ "$temporal_status" != "ERROR" ]; then
        log_info "PASS: Recovery time ${recovery_time}s, Temporal: $temporal_status"
        EXPERIMENT_RESULTS["$name"]="PASS:${recovery_time}:${temporal_status}"
        ((PASSED_EXPERIMENTS++))
        return 0
    else
        log_error "FAIL: Recovery time ${recovery_time}s (max: ${max_recovery}s), Temporal: $temporal_status"
        EXPERIMENT_RESULTS["$name"]="FAIL:${recovery_time}:${temporal_status}"
        ((FAILED_EXPERIMENTS++))
        return 1
    fi
}

# =============================================================================
# Experiment Categories
# =============================================================================

run_service_experiments() {
    log_section "SERVICE POD FAILURE EXPERIMENTS"
    
    # CAPSULE pod failure
    if [ -f "${CHAOS_DIR}/capsule-outage.yaml" ]; then
        run_experiment "capsule-pod-failure" "${CHAOS_DIR}/capsule-outage.yaml" "$MAX_RECOVERY_TIME_SERVICE" || true
    fi
    
    # NEXUS partial degradation
    if [ -f "${K8S_CHAOS_DIR}/nexus-partial-degradation.yaml" ]; then
        run_experiment "nexus-partial-degradation" "${K8S_CHAOS_DIR}/nexus-partial-degradation.yaml" "$MAX_RECOVERY_TIME_SERVICE" || true
    fi
    
    # PLATO latency spike
    if [ -f "${K8S_CHAOS_DIR}/plato-latency-spike.yaml" ]; then
        run_experiment "plato-latency-spike" "${K8S_CHAOS_DIR}/plato-latency-spike.yaml" "$MAX_RECOVERY_TIME_SERVICE" || true
    fi
    
    # ODYSSEY down
    if [ -f "${CHAOS_DIR}/odyssey-down.yaml" ]; then
        run_experiment "odyssey-failure" "${CHAOS_DIR}/odyssey-down.yaml" "$MAX_RECOVERY_TIME_SERVICE" || true
    fi
}

run_infrastructure_experiments() {
    log_section "INFRASTRUCTURE FAILOVER EXPERIMENTS"
    
    # Kafka broker loss
    if [ -f "${CHAOS_DIR}/kafka-broker-loss.yaml" ]; then
        run_experiment "kafka-broker-loss" "${CHAOS_DIR}/kafka-broker-loss.yaml" "$MAX_RECOVERY_TIME_INFRA" || true
    fi
    
    # Cassandra node failure (simulated via CAPSULE degradation)
    if [ -f "${K8S_CHAOS_DIR}/capsule-degraded.yaml" ]; then
        run_experiment "cassandra-degraded" "${K8S_CHAOS_DIR}/capsule-degraded.yaml" "$MAX_RECOVERY_TIME_INFRA" || true
    fi
}

run_network_experiments() {
    log_section "NETWORK PARTITION EXPERIMENTS"
    
    # PERCEPTION delay
    if [ -f "${CHAOS_DIR}/perception-delay.yaml" ]; then
        run_experiment "perception-network-delay" "${CHAOS_DIR}/perception-delay.yaml" "$MAX_RECOVERY_TIME_NETWORK" || true
    fi
}

run_full_ecosystem_test() {
    log_section "FULL ECOSYSTEM RESILIENCE"
    
    if [ -f "${K8S_CHAOS_DIR}/full-ecosystem-resilience.yaml" ]; then
        run_experiment "full-ecosystem-resilience" "${K8S_CHAOS_DIR}/full-ecosystem-resilience.yaml" "$MAX_RECOVERY_TIME_INFRA" || true
    fi
}

# =============================================================================
# Main Execution
# =============================================================================

log_info "============================================="
log_info "BUTTERFLY HA Failover Validation"
log_info "============================================="
log_info "Namespace: $NAMESPACE"
log_info "Category:  $CATEGORY"
log_info "Dry Run:   $DRY_RUN"
log_info "============================================="

check_prerequisites

case "$CATEGORY" in
    service)
        run_service_experiments
        ;;
    infra)
        run_infrastructure_experiments
        ;;
    network)
        run_network_experiments
        ;;
    all)
        run_service_experiments
        run_infrastructure_experiments
        run_network_experiments
        run_full_ecosystem_test
        ;;
    *)
        log_error "Unknown category: $CATEGORY"
        exit 2
        ;;
esac

# =============================================================================
# Generate Report
# =============================================================================

PASS_RATE=0
if [ $TOTAL_EXPERIMENTS -gt 0 ]; then
    PASS_RATE=$((PASSED_EXPERIMENTS * 100 / TOTAL_EXPERIMENTS))
fi

if [ -n "$REPORT_FILE" ]; then
    mkdir -p "$(dirname "$REPORT_FILE")"
    
    cat > "$REPORT_FILE" << EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "namespace": "$NAMESPACE",
  "category": "$CATEGORY",
  "dry_run": $DRY_RUN,
  "summary": {
    "total_experiments": $TOTAL_EXPERIMENTS,
    "passed": $PASSED_EXPERIMENTS,
    "failed": $FAILED_EXPERIMENTS,
    "pass_rate": $PASS_RATE
  },
  "thresholds": {
    "max_recovery_service_seconds": $MAX_RECOVERY_TIME_SERVICE,
    "max_recovery_infra_seconds": $MAX_RECOVERY_TIME_INFRA,
    "max_recovery_network_seconds": $MAX_RECOVERY_TIME_NETWORK
  },
  "experiments": {
EOF

    first=true
    for name in "${!EXPERIMENT_RESULTS[@]}"; do
        result="${EXPERIMENT_RESULTS[$name]}"
        status="${result%%:*}"
        rest="${result#*:}"
        recovery="${rest%%:*}"
        detail="${rest#*:}"
        
        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$REPORT_FILE"
        fi
        
        echo -n "    \"$name\": {\"status\": \"$status\", \"recovery_seconds\": $recovery, \"detail\": \"$detail\"}" >> "$REPORT_FILE"
    done

    cat >> "$REPORT_FILE" << EOF

  }
}
EOF

    log_info "Report saved to: $REPORT_FILE"
fi

# =============================================================================
# Summary
# =============================================================================

echo ""
log_info "============================================="
log_info "HA FAILOVER VALIDATION SUMMARY"
log_info "============================================="
log_info "Total Experiments: $TOTAL_EXPERIMENTS"
log_info "Passed:            $PASSED_EXPERIMENTS"
log_info "Failed:            $FAILED_EXPERIMENTS"
log_info "Pass Rate:         ${PASS_RATE}%"
log_info "============================================="

echo ""
echo "Experiment Results:"
for name in "${!EXPERIMENT_RESULTS[@]}"; do
    result="${EXPERIMENT_RESULTS[$name]}"
    status="${result%%:*}"
    case "$status" in
        PASS) echo -e "  ${GREEN}[✓]${NC} $name" ;;
        FAIL) echo -e "  ${RED}[✗]${NC} $name" ;;
        SKIP) echo -e "  ${YELLOW}[~]${NC} $name (skipped)" ;;
    esac
done

echo ""

if [ $FAILED_EXPERIMENTS -eq 0 ]; then
    log_info "HA FAILOVER VALIDATION: PASSED"
    exit 0
else
    log_error "HA FAILOVER VALIDATION: FAILED"
    exit 1
fi

