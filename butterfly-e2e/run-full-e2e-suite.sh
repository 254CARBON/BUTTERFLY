#!/usr/bin/env bash
#
# BUTTERFLY Full E2E Test Suite
# Runs golden path + chaos scenarios for CI integration
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Color output
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
log_header() { echo -e "${CYAN}$1${NC}"; }

# Configuration
RESULTS_DIR="${RESULTS_DIR:-$SCRIPT_DIR/e2e-results}"
JUNIT_REPORT="${JUNIT_REPORT:-$RESULTS_DIR/junit-report.xml}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.full.yml}"

# Test categories
RUN_GOLDEN_PATH=${RUN_GOLDEN_PATH:-true}
RUN_CHAOS=${RUN_CHAOS:-true}
RUN_INTEGRATION=${RUN_INTEGRATION:-true}
SKIP_INFRASTRUCTURE=${SKIP_INFRASTRUCTURE:-false}
PARALLEL_SCENARIOS=${PARALLEL_SCENARIOS:-false}
VERBOSE=${VERBOSE:-false}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --golden-path-only)
            RUN_GOLDEN_PATH=true
            RUN_CHAOS=false
            RUN_INTEGRATION=false
            shift
            ;;
        --chaos-only)
            RUN_GOLDEN_PATH=false
            RUN_CHAOS=true
            RUN_INTEGRATION=false
            shift
            ;;
        --integration-only)
            RUN_GOLDEN_PATH=false
            RUN_CHAOS=false
            RUN_INTEGRATION=true
            shift
            ;;
        --skip-infrastructure)
            SKIP_INFRASTRUCTURE=true
            shift
            ;;
        --parallel)
            PARALLEL_SCENARIOS=true
            shift
            ;;
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --help|-h)
            echo "BUTTERFLY Full E2E Test Suite"
            echo ""
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --golden-path-only    Run only golden path scenarios"
            echo "  --chaos-only          Run only chaos scenarios"
            echo "  --integration-only    Run only integration tests"
            echo "  --skip-infrastructure Skip infrastructure setup/teardown"
            echo "  --parallel            Run scenarios in parallel (experimental)"
            echo "  --verbose, -v         Enable verbose output"
            echo "  -h, --help            Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  RESULTS_DIR           Directory for test results (default: ./e2e-results)"
            echo "  JUNIT_REPORT          Path for JUnit XML report"
            echo "  COMPOSE_FILE          Docker Compose file to use"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Initialize
mkdir -p "$RESULTS_DIR"
START_TIME=$(date +%s)

echo ""
log_header "================================================================="
log_header "      BUTTERFLY Full E2E Test Suite"
log_header "================================================================="
echo ""
log_info "Results directory: $RESULTS_DIR"
log_info "JUnit report: $JUNIT_REPORT"
log_info "Run golden path: $RUN_GOLDEN_PATH"
log_info "Run chaos: $RUN_CHAOS"
log_info "Run integration: $RUN_INTEGRATION"
echo ""

# Track results
declare -A TEST_RESULTS
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

record_result() {
    local test_name="$1"
    local result="$2"  # pass, fail, skip
    local duration="$3"
    local message="${4:-}"

    TEST_RESULTS["$test_name"]="$result:$duration:$message"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    case "$result" in
        "pass") PASSED_TESTS=$((PASSED_TESTS + 1)) ;;
        "fail") FAILED_TESTS=$((FAILED_TESTS + 1)) ;;
        "skip") SKIPPED_TESTS=$((SKIPPED_TESTS + 1)) ;;
    esac
}

# Infrastructure management
setup_infrastructure() {
    if [[ "$SKIP_INFRASTRUCTURE" == "true" ]]; then
        log_warn "Skipping infrastructure setup"
        return 0
    fi

    log_step "Setting up test infrastructure..."
    
    if [[ -f "$COMPOSE_FILE" ]]; then
        docker compose -f "$COMPOSE_FILE" up -d 2>/dev/null || {
            log_warn "Could not start infrastructure via Docker Compose"
            return 1
        }
        
        # Wait for services to be healthy
        log_info "Waiting for services to become healthy..."
        sleep 15
        
        local retries=30
        local services_ready=false
        
        while [[ $retries -gt 0 ]]; do
            if check_service_health; then
                services_ready=true
                break
            fi
            retries=$((retries - 1))
            sleep 2
        done
        
        if [[ "$services_ready" == "true" ]]; then
            log_info "✓ Infrastructure is ready"
        else
            log_warn "⚠ Some services may not be fully ready"
        fi
    else
        log_warn "Docker Compose file not found: $COMPOSE_FILE"
    fi
}

teardown_infrastructure() {
    if [[ "$SKIP_INFRASTRUCTURE" == "true" ]]; then
        return 0
    fi

    log_step "Tearing down test infrastructure..."
    
    if [[ -f "$COMPOSE_FILE" ]]; then
        docker compose -f "$COMPOSE_FILE" down -v 2>/dev/null || true
    fi
}

check_service_health() {
    local healthy=true
    
    for service in plato synapse nexus; do
        local port
        case "$service" in
            plato) port=8084 ;;
            synapse) port=8086 ;;
            nexus) port=8085 ;;
        esac
        
        if ! curl -sf "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
            healthy=false
        fi
    done
    
    [[ "$healthy" == "true" ]]
}

# Run golden path scenarios
run_golden_path_tests() {
    log_step "Running Golden Path Scenarios..."
    echo ""
    
    local test_start
    local test_end
    local duration
    
    # Run the golden path script
    test_start=$(date +%s)
    
    if "$SCRIPT_DIR/run-golden-path.sh" --skip-build 2>&1 | tee "$RESULTS_DIR/golden-path.log"; then
        test_end=$(date +%s)
        duration=$((test_end - test_start))
        record_result "golden-path" "pass" "$duration"
        log_info "✅ Golden path tests passed (${duration}s)"
    else
        test_end=$(date +%s)
        duration=$((test_end - test_start))
        record_result "golden-path" "fail" "$duration" "See golden-path.log"
        log_error "❌ Golden path tests failed (${duration}s)"
    fi
    echo ""
}

# Run chaos scenarios
run_chaos_tests() {
    log_step "Running Chaos Scenarios..."
    echo ""
    
    local test_start
    local test_end
    local duration
    
    test_start=$(date +%s)
    
    if "$SCRIPT_DIR/run-chaos-scenarios.sh" 2>&1 | tee "$RESULTS_DIR/chaos.log"; then
        test_end=$(date +%s)
        duration=$((test_end - test_start))
        record_result "chaos-scenarios" "pass" "$duration"
        log_info "✅ Chaos scenarios passed (${duration}s)"
    else
        test_end=$(date +%s)
        duration=$((test_end - test_start))
        record_result "chaos-scenarios" "fail" "$duration" "See chaos.log"
        log_error "❌ Chaos scenarios failed (${duration}s)"
    fi
    echo ""
}

# Run integration tests
run_integration_tests() {
    log_step "Running Integration Tests..."
    echo ""
    
    local test_start
    local test_end
    local duration
    
    test_start=$(date +%s)
    
    if mvn -f "$SCRIPT_DIR/pom.xml" test -DskipTests=false 2>&1 | tee "$RESULTS_DIR/integration.log"; then
        test_end=$(date +%s)
        duration=$((test_end - test_start))
        record_result "integration-tests" "pass" "$duration"
        log_info "✅ Integration tests passed (${duration}s)"
    else
        test_end=$(date +%s)
        duration=$((test_end - test_start))
        record_result "integration-tests" "fail" "$duration" "See integration.log"
        log_error "❌ Integration tests failed (${duration}s)"
    fi
    echo ""
}

# Generate JUnit XML report
generate_junit_report() {
    log_step "Generating JUnit report..."
    
    local total_time=$(($(date +%s) - START_TIME))
    
    cat > "$JUNIT_REPORT" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="BUTTERFLY E2E Test Suite" tests="$TOTAL_TESTS" failures="$FAILED_TESTS" skipped="$SKIPPED_TESTS" time="$total_time">
    <testsuite name="E2E Scenarios" tests="$TOTAL_TESTS" failures="$FAILED_TESTS" skipped="$SKIPPED_TESTS" time="$total_time">
EOF

    for test_name in "${!TEST_RESULTS[@]}"; do
        local result_data="${TEST_RESULTS[$test_name]}"
        local result
        local duration
        local message
        
        IFS=':' read -r result duration message <<< "$result_data"
        
        echo "        <testcase name=\"$test_name\" classname=\"butterfly.e2e\" time=\"$duration\">" >> "$JUNIT_REPORT"
        
        case "$result" in
            "fail")
                echo "            <failure message=\"Test failed\"><![CDATA[$message]]></failure>" >> "$JUNIT_REPORT"
                ;;
            "skip")
                echo "            <skipped message=\"$message\"/>" >> "$JUNIT_REPORT"
                ;;
        esac
        
        echo "        </testcase>" >> "$JUNIT_REPORT"
    done

    cat >> "$JUNIT_REPORT" <<EOF
    </testsuite>
</testsuites>
EOF

    log_info "JUnit report written to: $JUNIT_REPORT"
}

# Print summary
print_summary() {
    local end_time
    local total_duration
    
    end_time=$(date +%s)
    total_duration=$((end_time - START_TIME))
    
    echo ""
    log_header "================================================================="
    log_header "      E2E Test Suite Summary"
    log_header "================================================================="
    echo ""
    log_info "Total duration: ${total_duration}s"
    log_info "Total tests: $TOTAL_TESTS"
    log_info "Passed: $PASSED_TESTS"
    log_info "Failed: $FAILED_TESTS"
    log_info "Skipped: $SKIPPED_TESTS"
    echo ""
    
    if [[ $FAILED_TESTS -gt 0 ]]; then
        log_error "❌ E2E Test Suite FAILED"
        log_info "Review logs in: $RESULTS_DIR"
        return 1
    else
        log_info "✅ E2E Test Suite PASSED"
        return 0
    fi
}

# Cleanup on exit
cleanup() {
    teardown_infrastructure || true
}
trap cleanup EXIT

# Main execution
main() {
    setup_infrastructure || log_warn "Infrastructure setup had issues"
    
    if [[ "$RUN_GOLDEN_PATH" == "true" ]]; then
        run_golden_path_tests
    fi
    
    if [[ "$RUN_CHAOS" == "true" ]]; then
        run_chaos_tests
    fi
    
    if [[ "$RUN_INTEGRATION" == "true" ]]; then
        run_integration_tests
    fi
    
    generate_junit_report
    print_summary
}

main "$@"

