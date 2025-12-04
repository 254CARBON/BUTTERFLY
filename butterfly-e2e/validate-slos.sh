#!/bin/bash
# =============================================================================
# BUTTERFLY SLO Validation Script
# =============================================================================
# Validates Service Level Objectives against Prometheus metrics.
# Produces JUnit XML and JSON reports for CI integration.
#
# SLO Targets:
#   - Temporal Slice P50: < 50ms
#   - Temporal Slice P95: < 100ms
#   - Temporal Slice P99: < 200ms
#   - Cross-System Coherence: > 0.7
#   - Temporal Slice Error Rate: < 0.1%
#   - Golden Loop P95: < 2000ms
#   - Golden Loop P99: < 5000ms
#   - Golden Loop Success Rate: > 99%
#
# Usage:
#   ./validate-slos.sh [OPTIONS]
#
# Options:
#   --prometheus-url URL    Prometheus URL (default: http://localhost:9090)
#   --junit-report FILE     Output JUnit XML report
#   --json-report FILE      Output JSON report
#   --verbose               Enable verbose output
#   --help                  Show this help message
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Configuration defaults
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
JUNIT_REPORT=""
JSON_REPORT=""
VERBOSE=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Results tracking
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
declare -A SLO_RESULTS

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_check() { echo -e "${BLUE}[CHECK]${NC} $1"; }

show_help() {
    head -30 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --prometheus-url) PROMETHEUS_URL="$2"; shift 2 ;;
        --junit-report) JUNIT_REPORT="$2"; shift 2 ;;
        --json-report) JSON_REPORT="$2"; shift 2 ;;
        --verbose) VERBOSE=true; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 1 ;;
    esac
done

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

check_latency_slo() {
    local name="$1"
    local metric="$2"
    local percentile="$3"
    local target_ms="$4"
    
    ((TOTAL_CHECKS++))
    log_check "Checking $name (target: < ${target_ms}ms)..."
    
    local query="histogram_quantile(${percentile}, rate(${metric}_bucket[5m])) * 1000"
    local result
    result=$(query_prometheus "$query")
    
    if [ "$result" = "N/A" ] || [ "$result" = "null" ]; then
        log_warn "  Could not retrieve metric for $name"
        SLO_RESULTS["$name"]="SKIP:N/A:${target_ms}"
        return 1
    fi
    
    local result_int
    result_int=$(printf "%.0f" "$result")
    
    if [ "$result_int" -le "$target_ms" ]; then
        log_info "  PASS: ${result_int}ms <= ${target_ms}ms"
        SLO_RESULTS["$name"]="PASS:${result_int}:${target_ms}"
        ((PASSED_CHECKS++))
        return 0
    else
        log_error "  FAIL: ${result_int}ms > ${target_ms}ms"
        SLO_RESULTS["$name"]="FAIL:${result_int}:${target_ms}"
        ((FAILED_CHECKS++))
        return 1
    fi
}

check_rate_slo() {
    local name="$1"
    local metric="$2"
    local target="$3"
    local operator="$4"  # "gt" or "lt"
    
    ((TOTAL_CHECKS++))
    log_check "Checking $name (target: $operator $target)..."
    
    local result
    result=$(query_prometheus "$metric")
    
    if [ "$result" = "N/A" ] || [ "$result" = "null" ]; then
        log_warn "  Could not retrieve metric for $name"
        SLO_RESULTS["$name"]="SKIP:N/A:${target}"
        return 1
    fi
    
    local result_float
    result_float=$(printf "%.4f" "$result")
    
    local pass=false
    if [ "$operator" = "gt" ]; then
        if (( $(echo "$result_float > $target" | bc -l) )); then
            pass=true
        fi
    else
        if (( $(echo "$result_float < $target" | bc -l) )); then
            pass=true
        fi
    fi
    
    if [ "$pass" = true ]; then
        log_info "  PASS: $result_float $operator $target"
        SLO_RESULTS["$name"]="PASS:${result_float}:${target}"
        ((PASSED_CHECKS++))
        return 0
    else
        log_error "  FAIL: $result_float NOT $operator $target"
        SLO_RESULTS["$name"]="FAIL:${result_float}:${target}"
        ((FAILED_CHECKS++))
        return 1
    fi
}

# =============================================================================
# SLO Checks
# =============================================================================

log_info "============================================="
log_info "BUTTERFLY SLO Validation"
log_info "============================================="
log_info "Prometheus URL: ${PROMETHEUS_URL}"
log_info "============================================="
echo ""

# Verify Prometheus connectivity
if ! curl -sf "${PROMETHEUS_URL}/-/healthy" > /dev/null 2>&1; then
    log_error "Cannot connect to Prometheus at ${PROMETHEUS_URL}"
    exit 1
fi
log_info "Prometheus connection verified"
echo ""

# -----------------------------------------------------------------------------
# Temporal Slice SLOs
# -----------------------------------------------------------------------------
log_info "--- Temporal Slice SLOs ---"

check_latency_slo \
    "temporal_slice_p50" \
    "nexus_temporal_slice_query_duration" \
    "0.50" \
    "50" || true

check_latency_slo \
    "temporal_slice_p95" \
    "nexus_temporal_slice_query_duration" \
    "0.95" \
    "100" || true

check_latency_slo \
    "temporal_slice_p99" \
    "nexus_temporal_slice_query_duration" \
    "0.99" \
    "200" || true

echo ""

# -----------------------------------------------------------------------------
# Cross-System Coherence SLO
# -----------------------------------------------------------------------------
log_info "--- Cross-System Coherence SLO ---"

check_rate_slo \
    "cross_system_coherence" \
    "avg(nexus_temporal_coherence_score)" \
    "0.7" \
    "gt" || true

echo ""

# -----------------------------------------------------------------------------
# Error Rate SLO
# -----------------------------------------------------------------------------
log_info "--- Error Rate SLO ---"

check_rate_slo \
    "temporal_slice_error_rate" \
    "rate(nexus_temporal_slice_errors_total[5m]) / rate(nexus_temporal_slice_requests_total[5m])" \
    "0.001" \
    "lt" || true

echo ""

# -----------------------------------------------------------------------------
# Golden Loop SLOs
# -----------------------------------------------------------------------------
log_info "--- Golden Loop SLOs ---"

check_latency_slo \
    "golden_loop_p95" \
    "nexus_golden_loop_duration" \
    "0.95" \
    "2000" || true

check_latency_slo \
    "golden_loop_p99" \
    "nexus_golden_loop_duration" \
    "0.99" \
    "5000" || true

check_rate_slo \
    "golden_loop_success_rate" \
    "sum(rate(nexus_golden_loop_success_total[5m])) / sum(rate(nexus_golden_loop_total[5m]))" \
    "0.99" \
    "gt" || true

check_rate_slo \
    "golden_loop_coherence" \
    "avg(nexus_golden_loop_coherence_score)" \
    "0.7" \
    "gt" || true

echo ""

# =============================================================================
# Generate Reports
# =============================================================================

# Calculate pass rate
PASS_RATE=0
if [ $TOTAL_CHECKS -gt 0 ]; then
    PASS_RATE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
fi

# Generate JUnit XML report
if [ -n "$JUNIT_REPORT" ]; then
    mkdir -p "$(dirname "$JUNIT_REPORT")"
    
    cat > "$JUNIT_REPORT" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="BUTTERFLY SLO Validation" tests="${TOTAL_CHECKS}" failures="${FAILED_CHECKS}" errors="0" time="0">
  <testsuite name="SLO Checks" tests="${TOTAL_CHECKS}" failures="${FAILED_CHECKS}" errors="0">
EOF

    for name in "${!SLO_RESULTS[@]}"; do
        result="${SLO_RESULTS[$name]}"
        status="${result%%:*}"
        rest="${result#*:}"
        actual="${rest%%:*}"
        target="${rest#*:}"
        
        if [ "$status" = "PASS" ]; then
            echo "    <testcase name=\"${name}\" classname=\"slo.validation\">" >> "$JUNIT_REPORT"
            echo "      <system-out>Actual: ${actual}, Target: ${target}</system-out>" >> "$JUNIT_REPORT"
            echo "    </testcase>" >> "$JUNIT_REPORT"
        elif [ "$status" = "FAIL" ]; then
            echo "    <testcase name=\"${name}\" classname=\"slo.validation\">" >> "$JUNIT_REPORT"
            echo "      <failure message=\"SLO not met\">Actual: ${actual}, Target: ${target}</failure>" >> "$JUNIT_REPORT"
            echo "    </testcase>" >> "$JUNIT_REPORT"
        else
            echo "    <testcase name=\"${name}\" classname=\"slo.validation\">" >> "$JUNIT_REPORT"
            echo "      <skipped message=\"Metric not available\"/>" >> "$JUNIT_REPORT"
            echo "    </testcase>" >> "$JUNIT_REPORT"
        fi
    done

    cat >> "$JUNIT_REPORT" << EOF
  </testsuite>
</testsuites>
EOF

    log_info "JUnit report saved to: $JUNIT_REPORT"
fi

# Generate JSON report
if [ -n "$JSON_REPORT" ]; then
    mkdir -p "$(dirname "$JSON_REPORT")"
    
    cat > "$JSON_REPORT" << EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "prometheus_url": "${PROMETHEUS_URL}",
  "summary": {
    "total_checks": ${TOTAL_CHECKS},
    "passed": ${PASSED_CHECKS},
    "failed": ${FAILED_CHECKS},
    "pass_rate": ${PASS_RATE}
  },
  "slo_targets": {
    "temporal_slice_p50_ms": 50,
    "temporal_slice_p95_ms": 100,
    "temporal_slice_p99_ms": 200,
    "cross_system_coherence": 0.7,
    "error_rate": 0.001,
    "golden_loop_p95_ms": 2000,
    "golden_loop_p99_ms": 5000,
    "golden_loop_success_rate": 0.99,
    "golden_loop_coherence": 0.7
  },
  "results": {
EOF

    first=true
    for name in "${!SLO_RESULTS[@]}"; do
        result="${SLO_RESULTS[$name]}"
        status="${result%%:*}"
        rest="${result#*:}"
        actual="${rest%%:*}"
        target="${rest#*:}"
        
        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$JSON_REPORT"
        fi
        
        echo -n "    \"${name}\": {\"status\": \"${status}\", \"actual\": \"${actual}\", \"target\": \"${target}\"}" >> "$JSON_REPORT"
    done

    cat >> "$JSON_REPORT" << EOF

  }
}
EOF

    log_info "JSON report saved to: $JSON_REPORT"
fi

# =============================================================================
# Summary
# =============================================================================

echo ""
log_info "============================================="
log_info "SLO VALIDATION SUMMARY"
log_info "============================================="
log_info "Total Checks: ${TOTAL_CHECKS}"
log_info "Passed:       ${PASSED_CHECKS}"
log_info "Failed:       ${FAILED_CHECKS}"
log_info "Pass Rate:    ${PASS_RATE}%"
log_info "============================================="

if [ $FAILED_CHECKS -gt 0 ]; then
    log_error "SLO validation FAILED - some targets not met"
    exit 1
else
    log_info "SLO validation PASSED - all targets met"
    exit 0
fi

