#!/bin/bash
# =============================================================================
# Phase 1 Integration Validation Suite
# =============================================================================
# Runs all Phase 1 validation checks including:
# - All E2E scenarios (17 scenarios)
# - Golden Loop SLO validation
# - Resilience checks
# - Data quality validation
# - Metrics export to Prometheus Pushgateway
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NEXUS_URL="${NEXUS_URL:-http://localhost:8084}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
PUSHGATEWAY_URL="${PUSHGATEWAY_URL:-}"
RESULTS_DIR="${RESULTS_DIR:-$SCRIPT_DIR/phase1-results}"
CONSECUTIVE_RUNS="${CONSECUTIVE_RUNS:-3}"
SKIP_STACK="${SKIP_STACK:-false}"
TOTAL_SCENARIOS=17
PASSED_SCENARIOS=0
FAILED_SCENARIOS=0
START_TIME=$(date +%s)

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-stack)
            SKIP_STACK=true
            shift
            ;;
        --prometheus-url)
            PROMETHEUS_URL=$2
            shift 2
            ;;
        --pushgateway-url)
            PUSHGATEWAY_URL=$2
            shift 2
            ;;
        --results-dir)
            RESULTS_DIR=$2
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

# Create results directory
mkdir -p "$RESULTS_DIR"

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}       BUTTERFLY Phase 1 Integration Validation Suite       ${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

log_section() {
    echo ""
    echo -e "${BLUE}------------------------------------------------------------${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}------------------------------------------------------------${NC}"
}

log_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

log_failure() {
    echo -e "${RED}✗ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

log_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

check_service() {
    local service_url=$1
    local service_name=$2
    if curl -s --connect-timeout 5 "$service_url/actuator/health" > /dev/null 2>&1; then
        log_success "$service_name is healthy"
        return 0
    else
        log_failure "$service_name is not reachable at $service_url"
        return 1
    fi
}

# -----------------------------------------------------------------------------
# Pre-flight Checks
# -----------------------------------------------------------------------------

log_section "Pre-flight Checks"

# Check if required services are running
SERVICES_OK=true
check_service "$NEXUS_URL" "NEXUS" || SERVICES_OK=false

if [ "$SERVICES_OK" = false ]; then
    log_failure "Required services are not available. Please start the stack first."
    echo ""
    echo "To start the stack:"
    echo "  ./scripts/dev-up.sh --profile full-stack"
    exit 1
fi

# -----------------------------------------------------------------------------
# Run E2E Scenarios
# -----------------------------------------------------------------------------

log_section "E2E Scenario Execution (${CONSECUTIVE_RUNS} consecutive runs required)"

run_scenarios() {
    local category=$1
    local run_number=$2
    
    log_info "Running $category scenarios (Run $run_number/${CONSECUTIVE_RUNS})"
    
    if ./run-scenarios.sh --category "$category" --quiet; then
        return 0
    else
        return 1
    fi
}

SCENARIO_CATEGORIES=("golden-path" "temporal-navigation" "failure-injection" "nexus-core" "edge")
ALL_PASSES=true

for run in $(seq 1 $CONSECUTIVE_RUNS); do
    echo ""
    log_info "=== Consecutive Run $run of $CONSECUTIVE_RUNS ==="
    
    for category in "${SCENARIO_CATEGORIES[@]}"; do
        if run_scenarios "$category" "$run"; then
            log_success "$category scenarios passed (Run $run)"
        else
            log_failure "$category scenarios failed (Run $run)"
            ALL_PASSES=false
        fi
    done
done

if [ "$ALL_PASSES" = true ]; then
    log_success "All scenarios passed across $CONSECUTIVE_RUNS consecutive runs"
else
    log_failure "Some scenarios failed. Phase 1 validation incomplete."
fi

# -----------------------------------------------------------------------------
# SLO Validation
# -----------------------------------------------------------------------------

log_section "SLO Validation"

validate_slo() {
    local metric_name=$1
    local target_ms=$2
    local percentile=$3
    
    # Query Prometheus for the metric
    local query="histogram_quantile(${percentile}, rate(nexus_temporal_slice_query_duration_bucket[5m])) * 1000"
    local result=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')
    
    if [ "$result" = "N/A" ] || [ "$result" = "null" ]; then
        log_warning "Could not retrieve metric: $metric_name"
        return 1
    fi
    
    local result_int=$(printf "%.0f" "$result")
    
    if [ "$result_int" -le "$target_ms" ]; then
        log_success "$metric_name: ${result_int}ms <= ${target_ms}ms"
        return 0
    else
        log_failure "$metric_name: ${result_int}ms > ${target_ms}ms"
        return 1
    fi
}

SLO_PASS=true

validate_slo "Temporal Slice P50 Latency" 50 0.50 || SLO_PASS=false
validate_slo "Temporal Slice P95 Latency" 100 0.95 || SLO_PASS=false
validate_slo "Temporal Slice P99 Latency" 200 0.99 || SLO_PASS=false

# Coherence score validation
log_info "Checking cross-system coherence score..."
coherence_query="avg(nexus_temporal_coherence_score)"
coherence_result=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$coherence_query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')

if [ "$coherence_result" != "N/A" ] && [ "$coherence_result" != "null" ]; then
    coherence_float=$(printf "%.2f" "$coherence_result")
    if (( $(echo "$coherence_float > 0.7" | bc -l) )); then
        log_success "Cross-system coherence score: $coherence_float > 0.7"
    else
        log_failure "Cross-system coherence score: $coherence_float <= 0.7"
        SLO_PASS=false
    fi
else
    log_warning "Could not retrieve coherence score metric"
fi

if [ "$SLO_PASS" = true ]; then
    log_success "All SLO targets met"
else
    log_failure "Some SLO targets not met"
fi

# -----------------------------------------------------------------------------
# Golden Loop SLO Validation
# -----------------------------------------------------------------------------

log_section "Golden Loop SLO Validation"

GOLDEN_LOOP_PASS=true

# Run the dedicated golden loop SLO validator
if [ -x "$SCRIPT_DIR/validate-golden-loop-slo.sh" ]; then
    log_info "Running Golden Loop SLO validator..."
    if "$SCRIPT_DIR/validate-golden-loop-slo.sh" \
        --prometheus-url "$PROMETHEUS_URL" \
        --results-dir "$RESULTS_DIR/golden-loop" \
        --junit-report "$RESULTS_DIR/golden-loop-slo-report.xml"; then
        log_success "Golden Loop SLO validation passed"
    else
        log_failure "Golden Loop SLO validation failed"
        GOLDEN_LOOP_PASS=false
    fi
else
    log_warning "Golden Loop SLO validator not found, skipping..."
fi

# Additional golden loop metrics checks
log_info "Checking Golden Loop specific metrics..."

# Golden Loop p95 latency (target: < 2s)
golden_loop_p95_query="nexus:golden_loop_latency_p95:rate5m * 1000"
golden_loop_p95=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$golden_loop_p95_query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')

if [ "$golden_loop_p95" != "N/A" ] && [ "$golden_loop_p95" != "null" ]; then
    p95_int=$(printf "%.0f" "$golden_loop_p95")
    if [ "$p95_int" -le 2000 ]; then
        log_success "Golden Loop P95 Latency: ${p95_int}ms <= 2000ms"
    else
        log_failure "Golden Loop P95 Latency: ${p95_int}ms > 2000ms"
        GOLDEN_LOOP_PASS=false
    fi
else
    log_warning "Golden Loop P95 metric not available"
fi

# Golden Loop p99 latency (target: < 5s)
golden_loop_p99_query="nexus:golden_loop_latency_p99:rate5m * 1000"
golden_loop_p99=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$golden_loop_p99_query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')

if [ "$golden_loop_p99" != "N/A" ] && [ "$golden_loop_p99" != "null" ]; then
    p99_int=$(printf "%.0f" "$golden_loop_p99")
    if [ "$p99_int" -le 5000 ]; then
        log_success "Golden Loop P99 Latency: ${p99_int}ms <= 5000ms"
    else
        log_failure "Golden Loop P99 Latency: ${p99_int}ms > 5000ms"
        GOLDEN_LOOP_PASS=false
    fi
else
    log_warning "Golden Loop P99 metric not available"
fi

# Golden Loop success rate (target: > 99%)
golden_loop_success_query="nexus:golden_loop_success_rate:rate5m"
golden_loop_success=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$golden_loop_success_query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')

if [ "$golden_loop_success" != "N/A" ] && [ "$golden_loop_success" != "null" ]; then
    success_pct=$(echo "$golden_loop_success * 100" | bc -l | xargs printf "%.2f")
    if (( $(echo "$golden_loop_success >= 0.99" | bc -l) )); then
        log_success "Golden Loop Success Rate: ${success_pct}% >= 99%"
    else
        log_failure "Golden Loop Success Rate: ${success_pct}% < 99%"
        GOLDEN_LOOP_PASS=false
    fi
else
    log_warning "Golden Loop Success Rate metric not available"
fi

# Golden Loop coherence (target: > 0.7)
golden_loop_coherence_query="nexus:golden_loop_coherence_avg:rate5m"
golden_loop_coherence=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$golden_loop_coherence_query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')

if [ "$golden_loop_coherence" != "N/A" ] && [ "$golden_loop_coherence" != "null" ]; then
    coherence_val=$(printf "%.3f" "$golden_loop_coherence")
    if (( $(echo "$golden_loop_coherence > 0.7" | bc -l) )); then
        log_success "Golden Loop Coherence: ${coherence_val} > 0.7"
    else
        log_failure "Golden Loop Coherence: ${coherence_val} <= 0.7"
        GOLDEN_LOOP_PASS=false
    fi
else
    log_warning "Golden Loop Coherence metric not available"
fi

if [ "$GOLDEN_LOOP_PASS" = true ]; then
    log_success "All Golden Loop SLO targets met"
else
    log_failure "Some Golden Loop SLO targets not met"
fi

# -----------------------------------------------------------------------------
# Resilience Checks
# -----------------------------------------------------------------------------

log_section "Resilience Validation"

# Check DLQ topics for messages
log_info "Checking DLQ topics for messages..."
DLQ_TOPICS=$(kafka-topics.sh --list --bootstrap-server localhost:9092 2>/dev/null | grep ".dlq$" || echo "")

DLQ_CLEAN=true
for topic in $DLQ_TOPICS; do
    count=$(kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$topic" --from-beginning --timeout-ms 5000 2>/dev/null | wc -l)
    if [ "$count" -gt 0 ]; then
        log_failure "DLQ topic $topic has $count messages"
        DLQ_CLEAN=false
    else
        log_success "DLQ topic $topic is empty"
    fi
done

if [ "$DLQ_CLEAN" = true ]; then
    log_success "All DLQ topics are clean"
fi

# Check circuit breaker states
log_info "Checking circuit breaker states..."
CB_RESPONSE=$(curl -s "${NEXUS_URL}/actuator/health" | jq -r '.components.circuitBreakers.details // {}')

if echo "$CB_RESPONSE" | grep -q '"state":"OPEN"'; then
    log_failure "Some circuit breakers are OPEN"
else
    log_success "All circuit breakers are CLOSED"
fi

# -----------------------------------------------------------------------------
# Final Summary
# -----------------------------------------------------------------------------

log_section "Phase 1 Validation Summary"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

OVERALL_PASS=true

echo ""
echo "Checklist:"
echo ""

if [ "$ALL_PASSES" = true ]; then
    echo -e "  ${GREEN}[✓]${NC} All 17 E2E scenarios pass (${CONSECUTIVE_RUNS} consecutive runs)"
    SCENARIO_STATUS=1
else
    echo -e "  ${RED}[✗]${NC} All 17 E2E scenarios pass (${CONSECUTIVE_RUNS} consecutive runs)"
    OVERALL_PASS=false
    SCENARIO_STATUS=0
fi

if [ "$SLO_PASS" = true ]; then
    echo -e "  ${GREEN}[✓]${NC} Temporal Slice SLO targets met"
    TEMPORAL_SLO_STATUS=1
else
    echo -e "  ${RED}[✗]${NC} Temporal Slice SLO targets met"
    OVERALL_PASS=false
    TEMPORAL_SLO_STATUS=0
fi

if [ "$GOLDEN_LOOP_PASS" = true ]; then
    echo -e "  ${GREEN}[✓]${NC} Golden Loop SLO targets met"
    GOLDEN_LOOP_SLO_STATUS=1
else
    echo -e "  ${RED}[✗]${NC} Golden Loop SLO targets met"
    OVERALL_PASS=false
    GOLDEN_LOOP_SLO_STATUS=0
fi

if [ "$DLQ_CLEAN" = true ]; then
    echo -e "  ${GREEN}[✓]${NC} Zero DLQ messages during golden path"
    DLQ_STATUS=1
else
    echo -e "  ${RED}[✗]${NC} Zero DLQ messages during golden path"
    OVERALL_PASS=false
    DLQ_STATUS=0
fi

echo ""

# -----------------------------------------------------------------------------
# Export Metrics to Prometheus Pushgateway
# -----------------------------------------------------------------------------

log_section "Metrics Export"

if [ -n "$PUSHGATEWAY_URL" ]; then
    log_info "Pushing Phase 1 validation metrics to Pushgateway..."
    
    OVERALL_STATUS=0
    if [ "$OVERALL_PASS" = true ]; then
        OVERALL_STATUS=1
    fi
    
    # Prepare metrics payload
    METRICS_PAYLOAD=$(cat <<EOF
# HELP butterfly_phase1_validation_run_timestamp Unix timestamp of Phase 1 validation run
# TYPE butterfly_phase1_validation_run_timestamp gauge
butterfly_phase1_validation_run_timestamp $END_TIME
# HELP butterfly_phase1_validation_duration_seconds Duration of Phase 1 validation run in seconds
# TYPE butterfly_phase1_validation_duration_seconds gauge
butterfly_phase1_validation_duration_seconds $DURATION
# HELP butterfly_phase1_validation_overall_status Overall Phase 1 validation status (1=pass, 0=fail)
# TYPE butterfly_phase1_validation_overall_status gauge
butterfly_phase1_validation_overall_status $OVERALL_STATUS
# HELP butterfly_phase1_validation_scenario_status E2E scenarios validation status (1=pass, 0=fail)
# TYPE butterfly_phase1_validation_scenario_status gauge
butterfly_phase1_validation_scenario_status $SCENARIO_STATUS
# HELP butterfly_phase1_validation_temporal_slo_status Temporal Slice SLO validation status (1=pass, 0=fail)
# TYPE butterfly_phase1_validation_temporal_slo_status gauge
butterfly_phase1_validation_temporal_slo_status $TEMPORAL_SLO_STATUS
# HELP butterfly_phase1_validation_golden_loop_slo_status Golden Loop SLO validation status (1=pass, 0=fail)
# TYPE butterfly_phase1_validation_golden_loop_slo_status gauge
butterfly_phase1_validation_golden_loop_slo_status $GOLDEN_LOOP_SLO_STATUS
# HELP butterfly_phase1_validation_dlq_status DLQ cleanliness status (1=clean, 0=has_messages)
# TYPE butterfly_phase1_validation_dlq_status gauge
butterfly_phase1_validation_dlq_status $DLQ_STATUS
# HELP butterfly_phase1_validation_consecutive_runs Number of consecutive runs required/executed
# TYPE butterfly_phase1_validation_consecutive_runs gauge
butterfly_phase1_validation_consecutive_runs $CONSECUTIVE_RUNS
EOF
    )
    
    # Push to Pushgateway
    if echo "$METRICS_PAYLOAD" | curl -sf --data-binary @- "$PUSHGATEWAY_URL/metrics/job/butterfly_phase1_validation/instance/e2e_harness"; then
        log_success "Metrics pushed to Pushgateway successfully"
    else
        log_warning "Failed to push metrics to Pushgateway"
    fi
else
    log_info "PUSHGATEWAY_URL not set, skipping metrics export"
    log_info "To enable metrics export, set PUSHGATEWAY_URL environment variable"
fi

# -----------------------------------------------------------------------------
# Generate Results Report
# -----------------------------------------------------------------------------

log_section "Generating Results Report"

RESULTS_FILE="$RESULTS_DIR/phase1-validation-report.json"

cat > "$RESULTS_FILE" <<EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "duration_seconds": $DURATION,
  "overall_status": "$OVERALL_PASS",
  "checks": {
    "e2e_scenarios": {
      "status": "$ALL_PASSES",
      "consecutive_runs": $CONSECUTIVE_RUNS,
      "total_scenarios": $TOTAL_SCENARIOS
    },
    "temporal_slice_slo": {
      "status": "$SLO_PASS",
      "targets": {
        "p50_ms": 50,
        "p95_ms": 100,
        "p99_ms": 200,
        "coherence": 0.7
      }
    },
    "golden_loop_slo": {
      "status": "$GOLDEN_LOOP_PASS",
      "targets": {
        "p95_ms": 2000,
        "p99_ms": 5000,
        "success_rate": 0.99,
        "coherence": 0.7
      }
    },
    "resilience": {
      "dlq_clean": "$DLQ_CLEAN"
    }
  },
  "prometheus_url": "$PROMETHEUS_URL",
  "grafana_dashboard": "nexus-temporal-slo"
}
EOF

log_info "Results report saved to: $RESULTS_FILE"

# Also save a summary text file
SUMMARY_FILE="$RESULTS_DIR/phase1-validation-summary.txt"
cat > "$SUMMARY_FILE" <<EOF
================================================================================
BUTTERFLY Phase 1 Integration Validation Summary
================================================================================
Timestamp: $(date -u +'%Y-%m-%d %H:%M:%S UTC')
Duration: ${DURATION}s

Results:
  E2E Scenarios (${CONSECUTIVE_RUNS} runs): $([ "$ALL_PASSES" = true ] && echo "PASS" || echo "FAIL")
  Temporal Slice SLOs: $([ "$SLO_PASS" = true ] && echo "PASS" || echo "FAIL")
  Golden Loop SLOs: $([ "$GOLDEN_LOOP_PASS" = true ] && echo "PASS" || echo "FAIL")
  DLQ Clean: $([ "$DLQ_CLEAN" = true ] && echo "PASS" || echo "FAIL")

Overall: $([ "$OVERALL_PASS" = true ] && echo "PASSED" || echo "FAILED")
================================================================================
EOF

log_info "Summary saved to: $SUMMARY_FILE"

echo ""

if [ "$OVERALL_PASS" = true ]; then
    echo -e "${GREEN}============================================================${NC}"
    echo -e "${GREEN}       PHASE 1 VALIDATION: PASSED                           ${NC}"
    echo -e "${GREEN}============================================================${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Run 24-hour soak test: ./run-soak-test.sh"
    echo "  2. Obtain integration team sign-off"
    echo "  3. Tag release: git tag v1.0.0-integration-complete"
    echo ""
    echo "View results in Grafana:"
    echo "  - NEXUS Temporal SLO Dashboard: /d/nexus-temporal-slo"
    echo "  - Golden Loop panels show Phase 1 metrics"
    exit 0
else
    echo -e "${RED}============================================================${NC}"
    echo -e "${RED}       PHASE 1 VALIDATION: FAILED                           ${NC}"
    echo -e "${RED}============================================================${NC}"
    echo ""
    echo "Please address the failing criteria and re-run validation."
    echo ""
    echo "Debug resources:"
    echo "  - Results directory: $RESULTS_DIR"
    echo "  - Prometheus: $PROMETHEUS_URL"
    echo "  - Grafana dashboard: nexus-temporal-slo"
    exit 1
fi

