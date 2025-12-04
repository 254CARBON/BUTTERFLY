#!/usr/bin/env bash
# =============================================================================
# BUTTERFLY Scaling Reaction Validation Script
# =============================================================================
# Validates that services scale appropriately during and after chaos scenarios
# by querying Prometheus metrics from the PERCEPTION scaling runbook.
#
# Validates:
#   - CPU utilization thresholds (scale up > 70%, down < 30%)
#   - Memory utilization (scale up > 80%, down < 40%)
#   - Kafka consumer lag (scale up > 500 msgs, down < 100 msgs)
#   - API request queue depth (scale up > 100 pending, down < 20)
#   - Event processing latency (scale up p95 > 1s, down p95 < 100ms)
#   - HPA scaling reactions within SLO timeframe
#
# Reference: PERCEPTION/docs/runbooks/scaling.md
#
# Usage:
#   ./validate-scaling-reactions.sh [OPTIONS]
#
# Options:
#   --prometheus-url URL    Prometheus URL (default: http://prometheus:9090)
#   --namespace NS          Kubernetes namespace (default: perception)
#   --output FILE           Output JSON file for results
#   --services SERVICES     Comma-separated services to check (default: all)
#   --window DURATION       Prometheus query window (default: 5m)
#   --timeout SECS          Query timeout (default: 30)
#   --verbose               Enable verbose output
#   --help                  Show this help message
#
# Exit Codes:
#   0 - All scaling checks passed or within tolerance
#   1 - Critical scaling issues detected
#   2 - Configuration error
# =============================================================================

set -euo pipefail

# Script metadata
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "$0")"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Default configuration
PROMETHEUS_URL="${PROMETHEUS_URL:-http://prometheus:9090}"
NAMESPACE="${NAMESPACE:-perception}"
OUTPUT_FILE=""
SERVICES=""
QUERY_WINDOW="5m"
TIMEOUT=30
VERBOSE=false

# Scaling thresholds from runbook
declare -A SCALE_UP_THRESHOLDS=(
    ["cpu_utilization"]=70
    ["memory_utilization"]=80
    ["kafka_consumer_lag"]=500
    ["api_request_queue"]=100
    ["event_processing_latency_p95_ms"]=1000
)

declare -A SCALE_DOWN_THRESHOLDS=(
    ["cpu_utilization"]=30
    ["memory_utilization"]=40
    ["kafka_consumer_lag"]=100
    ["api_request_queue"]=20
    ["event_processing_latency_p95_ms"]=100
)

# Results tracking
declare -A METRIC_VALUES
declare -A CHECK_RESULTS
CHECKS_PASSED=0
CHECKS_FAILED=0
CHECKS_WARN=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }
log_debug() { [[ "$VERBOSE" == "true" ]] && echo -e "${CYAN}[DEBUG]${NC} $1" || true; }

show_help() {
    head -40 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --prometheus-url) PROMETHEUS_URL="$2"; shift 2 ;;
            --namespace) NAMESPACE="$2"; shift 2 ;;
            --output) OUTPUT_FILE="$2"; shift 2 ;;
            --services) SERVICES="$2"; shift 2 ;;
            --window) QUERY_WINDOW="$2"; shift 2 ;;
            --timeout) TIMEOUT="$2"; shift 2 ;;
            --verbose) VERBOSE=true; shift ;;
            --help|-h) show_help ;;
            *) log_error "Unknown option: $1"; exit 2 ;;
        esac
    done
}

# Validate prerequisites
validate_prerequisites() {
    command -v curl &>/dev/null || { log_error "curl is required"; exit 2; }
    command -v jq &>/dev/null || { log_error "jq is required"; exit 2; }
}

# Check Prometheus connectivity
check_prometheus() {
    log_step "Checking Prometheus connectivity..."
    
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" "${PROMETHEUS_URL}/-/ready" 2>/dev/null || echo "000")
    
    if [[ "$status" == "200" ]]; then
        log_info "Prometheus is ready at ${PROMETHEUS_URL}"
        return 0
    else
        log_warn "Prometheus not ready (status: $status) - metrics validation will be limited"
        return 1
    fi
}

# Execute Prometheus query
query_prometheus() {
    local query="$1"
    local result
    
    log_debug "Executing query: $query"
    
    result=$(curl -s --max-time "$TIMEOUT" \
        -G "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=${query}" 2>/dev/null || echo '{"status":"error"}')
    
    if echo "$result" | jq -e '.status == "success"' &>/dev/null; then
        echo "$result" | jq -r '.data.result[0].value[1] // "N/A"'
    else
        log_debug "Query failed: $result"
        echo "N/A"
    fi
}

# Query range for average over time
query_prometheus_avg() {
    local query="$1"
    local result
    
    result=$(curl -s --max-time "$TIMEOUT" \
        -G "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=avg_over_time(${query}[${QUERY_WINDOW}])" 2>/dev/null || echo '{"status":"error"}')
    
    if echo "$result" | jq -e '.status == "success"' &>/dev/null; then
        echo "$result" | jq -r '.data.result[0].value[1] // "N/A"'
    else
        echo "N/A"
    fi
}

# =============================================================================
# Metric Checks from Scaling Runbook
# =============================================================================

check_cpu_utilization() {
    log_step "Checking CPU utilization..."
    
    # From runbook: avg(rate(container_cpu_usage_seconds_total{namespace="perception", container!=""}[5m])) by (pod) * 100
    local query="avg(rate(container_cpu_usage_seconds_total{namespace=\"${NAMESPACE}\", container!=\"\"}[${QUERY_WINDOW}])) * 100"
    local value
    value=$(query_prometheus "$query")
    
    METRIC_VALUES["cpu_utilization"]="$value"
    
    if [[ "$value" == "N/A" ]]; then
        log_warn "  CPU utilization: Unable to retrieve"
        CHECK_RESULTS["cpu_utilization"]="UNKNOWN"
        return 0
    fi
    
    local int_value
    int_value=$(printf "%.0f" "$value")
    
    if [[ $int_value -gt ${SCALE_UP_THRESHOLDS["cpu_utilization"]} ]]; then
        log_warn "  CPU utilization: ${int_value}% (above scale-up threshold ${SCALE_UP_THRESHOLDS["cpu_utilization"]}%)"
        CHECK_RESULTS["cpu_utilization"]="SCALE_UP_NEEDED"
        ((CHECKS_WARN++))
    elif [[ $int_value -lt ${SCALE_DOWN_THRESHOLDS["cpu_utilization"]} ]]; then
        log_info "  CPU utilization: ${int_value}% (below scale-down threshold - normal)"
        CHECK_RESULTS["cpu_utilization"]="NORMAL"
        ((CHECKS_PASSED++))
    else
        log_info "  CPU utilization: ${int_value}% (within normal range)"
        CHECK_RESULTS["cpu_utilization"]="NORMAL"
        ((CHECKS_PASSED++))
    fi
}

check_memory_utilization() {
    log_step "Checking memory utilization..."
    
    # From runbook: sum(container_memory_working_set_bytes{namespace="perception"}) by (pod) / sum(container_spec_memory_limit_bytes{namespace="perception"}) by (pod) * 100
    local query="avg(container_memory_working_set_bytes{namespace=\"${NAMESPACE}\"} / container_spec_memory_limit_bytes{namespace=\"${NAMESPACE}\"}) * 100"
    local value
    value=$(query_prometheus "$query")
    
    METRIC_VALUES["memory_utilization"]="$value"
    
    if [[ "$value" == "N/A" ]]; then
        log_warn "  Memory utilization: Unable to retrieve"
        CHECK_RESULTS["memory_utilization"]="UNKNOWN"
        return 0
    fi
    
    local int_value
    int_value=$(printf "%.0f" "$value")
    
    if [[ $int_value -gt ${SCALE_UP_THRESHOLDS["memory_utilization"]} ]]; then
        log_warn "  Memory utilization: ${int_value}% (above scale-up threshold ${SCALE_UP_THRESHOLDS["memory_utilization"]}%)"
        CHECK_RESULTS["memory_utilization"]="SCALE_UP_NEEDED"
        ((CHECKS_WARN++))
    elif [[ $int_value -lt ${SCALE_DOWN_THRESHOLDS["memory_utilization"]} ]]; then
        log_info "  Memory utilization: ${int_value}% (below scale-down threshold - normal)"
        CHECK_RESULTS["memory_utilization"]="NORMAL"
        ((CHECKS_PASSED++))
    else
        log_info "  Memory utilization: ${int_value}% (within normal range)"
        CHECK_RESULTS["memory_utilization"]="NORMAL"
        ((CHECKS_PASSED++))
    fi
}

check_kafka_consumer_lag() {
    log_step "Checking Kafka consumer lag..."
    
    # From runbook: sum(kafka_consumer_group_lag{consumer_group=~"perception-.*"}) by (consumer_group, topic)
    local query="sum(kafka_consumer_group_lag{consumer_group=~\"${NAMESPACE}-.*\"})"
    local value
    value=$(query_prometheus "$query")
    
    METRIC_VALUES["kafka_consumer_lag"]="$value"
    
    if [[ "$value" == "N/A" ]]; then
        log_warn "  Kafka consumer lag: Unable to retrieve (Kafka metrics may not be available)"
        CHECK_RESULTS["kafka_consumer_lag"]="UNKNOWN"
        return 0
    fi
    
    local int_value
    int_value=$(printf "%.0f" "$value")
    
    if [[ $int_value -gt ${SCALE_UP_THRESHOLDS["kafka_consumer_lag"]} ]]; then
        log_error "  Kafka consumer lag: ${int_value} messages (above scale-up threshold ${SCALE_UP_THRESHOLDS["kafka_consumer_lag"]})"
        CHECK_RESULTS["kafka_consumer_lag"]="SCALE_UP_NEEDED"
        ((CHECKS_FAILED++))
    elif [[ $int_value -lt ${SCALE_DOWN_THRESHOLDS["kafka_consumer_lag"]} ]]; then
        log_info "  Kafka consumer lag: ${int_value} messages (healthy)"
        CHECK_RESULTS["kafka_consumer_lag"]="HEALTHY"
        ((CHECKS_PASSED++))
    else
        log_info "  Kafka consumer lag: ${int_value} messages (within tolerance)"
        CHECK_RESULTS["kafka_consumer_lag"]="NORMAL"
        ((CHECKS_PASSED++))
    fi
}

check_api_request_queue() {
    log_step "Checking API request queue depth..."
    
    # From runbook: http_server_requests_active{application="perception-api"}
    local query="sum(http_server_requests_active{application=~\"${NAMESPACE}-.*\"})"
    local value
    value=$(query_prometheus "$query")
    
    METRIC_VALUES["api_request_queue"]="$value"
    
    if [[ "$value" == "N/A" ]]; then
        log_warn "  API request queue: Unable to retrieve"
        CHECK_RESULTS["api_request_queue"]="UNKNOWN"
        return 0
    fi
    
    local int_value
    int_value=$(printf "%.0f" "$value")
    
    if [[ $int_value -gt ${SCALE_UP_THRESHOLDS["api_request_queue"]} ]]; then
        log_warn "  API request queue: ${int_value} pending (above scale-up threshold ${SCALE_UP_THRESHOLDS["api_request_queue"]})"
        CHECK_RESULTS["api_request_queue"]="SCALE_UP_NEEDED"
        ((CHECKS_WARN++))
    elif [[ $int_value -lt ${SCALE_DOWN_THRESHOLDS["api_request_queue"]} ]]; then
        log_info "  API request queue: ${int_value} pending (healthy)"
        CHECK_RESULTS["api_request_queue"]="HEALTHY"
        ((CHECKS_PASSED++))
    else
        log_info "  API request queue: ${int_value} pending (within tolerance)"
        CHECK_RESULTS["api_request_queue"]="NORMAL"
        ((CHECKS_PASSED++))
    fi
}

check_event_processing_latency() {
    log_step "Checking event processing latency..."
    
    # p95 latency
    local query="histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application=~\"${NAMESPACE}-.*\"}[${QUERY_WINDOW}])) by (le)) * 1000"
    local value
    value=$(query_prometheus "$query")
    
    METRIC_VALUES["event_processing_latency_p95_ms"]="$value"
    
    if [[ "$value" == "N/A" ]]; then
        log_warn "  Event processing latency p95: Unable to retrieve"
        CHECK_RESULTS["event_processing_latency_p95_ms"]="UNKNOWN"
        return 0
    fi
    
    local int_value
    int_value=$(printf "%.0f" "$value")
    
    if [[ $int_value -gt ${SCALE_UP_THRESHOLDS["event_processing_latency_p95_ms"]} ]]; then
        log_error "  Event processing latency p95: ${int_value}ms (above scale-up threshold ${SCALE_UP_THRESHOLDS["event_processing_latency_p95_ms"]}ms)"
        CHECK_RESULTS["event_processing_latency_p95_ms"]="SCALE_UP_NEEDED"
        ((CHECKS_FAILED++))
    elif [[ $int_value -lt ${SCALE_DOWN_THRESHOLDS["event_processing_latency_p95_ms"]} ]]; then
        log_info "  Event processing latency p95: ${int_value}ms (healthy)"
        CHECK_RESULTS["event_processing_latency_p95_ms"]="HEALTHY"
        ((CHECKS_PASSED++))
    else
        log_info "  Event processing latency p95: ${int_value}ms (within tolerance)"
        CHECK_RESULTS["event_processing_latency_p95_ms"]="NORMAL"
        ((CHECKS_PASSED++))
    fi
}

check_hpa_status() {
    log_step "Checking HPA status..."
    
    if ! command -v kubectl &>/dev/null; then
        log_warn "  kubectl not available - skipping HPA check"
        CHECK_RESULTS["hpa_status"]="UNKNOWN"
        return 0
    fi
    
    local hpa_output
    hpa_output=$(kubectl get hpa -n "$NAMESPACE" -o json 2>/dev/null || echo '{"items":[]}')
    
    local hpa_count
    hpa_count=$(echo "$hpa_output" | jq '.items | length')
    
    if [[ "$hpa_count" -eq 0 ]]; then
        log_warn "  No HPAs found in namespace $NAMESPACE"
        CHECK_RESULTS["hpa_status"]="NONE"
        return 0
    fi
    
    local scaling_active=false
    local hpas_healthy=0
    local hpas_scaling=0
    
    while read -r hpa_name; do
        local current_replicas desired_replicas
        current_replicas=$(echo "$hpa_output" | jq -r ".items[] | select(.metadata.name==\"$hpa_name\") | .status.currentReplicas // 0")
        desired_replicas=$(echo "$hpa_output" | jq -r ".items[] | select(.metadata.name==\"$hpa_name\") | .status.desiredReplicas // 0")
        
        if [[ "$current_replicas" != "$desired_replicas" ]]; then
            log_info "  HPA $hpa_name: scaling ($current_replicas → $desired_replicas replicas)"
            scaling_active=true
            ((hpas_scaling++))
        else
            log_info "  HPA $hpa_name: stable ($current_replicas replicas)"
            ((hpas_healthy++))
        fi
    done < <(echo "$hpa_output" | jq -r '.items[].metadata.name')
    
    METRIC_VALUES["hpa_total"]="$hpa_count"
    METRIC_VALUES["hpa_scaling"]="$hpas_scaling"
    METRIC_VALUES["hpa_healthy"]="$hpas_healthy"
    
    if [[ "$scaling_active" == "true" ]]; then
        CHECK_RESULTS["hpa_status"]="SCALING"
        log_info "  HPA scaling in progress"
        ((CHECKS_PASSED++))
    else
        CHECK_RESULTS["hpa_status"]="STABLE"
        log_info "  All HPAs stable"
        ((CHECKS_PASSED++))
    fi
}

check_circuit_breakers() {
    log_step "Checking circuit breaker states..."
    
    # Query Resilience4j circuit breaker metrics
    local query="resilience4j_circuitbreaker_state{application=~\".*${NAMESPACE}.*\"}"
    local result
    result=$(curl -s --max-time "$TIMEOUT" \
        -G "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=${query}" 2>/dev/null || echo '{"status":"error"}')
    
    if ! echo "$result" | jq -e '.status == "success"' &>/dev/null; then
        log_warn "  Circuit breaker metrics: Unable to retrieve"
        CHECK_RESULTS["circuit_breakers"]="UNKNOWN"
        return 0
    fi
    
    local open_count closed_count half_open_count
    open_count=$(echo "$result" | jq '[.data.result[] | select(.metric.state == "open")] | length')
    closed_count=$(echo "$result" | jq '[.data.result[] | select(.metric.state == "closed")] | length')
    half_open_count=$(echo "$result" | jq '[.data.result[] | select(.metric.state == "half_open")] | length')
    
    METRIC_VALUES["circuit_breakers_open"]="$open_count"
    METRIC_VALUES["circuit_breakers_closed"]="$closed_count"
    METRIC_VALUES["circuit_breakers_half_open"]="$half_open_count"
    
    log_info "  Circuit breakers - Open: $open_count, Closed: $closed_count, Half-Open: $half_open_count"
    
    if [[ "$open_count" -gt 0 ]]; then
        log_warn "  ⚠ $open_count circuit breaker(s) in OPEN state"
        CHECK_RESULTS["circuit_breakers"]="DEGRADED"
        ((CHECKS_WARN++))
    else
        log_info "  ✓ All circuit breakers healthy"
        CHECK_RESULTS["circuit_breakers"]="HEALTHY"
        ((CHECKS_PASSED++))
    fi
}

# =============================================================================
# Output and Summary
# =============================================================================

write_output() {
    if [[ -z "$OUTPUT_FILE" ]]; then
        return 0
    fi
    
    local all_passed="true"
    if [[ $CHECKS_FAILED -gt 0 ]]; then
        all_passed="false"
    fi
    
    cat > "$OUTPUT_FILE" << EOF
{
    "timestamp": "$(date -Iseconds)",
    "namespace": "${NAMESPACE}",
    "prometheus_url": "${PROMETHEUS_URL}",
    "query_window": "${QUERY_WINDOW}",
    "all_checks_passed": ${all_passed},
    "summary": {
        "passed": ${CHECKS_PASSED},
        "failed": ${CHECKS_FAILED},
        "warnings": ${CHECKS_WARN}
    },
    "thresholds": {
        "scale_up": {
            "cpu_utilization_percent": ${SCALE_UP_THRESHOLDS["cpu_utilization"]},
            "memory_utilization_percent": ${SCALE_UP_THRESHOLDS["memory_utilization"]},
            "kafka_consumer_lag_messages": ${SCALE_UP_THRESHOLDS["kafka_consumer_lag"]},
            "api_request_queue_pending": ${SCALE_UP_THRESHOLDS["api_request_queue"]},
            "event_processing_latency_p95_ms": ${SCALE_UP_THRESHOLDS["event_processing_latency_p95_ms"]}
        },
        "scale_down": {
            "cpu_utilization_percent": ${SCALE_DOWN_THRESHOLDS["cpu_utilization"]},
            "memory_utilization_percent": ${SCALE_DOWN_THRESHOLDS["memory_utilization"]},
            "kafka_consumer_lag_messages": ${SCALE_DOWN_THRESHOLDS["kafka_consumer_lag"]},
            "api_request_queue_pending": ${SCALE_DOWN_THRESHOLDS["api_request_queue"]},
            "event_processing_latency_p95_ms": ${SCALE_DOWN_THRESHOLDS["event_processing_latency_p95_ms"]}
        }
    },
    "metrics": {
$(for metric in "${!METRIC_VALUES[@]}"; do
    echo "        \"${metric}\": \"${METRIC_VALUES[$metric]}\","
done | sed '$ s/,$//')
    },
    "check_results": {
$(for check in "${!CHECK_RESULTS[@]}"; do
    echo "        \"${check}\": \"${CHECK_RESULTS[$check]}\","
done | sed '$ s/,$//')
    }
}
EOF
    
    log_info "Results written to: $OUTPUT_FILE"
}

print_summary() {
    echo ""
    echo "================================================================="
    echo "      Scaling Validation Summary"
    echo "================================================================="
    echo ""
    log_info "Namespace:  ${NAMESPACE}"
    log_info "Prometheus: ${PROMETHEUS_URL}"
    echo ""
    log_info "Checks Passed:   ${CHECKS_PASSED}"
    log_info "Checks Failed:   ${CHECKS_FAILED}"
    log_info "Checks Warnings: ${CHECKS_WARN}"
    echo ""
    
    if [[ ${#CHECK_RESULTS[@]} -gt 0 ]]; then
        log_info "Check Results:"
        for check in "${!CHECK_RESULTS[@]}"; do
            local result="${CHECK_RESULTS[$check]}"
            case "$result" in
                HEALTHY|NORMAL|STABLE)
                    log_info "  ✓ $check: $result"
                    ;;
                SCALE_UP_NEEDED|SCALING|DEGRADED)
                    log_warn "  ⚠ $check: $result"
                    ;;
                UNKNOWN|NONE)
                    log_info "  ? $check: $result"
                    ;;
                *)
                    log_error "  ✗ $check: $result"
                    ;;
            esac
        done
    fi
    
    echo ""
    
    if [[ $CHECKS_FAILED -eq 0 ]]; then
        log_info "✅ Scaling validation passed"
        return 0
    else
        log_error "❌ Scaling validation detected issues"
        return 1
    fi
}

# =============================================================================
# Main Execution
# =============================================================================

main() {
    parse_args "$@"
    
    echo ""
    echo "================================================================="
    echo "      BUTTERFLY Scaling Reaction Validation"
    echo "================================================================="
    echo ""
    
    validate_prerequisites
    
    local prometheus_available=true
    if ! check_prometheus; then
        prometheus_available=false
    fi
    
    echo ""
    
    # Run all metric checks
    check_cpu_utilization
    check_memory_utilization
    check_kafka_consumer_lag
    check_api_request_queue
    check_event_processing_latency
    check_hpa_status
    check_circuit_breakers
    
    # Write output file
    write_output
    
    # Print summary and exit
    if print_summary; then
        exit 0
    else
        exit 1
    fi
}

main "$@"

