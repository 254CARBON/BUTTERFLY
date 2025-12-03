#!/bin/bash
# =============================================================================
# BUTTERFLY Chaos Test Suite Runner
# =============================================================================
# Automated execution of chaos experiments with pass/fail criteria validation.
# Designed for CI/CD integration and scheduled chaos testing.
#
# Usage:
#   ./run-chaos-suite.sh [OPTIONS]
#
# Options:
#   --env ENV           Target environment: staging, canary (default: staging)
#   --category CAT      Run specific category: kafka, database, cache, network, pod
#                       (default: all)
#   --experiment EXP    Run single experiment by name
#   --dry-run           Validate YAML only, don't execute
#   --report            Generate detailed report
#   --output-dir DIR    Output directory for reports (default: /tmp/chaos-results)
#   --timeout SECS      Global timeout in seconds (default: 600)
#   --slack-webhook URL Slack webhook for notifications
#   --help              Show this help message
#
# Exit Codes:
#   0 - All experiments passed
#   1 - One or more experiments failed
#   2 - Configuration/setup error
#
# Requirements:
#   - kubectl configured with cluster access
#   - Chaos Mesh installed in target cluster
#   - jq for JSON processing
# =============================================================================

set -euo pipefail

# Configuration defaults
ENV="${ENV:-staging}"
CATEGORY="${CATEGORY:-all}"
EXPERIMENT="${EXPERIMENT:-}"
DRY_RUN="${DRY_RUN:-false}"
REPORT="${REPORT:-false}"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/chaos-results}"
TIMEOUT="${TIMEOUT:-600}"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXPERIMENTS_DIR="${SCRIPT_DIR}/../experiments"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_FILE="${OUTPUT_DIR}/chaos-results-${TIMESTAMP}.json"

# Colors for output
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
log_test() { echo -e "${CYAN}[TEST]${NC} $1"; }

show_help() {
    head -35 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --env) ENV="$2"; shift 2 ;;
        --category) CATEGORY="$2"; shift 2 ;;
        --experiment) EXPERIMENT="$2"; shift 2 ;;
        --dry-run) DRY_RUN="true"; shift ;;
        --report) REPORT="true"; shift ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --timeout) TIMEOUT="$2"; shift 2 ;;
        --slack-webhook) SLACK_WEBHOOK="$2"; shift 2 ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 2 ;;
    esac
done

# Validate prerequisites
command -v kubectl &>/dev/null || { log_error "kubectl not found"; exit 2; }
command -v jq &>/dev/null || { log_error "jq not found"; exit 2; }

# Validate environment
case "${ENV}" in
    staging|canary) ;;
    production) log_error "Cannot run chaos experiments in production!"; exit 2 ;;
    *) log_error "Unknown environment: ${ENV}"; exit 2 ;;
esac

# Determine namespace based on environment
NAMESPACE="butterfly-${ENV}"
if [[ "${ENV}" == "staging" ]]; then
    NAMESPACE="butterfly"  # Default namespace for staging
fi

# Create output directory
mkdir -p "${OUTPUT_DIR}"

# Initialize results
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0
declare -A TEST_RESULTS

log_info "============================================="
log_info "BUTTERFLY Chaos Test Suite"
log_info "============================================="
log_info "Environment: ${ENV}"
log_info "Namespace: ${NAMESPACE}"
log_info "Category: ${CATEGORY}"
log_info "Timeout: ${TIMEOUT}s"
log_info "============================================="

if [[ "${DRY_RUN}" == "true" ]]; then
    log_warn "DRY RUN MODE - Experiments will not be executed"
fi

# =============================================================================
# Helper Functions
# =============================================================================

# Get experiment files based on category
get_experiments() {
    local category="$1"
    if [[ "${category}" == "all" ]]; then
        find "${EXPERIMENTS_DIR}" -name "*.yaml" -type f | sort
    else
        find "${EXPERIMENTS_DIR}" -name "*${category}*.yaml" -type f | sort
    fi
}

# Extract pass criteria from experiment annotations
get_pass_criteria() {
    local file="$1"
    grep -E "chaos.butterfly.io/pass-criteria:" "$file" | head -1 | sed 's/.*pass-criteria: //' | tr -d '"' || echo "Recovery within duration"
}

# Wait for experiment to complete
wait_for_experiment() {
    local name="$1"
    local namespace="$2"
    local timeout="$3"
    
    local start_time=$(date +%s)
    while true; do
        local status=$(kubectl get chaos "${name}" -n "${namespace}" -o jsonpath='{.status.conditions[?(@.type=="AllRecovered")].status}' 2>/dev/null || echo "")
        
        if [[ "${status}" == "True" ]]; then
            return 0
        fi
        
        local elapsed=$(($(date +%s) - start_time))
        if [[ ${elapsed} -gt ${timeout} ]]; then
            return 1
        fi
        
        sleep 5
    done
}

# Check service health after experiment
check_service_health() {
    local service="$1"
    local namespace="$2"
    
    # Try to get health endpoint
    local health=$(kubectl exec -n "${namespace}" -l "app=${service}" -- \
        curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    
    [[ "${health}" == "200" ]]
}

# Check recovery time
measure_recovery_time() {
    local namespace="$1"
    local start_time="$2"
    local service="$3"
    
    local recovery_start=$(date +%s)
    local max_wait=120  # 2 minutes max
    
    while true; do
        if check_service_health "${service}" "${namespace}"; then
            local recovery_time=$(($(date +%s) - recovery_start))
            echo "${recovery_time}"
            return 0
        fi
        
        local elapsed=$(($(date +%s) - recovery_start))
        if [[ ${elapsed} -gt ${max_wait} ]]; then
            echo "-1"
            return 1
        fi
        
        sleep 2
    done
}

# Run a single experiment
run_experiment() {
    local file="$1"
    local name=$(basename "${file}" .yaml)
    local pass_criteria=$(get_pass_criteria "${file}")
    
    log_test "Running: ${name}"
    log_info "  Pass criteria: ${pass_criteria}"
    
    ((TOTAL_TESTS++))
    
    if [[ "${DRY_RUN}" == "true" ]]; then
        # Validate YAML only
        if kubectl apply --dry-run=client -f "${file}" &>/dev/null; then
            log_info "  [VALID] YAML syntax OK"
            TEST_RESULTS["${name}"]="SKIPPED"
            ((SKIPPED_TESTS++))
        else
            log_error "  [INVALID] YAML syntax error"
            TEST_RESULTS["${name}"]="FAILED"
            ((FAILED_TESTS++))
        fi
        return
    fi
    
    # Apply experiment
    local start_time=$(date +%s)
    if ! kubectl apply -f "${file}" -n "${NAMESPACE}" 2>/dev/null; then
        log_error "  [FAILED] Could not apply experiment"
        TEST_RESULTS["${name}"]="FAILED:Could not apply"
        ((FAILED_TESTS++))
        return
    fi
    
    # Get experiment kind and name
    local kind=$(yq e '.kind' "${file}" 2>/dev/null || grep "^kind:" "${file}" | head -1 | awk '{print $2}')
    local exp_name=$(yq e '.metadata.name' "${file}" 2>/dev/null || grep "^  name:" "${file}" | head -1 | awk '{print $2}')
    
    log_info "  Experiment active: ${kind}/${exp_name}"
    
    # Wait for experiment duration
    local duration=$(yq e '.spec.duration' "${file}" 2>/dev/null || echo "60s")
    duration=${duration%s}
    log_info "  Waiting ${duration}s for experiment to complete..."
    sleep "${duration}"
    
    # Check experiment status
    local exp_status=$(kubectl get "${kind,,}" "${exp_name}" -n "${NAMESPACE}" \
        -o jsonpath='{.status.experiment.phase}' 2>/dev/null || echo "Unknown")
    
    # Delete experiment
    kubectl delete -f "${file}" -n "${NAMESPACE}" 2>/dev/null || true
    
    # Measure recovery
    local recovery_time=$(measure_recovery_time "${NAMESPACE}" "${start_time}" "nexus")
    local total_time=$(($(date +%s) - start_time))
    
    # Evaluate pass/fail
    if [[ ${recovery_time} -gt 0 && ${recovery_time} -lt 60 ]]; then
        log_info "  [PASSED] Recovery time: ${recovery_time}s (< 60s target)"
        TEST_RESULTS["${name}"]="PASSED:${recovery_time}s"
        ((PASSED_TESTS++))
    elif [[ ${recovery_time} -eq -1 ]]; then
        log_error "  [FAILED] Service did not recover within timeout"
        TEST_RESULTS["${name}"]="FAILED:No recovery"
        ((FAILED_TESTS++))
    else
        log_warn "  [FAILED] Recovery time: ${recovery_time}s (> 60s target)"
        TEST_RESULTS["${name}"]="FAILED:${recovery_time}s"
        ((FAILED_TESTS++))
    fi
}

# =============================================================================
# Main Execution
# =============================================================================

log_step "Verifying Chaos Mesh installation..."
if ! kubectl get crd podchaos.chaos-mesh.org &>/dev/null; then
    log_error "Chaos Mesh not installed in cluster"
    exit 2
fi
log_info "Chaos Mesh is installed"

log_step "Checking namespace ${NAMESPACE}..."
if ! kubectl get namespace "${NAMESPACE}" &>/dev/null; then
    log_error "Namespace ${NAMESPACE} does not exist"
    exit 2
fi
log_info "Namespace exists"

# Get experiments to run
if [[ -n "${EXPERIMENT}" ]]; then
    EXPERIMENT_FILES="${EXPERIMENTS_DIR}/${EXPERIMENT}.yaml"
    if [[ ! -f "${EXPERIMENT_FILES}" ]]; then
        log_error "Experiment not found: ${EXPERIMENT}"
        exit 2
    fi
else
    EXPERIMENT_FILES=$(get_experiments "${CATEGORY}")
fi

# Count experiments
EXPERIMENT_COUNT=$(echo "${EXPERIMENT_FILES}" | wc -l)
log_info "Found ${EXPERIMENT_COUNT} experiments to run"

# Run experiments
log_step "Executing chaos experiments..."
echo ""

for file in ${EXPERIMENT_FILES}; do
    if [[ -f "${file}" ]]; then
        run_experiment "${file}"
        echo ""
    fi
done

# =============================================================================
# Generate Report
# =============================================================================

log_step "Generating results..."

# Calculate summary
PASS_RATE=0
if [[ ${TOTAL_TESTS} -gt 0 ]]; then
    PASS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
fi

# Generate JSON report
cat > "${RESULTS_FILE}" << EOF
{
    "suite": "butterfly-chaos",
    "timestamp": "${TIMESTAMP}",
    "environment": "${ENV}",
    "namespace": "${NAMESPACE}",
    "category": "${CATEGORY}",
    "summary": {
        "total": ${TOTAL_TESTS},
        "passed": ${PASSED_TESTS},
        "failed": ${FAILED_TESTS},
        "skipped": ${SKIPPED_TESTS},
        "pass_rate": ${PASS_RATE}
    },
    "results": {
$(for name in "${!TEST_RESULTS[@]}"; do
    result="${TEST_RESULTS[$name]}"
    status="${result%%:*}"
    detail="${result#*:}"
    echo "        \"${name}\": {\"status\": \"${status}\", \"detail\": \"${detail}\"},"
done | sed '$ s/,$//')
    },
    "target_metrics": {
        "chaos_recovery_target_seconds": 60,
        "rto_target_minutes": 60,
        "rpo_target_minutes": 15
    }
}
EOF

# Print summary
echo ""
log_info "============================================="
log_info "CHAOS TEST RESULTS"
log_info "============================================="
log_info "Total:    ${TOTAL_TESTS}"
log_info "Passed:   ${PASSED_TESTS}"
log_info "Failed:   ${FAILED_TESTS}"
log_info "Skipped:  ${SKIPPED_TESTS}"
log_info "Pass Rate: ${PASS_RATE}%"
log_info "============================================="
log_info "Results saved to: ${RESULTS_FILE}"

# Send Slack notification if configured
if [[ -n "${SLACK_WEBHOOK}" && "${DRY_RUN}" != "true" ]]; then
    log_info "Sending Slack notification..."
    
    if [[ ${FAILED_TESTS} -gt 0 ]]; then
        COLOR="danger"
        EMOJI=":x:"
    else
        COLOR="good"
        EMOJI=":white_check_mark:"
    fi
    
    curl -s -X POST "${SLACK_WEBHOOK}" \
        -H 'Content-Type: application/json' \
        -d "{
            \"attachments\": [{
                \"color\": \"${COLOR}\",
                \"title\": \"${EMOJI} BUTTERFLY Chaos Test Results\",
                \"fields\": [
                    {\"title\": \"Environment\", \"value\": \"${ENV}\", \"short\": true},
                    {\"title\": \"Category\", \"value\": \"${CATEGORY}\", \"short\": true},
                    {\"title\": \"Passed\", \"value\": \"${PASSED_TESTS}/${TOTAL_TESTS}\", \"short\": true},
                    {\"title\": \"Pass Rate\", \"value\": \"${PASS_RATE}%\", \"short\": true}
                ],
                \"footer\": \"Chaos Suite Runner\",
                \"ts\": $(date +%s)
            }]
        }" || log_warn "Failed to send Slack notification"
fi

# Exit with appropriate code
if [[ ${FAILED_TESTS} -gt 0 ]]; then
    log_error "Some experiments failed - review results for details"
    exit 1
else
    log_info "All experiments passed!"
    exit 0
fi

