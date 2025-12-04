#!/bin/bash
# =============================================================================
# BUTTERFLY 24-Hour Soak Test
# =============================================================================
# Executes extended stability testing over a configurable duration.
# Monitors SLO compliance, DLQ activity, and system stability.
#
# Usage:
#   ./run-soak-test.sh [OPTIONS]
#
# Options:
#   --duration DURATION     Test duration (default: 24h, format: Nh or Nm)
#   --prometheus-url URL    Prometheus URL (default: http://localhost:9090)
#   --pushgateway-url URL   Pushgateway URL for metrics (optional)
#   --interval SECONDS      Check interval in seconds (default: 300)
#   --report FILE           Output JSON report file
#   --alert-webhook URL     Webhook URL for alerts
#   --help                  Show this help message
#
# Exit Codes:
#   0 - Soak test passed
#   1 - Soak test failed (SLO violations)
#   2 - Configuration error
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Configuration
DURATION="${DURATION:-24h}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
PUSHGATEWAY_URL="${PUSHGATEWAY_URL:-}"
CHECK_INTERVAL="${CHECK_INTERVAL:-300}"
REPORT_FILE="${REPORT_FILE:-}"
ALERT_WEBHOOK="${ALERT_WEBHOOK:-}"
NEXUS_URL="${NEXUS_URL:-http://localhost:8084}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Tracking
START_TIME=$(date +%s)
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
SLO_VIOLATIONS=0
DLQ_MESSAGES=0
CIRCUIT_BREAKER_OPENS=0

log_info() { echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_check() { echo -e "${BLUE}[CHECK]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }

show_help() {
    head -25 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse duration to seconds
parse_duration() {
    local duration="$1"
    local value="${duration%[hm]}"
    local unit="${duration: -1}"
    
    case "$unit" in
        h) echo $((value * 3600)) ;;
        m) echo $((value * 60)) ;;
        *) echo "$value" ;;
    esac
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --duration) DURATION="$2"; shift 2 ;;
        --prometheus-url) PROMETHEUS_URL="$2"; shift 2 ;;
        --pushgateway-url) PUSHGATEWAY_URL="$2"; shift 2 ;;
        --interval) CHECK_INTERVAL="$2"; shift 2 ;;
        --report) REPORT_FILE="$2"; shift 2 ;;
        --alert-webhook) ALERT_WEBHOOK="$2"; shift 2 ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 2 ;;
    esac
done

DURATION_SECONDS=$(parse_duration "$DURATION")
END_TIME=$((START_TIME + DURATION_SECONDS))

# =============================================================================
# Helper Functions
# =============================================================================

query_prometheus() {
    local query="$1"
    local encoded_query
    encoded_query=$(echo "$query" | jq -sRr @uri)
    
    local result
    result=$(curl -sf "${PROMETHEUS_URL}/api/v1/query?query=${encoded_query}" 2>/dev/null || echo '{"status":"error"}')
    
    if echo "$result" | jq -e '.status == "success"' > /dev/null 2>&1; then
        echo "$result" | jq -r '.data.result[0].value[1] // "N/A"'
    else
        echo "N/A"
    fi
}

send_alert() {
    local message="$1"
    local severity="${2:-warning}"
    
    if [ -n "$ALERT_WEBHOOK" ]; then
        curl -sf -X POST "$ALERT_WEBHOOK" \
            -H "Content-Type: application/json" \
            -d "{\"text\": \"[BUTTERFLY Soak Test] [$severity] $message\"}" || true
    fi
}

push_metrics() {
    if [ -n "$PUSHGATEWAY_URL" ]; then
        local elapsed=$(($(date +%s) - START_TIME))
        
        cat << EOF | curl -sf --data-binary @- "${PUSHGATEWAY_URL}/metrics/job/butterfly_soak_test/instance/e2e" || true
# HELP butterfly_soak_test_elapsed_seconds Soak test elapsed time
# TYPE butterfly_soak_test_elapsed_seconds gauge
butterfly_soak_test_elapsed_seconds $elapsed
# HELP butterfly_soak_test_total_checks Total SLO checks performed
# TYPE butterfly_soak_test_total_checks counter
butterfly_soak_test_total_checks $TOTAL_CHECKS
# HELP butterfly_soak_test_passed_checks Passed SLO checks
# TYPE butterfly_soak_test_passed_checks counter
butterfly_soak_test_passed_checks $PASSED_CHECKS
# HELP butterfly_soak_test_failed_checks Failed SLO checks
# TYPE butterfly_soak_test_failed_checks counter
butterfly_soak_test_failed_checks $FAILED_CHECKS
# HELP butterfly_soak_test_slo_violations Total SLO violations
# TYPE butterfly_soak_test_slo_violations counter
butterfly_soak_test_slo_violations $SLO_VIOLATIONS
# HELP butterfly_soak_test_dlq_messages Total DLQ messages seen
# TYPE butterfly_soak_test_dlq_messages counter
butterfly_soak_test_dlq_messages $DLQ_MESSAGES
# HELP butterfly_soak_test_circuit_breaker_opens Circuit breaker open events
# TYPE butterfly_soak_test_circuit_breaker_opens counter
butterfly_soak_test_circuit_breaker_opens $CIRCUIT_BREAKER_OPENS
EOF
    fi
}

# =============================================================================
# SLO Check Functions
# =============================================================================

check_temporal_slos() {
    local p95_value
    p95_value=$(query_prometheus "histogram_quantile(0.95, rate(nexus_temporal_slice_query_duration_bucket[5m])) * 1000")
    
    if [ "$p95_value" != "N/A" ] && [ "$p95_value" != "null" ]; then
        local p95_int
        p95_int=$(printf "%.0f" "$p95_value")
        
        if [ "$p95_int" -gt 100 ]; then
            log_warn "SLO Violation: Temporal P95 = ${p95_int}ms (> 100ms)"
            ((SLO_VIOLATIONS++))
            send_alert "Temporal Slice P95 latency is ${p95_int}ms (target: < 100ms)" "warning"
            return 1
        else
            log_check "Temporal P95: ${p95_int}ms (OK)"
            return 0
        fi
    fi
    return 0
}

check_coherence_slo() {
    local coherence
    coherence=$(query_prometheus "avg(nexus_temporal_coherence_score)")
    
    if [ "$coherence" != "N/A" ] && [ "$coherence" != "null" ]; then
        if (( $(echo "$coherence < 0.7" | bc -l) )); then
            log_warn "SLO Violation: Coherence = $coherence (< 0.7)"
            ((SLO_VIOLATIONS++))
            send_alert "Cross-system coherence is $coherence (target: > 0.7)" "warning"
            return 1
        else
            log_check "Coherence: $coherence (OK)"
            return 0
        fi
    fi
    return 0
}

check_dlq_status() {
    # Count DLQ messages
    local dlq_count=0
    
    for topic in $(kafkacat -b localhost:9092 -L 2>/dev/null | grep "\.dlq$" | awk '{print $2}' || echo ""); do
        local count
        count=$(kafkacat -b localhost:9092 -t "$topic" -C -e -q 2>/dev/null | wc -l || echo "0")
        dlq_count=$((dlq_count + count))
    done
    
    if [ "$dlq_count" -gt 0 ]; then
        log_warn "DLQ Alert: $dlq_count messages found in DLQ topics"
        DLQ_MESSAGES=$((DLQ_MESSAGES + dlq_count))
        send_alert "$dlq_count messages found in DLQ topics" "error"
        return 1
    else
        log_check "DLQ: Clean (0 messages)"
        return 0
    fi
}

check_circuit_breakers() {
    local cb_response
    cb_response=$(curl -sf "${NEXUS_URL}/actuator/health" 2>/dev/null | jq -r '.components.circuitBreakers.details // {}')
    
    if echo "$cb_response" | grep -q '"state":"OPEN"'; then
        log_warn "Circuit Breaker Alert: Some breakers are OPEN"
        ((CIRCUIT_BREAKER_OPENS++))
        send_alert "Circuit breaker(s) in OPEN state" "error"
        return 1
    else
        log_check "Circuit Breakers: All CLOSED"
        return 0
    fi
}

check_service_health() {
    local services=("capsule:8080" "odyssey:8081" "perception:8082" "nexus:8084" "plato:8086" "synapse:8084")
    local all_healthy=true
    
    for service_port in "${services[@]}"; do
        local service="${service_port%%:*}"
        local port="${service_port##*:}"
        
        local health
        health=$(curl -sf "http://localhost:${port}/actuator/health" 2>/dev/null | jq -r '.status' || echo "DOWN")
        
        if [ "$health" != "UP" ]; then
            log_warn "Service unhealthy: $service"
            all_healthy=false
        fi
    done
    
    if [ "$all_healthy" = true ]; then
        log_check "All services: UP"
        return 0
    else
        send_alert "One or more services are unhealthy" "warning"
        return 1
    fi
}

# =============================================================================
# Main Soak Test Loop
# =============================================================================

log_info "============================================="
log_info "BUTTERFLY Soak Test"
log_info "============================================="
log_info "Duration:       $DURATION ($DURATION_SECONDS seconds)"
log_info "Check Interval: ${CHECK_INTERVAL} seconds"
log_info "Prometheus:     $PROMETHEUS_URL"
log_info "Start Time:     $(date)"
log_info "End Time:       $(date -d "@$END_TIME")"
log_info "============================================="

# Initial connectivity check
if ! curl -sf "${PROMETHEUS_URL}/-/healthy" > /dev/null 2>&1; then
    log_error "Cannot connect to Prometheus at ${PROMETHEUS_URL}"
    exit 2
fi

log_info "Starting soak test..."
send_alert "Soak test started - Duration: $DURATION" "info"

# Generate continuous load in background
log_info "Generating background load..."

(
    while [ "$(date +%s)" -lt "$END_TIME" ]; do
        # Simulate golden path queries
        curl -sf "${NEXUS_URL}/api/v1/temporal/slice" \
            -H "Content-Type: application/json" \
            -d '{"entityId": "rim:entity:test:soak", "timeframe": {"lookback": "PT1H"}}' \
            > /dev/null 2>&1 || true
        
        sleep $((RANDOM % 5 + 1))
    done
) &

LOAD_PID=$!

# Cleanup on exit
cleanup() {
    kill $LOAD_PID 2>/dev/null || true
    log_info "Soak test terminated"
}
trap cleanup EXIT

# Main check loop
while [ "$(date +%s)" -lt "$END_TIME" ]; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    REMAINING=$((END_TIME - CURRENT_TIME))
    
    HOURS=$((ELAPSED / 3600))
    MINUTES=$(((ELAPSED % 3600) / 60))
    
    echo ""
    log_info "--- Check #$((TOTAL_CHECKS + 1)) | Elapsed: ${HOURS}h ${MINUTES}m | Remaining: $((REMAINING / 60))m ---"
    
    ((TOTAL_CHECKS++))
    check_passed=true
    
    check_temporal_slos || check_passed=false
    check_coherence_slo || check_passed=false
    check_dlq_status || check_passed=false
    check_circuit_breakers || check_passed=false
    check_service_health || check_passed=false
    
    if [ "$check_passed" = true ]; then
        ((PASSED_CHECKS++))
    else
        ((FAILED_CHECKS++))
    fi
    
    # Push metrics
    push_metrics
    
    # Calculate pass rate
    PASS_RATE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
    log_info "Running Pass Rate: ${PASS_RATE}% ($PASSED_CHECKS/$TOTAL_CHECKS)"
    
    # Early termination on critical failure
    if [ $SLO_VIOLATIONS -gt 10 ]; then
        log_error "Too many SLO violations ($SLO_VIOLATIONS) - terminating soak test"
        send_alert "Soak test terminated due to excessive SLO violations" "error"
        break
    fi
    
    sleep "$CHECK_INTERVAL"
done

# =============================================================================
# Generate Report
# =============================================================================

ACTUAL_DURATION=$(($(date +%s) - START_TIME))
OVERALL_STATUS="PASS"

if [ $FAILED_CHECKS -gt 0 ] || [ $SLO_VIOLATIONS -gt 0 ] || [ $DLQ_MESSAGES -gt 0 ]; then
    OVERALL_STATUS="FAIL"
fi

if [ -n "$REPORT_FILE" ]; then
    mkdir -p "$(dirname "$REPORT_FILE")"
    
    cat > "$REPORT_FILE" << EOF
{
  "test": "soak_test",
  "start_time": "$(date -d "@$START_TIME" -u +'%Y-%m-%dT%H:%M:%SZ')",
  "end_time": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "planned_duration_seconds": $DURATION_SECONDS,
  "actual_duration_seconds": $ACTUAL_DURATION,
  "summary": {
    "overall_status": "$OVERALL_STATUS",
    "total_checks": $TOTAL_CHECKS,
    "passed_checks": $PASSED_CHECKS,
    "failed_checks": $FAILED_CHECKS,
    "pass_rate": $PASS_RATE,
    "slo_violations": $SLO_VIOLATIONS,
    "dlq_messages_total": $DLQ_MESSAGES,
    "circuit_breaker_opens": $CIRCUIT_BREAKER_OPENS
  },
  "thresholds": {
    "temporal_p95_ms": 100,
    "coherence_min": 0.7,
    "dlq_max": 0,
    "circuit_breaker_max_opens": 0
  },
  "configuration": {
    "check_interval_seconds": $CHECK_INTERVAL,
    "prometheus_url": "$PROMETHEUS_URL"
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
log_info "SOAK TEST COMPLETE"
log_info "============================================="
log_info "Duration:              $(($ACTUAL_DURATION / 3600))h $(($ACTUAL_DURATION % 3600 / 60))m"
log_info "Total Checks:          $TOTAL_CHECKS"
log_info "Passed Checks:         $PASSED_CHECKS"
log_info "Failed Checks:         $FAILED_CHECKS"
log_info "Pass Rate:             ${PASS_RATE}%"
log_info "SLO Violations:        $SLO_VIOLATIONS"
log_info "DLQ Messages:          $DLQ_MESSAGES"
log_info "Circuit Breaker Opens: $CIRCUIT_BREAKER_OPENS"
log_info "============================================="

if [ "$OVERALL_STATUS" = "PASS" ]; then
    log_info "SOAK TEST: PASSED"
    send_alert "Soak test completed successfully - Duration: $(($ACTUAL_DURATION / 3600))h, Pass Rate: ${PASS_RATE}%" "success"
    exit 0
else
    log_error "SOAK TEST: FAILED"
    send_alert "Soak test failed - SLO violations: $SLO_VIOLATIONS, DLQ messages: $DLQ_MESSAGES" "error"
    exit 1
fi

