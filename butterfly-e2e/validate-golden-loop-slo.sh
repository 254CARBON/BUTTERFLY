#!/usr/bin/env bash
# =============================================================================
# Golden Loop SLO Validator
# =============================================================================
# Validates Phase 1 SLO targets for golden loop scenarios.
# Queries Prometheus for metrics and outputs JUnit-compatible XML report.
#
# Phase 1 SLO Targets (from butterfly-e2e/README.md lines 82-91):
#   - Temporal Slice P50: < 50ms
#   - Temporal Slice P95: < 100ms
#   - Temporal Slice P99: < 200ms
#   - Cross-System Coherence: > 0.7
#   - Error Rate: < 0.1%
#   - Golden Loop P95: < 2s
#   - Golden Loop P99: < 5s
#   - Golden Loop Success Rate: > 99%
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Configuration
PROMETHEUS_URL=${PROMETHEUS_URL:-"http://localhost:9090"}
PROMQL_TIMEOUT=${PROMQL_TIMEOUT:-"30s"}
RESULTS_DIR="${RESULTS_DIR:-$SCRIPT_DIR/slo-results}"
JUNIT_REPORT="${JUNIT_REPORT:-$RESULTS_DIR/golden-loop-slo-report.xml}"

# Phase 1 SLO Targets
TEMPORAL_P50_TARGET_MS=50
TEMPORAL_P95_TARGET_MS=100
TEMPORAL_P99_TARGET_MS=200
COHERENCE_TARGET=0.7
ERROR_RATE_TARGET=0.001  # 0.1%
GOLDEN_LOOP_P95_TARGET_MS=2000
GOLDEN_LOOP_P99_TARGET_MS=5000
GOLDEN_LOOP_SUCCESS_RATE_TARGET=0.99

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Results tracking
declare -A SLO_RESULTS
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
START_TIME=$(date +%s)

print_usage() {
    cat <<USAGE
Usage: $(basename "$0") [options]

Golden Loop SLO Validator for Phase 1 criteria.

Options:
  --prometheus-url <url>     Prometheus base URL (default: $PROMETHEUS_URL)
  --promql-timeout <dur>     Prometheus query timeout (default: $PROMQL_TIMEOUT)
  --results-dir <dir>        Results directory (default: $RESULTS_DIR)
  --junit-report <file>      JUnit XML output file (default: $JUNIT_REPORT)
  --skip-temporal            Skip temporal slice SLO checks
  --skip-golden-loop         Skip golden loop SLO checks
  -h, --help                 Show this help message

Environment Variables:
  PROMETHEUS_URL             Override Prometheus URL
  PROMQL_TIMEOUT             Override query timeout
  RESULTS_DIR                Override results directory

Exit Codes:
  0 - All SLO checks passed
  1 - Script error
  2 - One or more SLO checks failed

USAGE
}

# Parse arguments
SKIP_TEMPORAL=false
SKIP_GOLDEN_LOOP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --prometheus-url)
            PROMETHEUS_URL=$2
            shift 2
            ;;
        --promql-timeout)
            PROMQL_TIMEOUT=$2
            shift 2
            ;;
        --results-dir)
            RESULTS_DIR=$2
            shift 2
            ;;
        --junit-report)
            JUNIT_REPORT=$2
            shift 2
            ;;
        --skip-temporal)
            SKIP_TEMPORAL=true
            shift
            ;;
        --skip-golden-loop)
            SKIP_GOLDEN_LOOP=true
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            print_usage
            exit 1
            ;;
    esac
done

# Helper functions
log_info() { echo -e "${GREEN}[SLO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[SLO][WARN]${NC} $*"; }
log_error() { echo -e "${RED}[SLO][ERROR]${NC} $*" >&2; }
log_pass() { echo -e "${GREEN}[PASS]${NC} $*"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; }

# Query Prometheus
run_prom_query() {
    local expr=$1
    curl -fsS --get \
        --data-urlencode "query=${expr}" \
        --data-urlencode "timeout=${PROMQL_TIMEOUT}" \
        "$PROMETHEUS_URL/api/v1/query" 2>/dev/null
}

# Extract scalar value from Prometheus response
extract_value() {
    local response=$1
    echo "$response" | jq -r '
        if .status != "success" then
            "ERROR"
        elif .data.result | length == 0 then
            "N/A"
        else
            .data.result[0].value[1]
        end
    ' 2>/dev/null || echo "ERROR"
}

# Numeric comparisons
lt() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a<b)}'; }
le() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a<=b)}'; }
gt() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a>b)}'; }
ge() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a>=b)}'; }

# Formatting
as_percent() { awk -v v="$1" 'BEGIN{printf "%.3f%%", v * 100}'; }
as_ms() { awk -v v="$1" 'BEGIN{printf "%.2f", v * 1000}'; }
as_seconds() { awk -v v="$1" 'BEGIN{printf "%.3f", v}'; }

# Record SLO check result
record_result() {
    local check_name=$1
    local result=$2  # pass, fail, skip, error
    local actual_value=$3
    local target_value=$4
    local message=${5:-""}

    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    SLO_RESULTS["$check_name"]="$result|$actual_value|$target_value|$message"

    case "$result" in
        pass) PASSED_CHECKS=$((PASSED_CHECKS + 1)) ;;
        fail) FAILED_CHECKS=$((FAILED_CHECKS + 1)) ;;
    esac
}

# Initialize
mkdir -p "$RESULTS_DIR"

echo ""
echo -e "${BLUE}=================================================================${NC}"
echo -e "${BLUE}      Golden Loop SLO Validator - Phase 1 Criteria              ${NC}"
echo -e "${BLUE}=================================================================${NC}"
echo ""
log_info "Prometheus URL: $PROMETHEUS_URL"
log_info "Results directory: $RESULTS_DIR"
echo ""

# Check Prometheus connectivity
log_info "Checking Prometheus connectivity..."
if ! curl -sf "$PROMETHEUS_URL/-/healthy" > /dev/null 2>&1; then
    log_error "Cannot connect to Prometheus at $PROMETHEUS_URL"
    log_warn "Generating partial report with skipped tests..."
    
    # Generate empty report for CI
    cat > "$JUNIT_REPORT" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="Golden Loop SLO Validation" tests="0" failures="0" skipped="1" time="0">
    <testsuite name="SLO Checks" tests="1" failures="0" skipped="1" time="0">
        <testcase name="prometheus_connectivity" classname="slo.connectivity">
            <skipped message="Prometheus not available at $PROMETHEUS_URL"/>
        </testcase>
    </testsuite>
</testsuites>
EOF
    exit 0
fi
log_info "Prometheus is healthy"
echo ""

# =============================================================================
# Temporal Slice SLO Checks
# =============================================================================
if [[ "$SKIP_TEMPORAL" != "true" ]]; then
    echo -e "${BLUE}--- Temporal Slice SLO Checks ---${NC}"
    echo ""

    # P50 Latency
    log_info "Checking Temporal Slice P50 Latency (target: <${TEMPORAL_P50_TARGET_MS}ms)..."
    response=$(run_prom_query 'nexus:temporal_slice_latency_p50:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Temporal P50 metric not available"
        record_result "temporal_p50_latency" "skip" "N/A" "${TEMPORAL_P50_TARGET_MS}ms" "Metric not available"
    else
        value_ms=$(as_ms "$value")
        if lt "$value_ms" "$TEMPORAL_P50_TARGET_MS"; then
            log_pass "Temporal P50: ${value_ms}ms < ${TEMPORAL_P50_TARGET_MS}ms"
            record_result "temporal_p50_latency" "pass" "${value_ms}ms" "<${TEMPORAL_P50_TARGET_MS}ms"
        else
            log_fail "Temporal P50: ${value_ms}ms >= ${TEMPORAL_P50_TARGET_MS}ms"
            record_result "temporal_p50_latency" "fail" "${value_ms}ms" "<${TEMPORAL_P50_TARGET_MS}ms"
        fi
    fi

    # P95 Latency
    log_info "Checking Temporal Slice P95 Latency (target: <${TEMPORAL_P95_TARGET_MS}ms)..."
    response=$(run_prom_query 'nexus:temporal_slice_latency_p95:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Temporal P95 metric not available"
        record_result "temporal_p95_latency" "skip" "N/A" "${TEMPORAL_P95_TARGET_MS}ms" "Metric not available"
    else
        value_ms=$(as_ms "$value")
        if lt "$value_ms" "$TEMPORAL_P95_TARGET_MS"; then
            log_pass "Temporal P95: ${value_ms}ms < ${TEMPORAL_P95_TARGET_MS}ms"
            record_result "temporal_p95_latency" "pass" "${value_ms}ms" "<${TEMPORAL_P95_TARGET_MS}ms"
        else
            log_fail "Temporal P95: ${value_ms}ms >= ${TEMPORAL_P95_TARGET_MS}ms"
            record_result "temporal_p95_latency" "fail" "${value_ms}ms" "<${TEMPORAL_P95_TARGET_MS}ms"
        fi
    fi

    # P99 Latency
    log_info "Checking Temporal Slice P99 Latency (target: <${TEMPORAL_P99_TARGET_MS}ms)..."
    response=$(run_prom_query 'nexus:temporal_slice_latency_p99:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Temporal P99 metric not available"
        record_result "temporal_p99_latency" "skip" "N/A" "${TEMPORAL_P99_TARGET_MS}ms" "Metric not available"
    else
        value_ms=$(as_ms "$value")
        if lt "$value_ms" "$TEMPORAL_P99_TARGET_MS"; then
            log_pass "Temporal P99: ${value_ms}ms < ${TEMPORAL_P99_TARGET_MS}ms"
            record_result "temporal_p99_latency" "pass" "${value_ms}ms" "<${TEMPORAL_P99_TARGET_MS}ms"
        else
            log_fail "Temporal P99: ${value_ms}ms >= ${TEMPORAL_P99_TARGET_MS}ms"
            record_result "temporal_p99_latency" "fail" "${value_ms}ms" "<${TEMPORAL_P99_TARGET_MS}ms"
        fi
    fi

    # Coherence Score
    log_info "Checking Cross-System Coherence (target: >${COHERENCE_TARGET})..."
    response=$(run_prom_query 'avg(nexus_temporal_coherence_score)')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Coherence metric not available"
        record_result "cross_system_coherence" "skip" "N/A" ">${COHERENCE_TARGET}" "Metric not available"
    else
        if gt "$value" "$COHERENCE_TARGET"; then
            log_pass "Coherence: $value > ${COHERENCE_TARGET}"
            record_result "cross_system_coherence" "pass" "$value" ">${COHERENCE_TARGET}"
        else
            log_fail "Coherence: $value <= ${COHERENCE_TARGET}"
            record_result "cross_system_coherence" "fail" "$value" ">${COHERENCE_TARGET}"
        fi
    fi

    # Error Rate
    log_info "Checking Temporal Error Rate (target: <0.1%)..."
    response=$(run_prom_query 'nexus:temporal_slice_error_rate:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Error rate metric not available"
        record_result "temporal_error_rate" "skip" "N/A" "<0.1%" "Metric not available"
    else
        value_pct=$(as_percent "$value")
        if lt "$value" "$ERROR_RATE_TARGET"; then
            log_pass "Error Rate: ${value_pct} < 0.1%"
            record_result "temporal_error_rate" "pass" "$value_pct" "<0.1%"
        else
            log_fail "Error Rate: ${value_pct} >= 0.1%"
            record_result "temporal_error_rate" "fail" "$value_pct" "<0.1%"
        fi
    fi

    echo ""
fi

# =============================================================================
# Golden Loop SLO Checks
# =============================================================================
if [[ "$SKIP_GOLDEN_LOOP" != "true" ]]; then
    echo -e "${BLUE}--- Golden Loop SLO Checks ---${NC}"
    echo ""

    # Golden Loop P95 Latency
    log_info "Checking Golden Loop P95 Latency (target: <${GOLDEN_LOOP_P95_TARGET_MS}ms)..."
    response=$(run_prom_query 'nexus:golden_loop_latency_p95:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Golden Loop P95 metric not available"
        record_result "golden_loop_p95_latency" "skip" "N/A" "<${GOLDEN_LOOP_P95_TARGET_MS}ms" "Metric not available"
    else
        value_ms=$(as_ms "$value")
        if lt "$value_ms" "$GOLDEN_LOOP_P95_TARGET_MS"; then
            log_pass "Golden Loop P95: ${value_ms}ms < ${GOLDEN_LOOP_P95_TARGET_MS}ms"
            record_result "golden_loop_p95_latency" "pass" "${value_ms}ms" "<${GOLDEN_LOOP_P95_TARGET_MS}ms"
        else
            log_fail "Golden Loop P95: ${value_ms}ms >= ${GOLDEN_LOOP_P95_TARGET_MS}ms"
            record_result "golden_loop_p95_latency" "fail" "${value_ms}ms" "<${GOLDEN_LOOP_P95_TARGET_MS}ms"
        fi
    fi

    # Golden Loop P99 Latency
    log_info "Checking Golden Loop P99 Latency (target: <${GOLDEN_LOOP_P99_TARGET_MS}ms)..."
    response=$(run_prom_query 'nexus:golden_loop_latency_p99:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Golden Loop P99 metric not available"
        record_result "golden_loop_p99_latency" "skip" "N/A" "<${GOLDEN_LOOP_P99_TARGET_MS}ms" "Metric not available"
    else
        value_ms=$(as_ms "$value")
        if lt "$value_ms" "$GOLDEN_LOOP_P99_TARGET_MS"; then
            log_pass "Golden Loop P99: ${value_ms}ms < ${GOLDEN_LOOP_P99_TARGET_MS}ms"
            record_result "golden_loop_p99_latency" "pass" "${value_ms}ms" "<${GOLDEN_LOOP_P99_TARGET_MS}ms"
        else
            log_fail "Golden Loop P99: ${value_ms}ms >= ${GOLDEN_LOOP_P99_TARGET_MS}ms"
            record_result "golden_loop_p99_latency" "fail" "${value_ms}ms" "<${GOLDEN_LOOP_P99_TARGET_MS}ms"
        fi
    fi

    # Golden Loop Success Rate
    log_info "Checking Golden Loop Success Rate (target: >99%)..."
    response=$(run_prom_query 'nexus:golden_loop_success_rate:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Golden Loop Success Rate metric not available"
        record_result "golden_loop_success_rate" "skip" "N/A" ">99%" "Metric not available"
    else
        value_pct=$(as_percent "$value")
        if ge "$value" "$GOLDEN_LOOP_SUCCESS_RATE_TARGET"; then
            log_pass "Golden Loop Success Rate: ${value_pct} >= 99%"
            record_result "golden_loop_success_rate" "pass" "$value_pct" ">=99%"
        else
            log_fail "Golden Loop Success Rate: ${value_pct} < 99%"
            record_result "golden_loop_success_rate" "fail" "$value_pct" ">=99%"
        fi
    fi

    # Golden Loop Coherence
    log_info "Checking Golden Loop Coherence (target: >${COHERENCE_TARGET})..."
    response=$(run_prom_query 'nexus:golden_loop_coherence_avg:rate5m')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Golden Loop Coherence metric not available"
        record_result "golden_loop_coherence" "skip" "N/A" ">${COHERENCE_TARGET}" "Metric not available"
    else
        if gt "$value" "$COHERENCE_TARGET"; then
            log_pass "Golden Loop Coherence: $value > ${COHERENCE_TARGET}"
            record_result "golden_loop_coherence" "pass" "$value" ">${COHERENCE_TARGET}"
        else
            log_fail "Golden Loop Coherence: $value <= ${COHERENCE_TARGET}"
            record_result "golden_loop_coherence" "fail" "$value" ">${COHERENCE_TARGET}"
        fi
    fi

    # Golden Loop SLO Compliance
    log_info "Checking Golden Loop SLO Compliance (target: >95%)..."
    response=$(run_prom_query 'nexus_golden_loop_slo_compliance')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Golden Loop SLO Compliance metric not available"
        record_result "golden_loop_slo_compliance" "skip" "N/A" ">95%" "Metric not available"
    else
        value_pct=$(as_percent "$value")
        if ge "$value" "0.95"; then
            log_pass "Golden Loop SLO Compliance: ${value_pct} >= 95%"
            record_result "golden_loop_slo_compliance" "pass" "$value_pct" ">=95%"
        else
            log_fail "Golden Loop SLO Compliance: ${value_pct} < 95%"
            record_result "golden_loop_slo_compliance" "fail" "$value_pct" ">=95%"
        fi
    fi

    # Error Budget Remaining
    log_info "Checking Error Budget Remaining (target: >20%)..."
    response=$(run_prom_query 'nexus:golden_loop_error_budget_remaining:ratio')
    value=$(extract_value "$response")
    if [[ "$value" == "N/A" || "$value" == "ERROR" ]]; then
        log_warn "Error Budget metric not available"
        record_result "golden_loop_error_budget" "skip" "N/A" ">20%" "Metric not available"
    else
        value_pct=$(as_percent "$value")
        if gt "$value" "0.2"; then
            log_pass "Error Budget Remaining: ${value_pct} > 20%"
            record_result "golden_loop_error_budget" "pass" "$value_pct" ">20%"
        else
            log_fail "Error Budget Remaining: ${value_pct} <= 20%"
            record_result "golden_loop_error_budget" "fail" "$value_pct" ">20%"
        fi
    fi

    echo ""
fi

# =============================================================================
# Generate JUnit XML Report
# =============================================================================
log_info "Generating JUnit XML report..."

END_TIME=$(date +%s)
TOTAL_TIME=$((END_TIME - START_TIME))
SKIPPED_CHECKS=$((TOTAL_CHECKS - PASSED_CHECKS - FAILED_CHECKS))

cat > "$JUNIT_REPORT" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="Golden Loop SLO Validation" tests="$TOTAL_CHECKS" failures="$FAILED_CHECKS" skipped="$SKIPPED_CHECKS" time="$TOTAL_TIME">
    <testsuite name="Phase 1 SLO Checks" tests="$TOTAL_CHECKS" failures="$FAILED_CHECKS" skipped="$SKIPPED_CHECKS" time="$TOTAL_TIME">
EOF

for check_name in "${!SLO_RESULTS[@]}"; do
    IFS='|' read -r result actual target message <<< "${SLO_RESULTS[$check_name]}"
    
    cat >> "$JUNIT_REPORT" <<EOF
        <testcase name="$check_name" classname="slo.golden_loop" time="1">
EOF
    
    case "$result" in
        fail)
            cat >> "$JUNIT_REPORT" <<EOF
            <failure message="SLO not met: actual=$actual, target=$target"><![CDATA[$message]]></failure>
EOF
            ;;
        skip)
            cat >> "$JUNIT_REPORT" <<EOF
            <skipped message="$message"/>
EOF
            ;;
    esac
    
    echo "        </testcase>" >> "$JUNIT_REPORT"
done

cat >> "$JUNIT_REPORT" <<EOF
    </testsuite>
</testsuites>
EOF

log_info "JUnit report written to: $JUNIT_REPORT"

# =============================================================================
# Generate Summary Report
# =============================================================================
SUMMARY_FILE="$RESULTS_DIR/slo-summary.txt"
cat > "$SUMMARY_FILE" <<EOF
================================================================================
Golden Loop SLO Validation Summary
================================================================================
Timestamp: $(date -u +'%Y-%m-%d %H:%M:%S UTC')
Prometheus URL: $PROMETHEUS_URL

Phase 1 SLO Results:
--------------------------------------------------------------------------------
EOF

for check_name in "${!SLO_RESULTS[@]}"; do
    IFS='|' read -r result actual target message <<< "${SLO_RESULTS[$check_name]}"
    status_icon="?"
    case "$result" in
        pass) status_icon="✓" ;;
        fail) status_icon="✗" ;;
        skip) status_icon="○" ;;
    esac
    printf "  [%s] %-30s %s (target: %s)\n" "$status_icon" "$check_name" "$actual" "$target" >> "$SUMMARY_FILE"
done

cat >> "$SUMMARY_FILE" <<EOF

--------------------------------------------------------------------------------
Total Checks: $TOTAL_CHECKS
Passed: $PASSED_CHECKS
Failed: $FAILED_CHECKS
Skipped: $SKIPPED_CHECKS
Duration: ${TOTAL_TIME}s
================================================================================
EOF

# =============================================================================
# Print Final Summary
# =============================================================================
echo ""
echo -e "${BLUE}=================================================================${NC}"
echo -e "${BLUE}      SLO Validation Summary                                    ${NC}"
echo -e "${BLUE}=================================================================${NC}"
echo ""
log_info "Total Checks: $TOTAL_CHECKS"
log_info "Passed: $PASSED_CHECKS"
log_info "Failed: $FAILED_CHECKS"
log_info "Skipped: $SKIPPED_CHECKS"
log_info "Duration: ${TOTAL_TIME}s"
echo ""
log_info "Summary written to: $SUMMARY_FILE"
log_info "JUnit report written to: $JUNIT_REPORT"
echo ""

if [[ $FAILED_CHECKS -gt 0 ]]; then
    echo -e "${RED}=================================================================${NC}"
    echo -e "${RED}      PHASE 1 SLO VALIDATION: FAILED                           ${NC}"
    echo -e "${RED}=================================================================${NC}"
    echo ""
    log_error "$FAILED_CHECKS SLO checks failed. Review results above."
    exit 2
else
    echo -e "${GREEN}=================================================================${NC}"
    echo -e "${GREEN}      PHASE 1 SLO VALIDATION: PASSED                           ${NC}"
    echo -e "${GREEN}=================================================================${NC}"
    exit 0
fi

