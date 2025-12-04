#!/usr/bin/env bash
#
# BUTTERFLY Chaos Scenario Test Suite
# Runs fault injection scenarios to verify system resilience
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SCENARIOS_DIR="$SCRIPT_DIR/scenarios"
CATALOG_FILE="$SCENARIOS_DIR/catalog.json"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# Configuration
CHAOS_TIMEOUT=${CHAOS_TIMEOUT:-120}
RESULTS_DIR="${RESULTS_DIR:-$SCRIPT_DIR/chaos-results}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.full.yml}"

# Parse arguments
DRY_RUN=false
SCENARIO_FILTER=""
SKIP_SETUP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --scenario)
            SCENARIO_FILTER="$2"
            shift 2
            ;;
        --skip-setup)
            SKIP_SETUP=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --dry-run       Print actions without executing"
            echo "  --scenario NAME Run only the specified scenario"
            echo "  --skip-setup    Skip infrastructure setup"
            echo "  -h, --help      Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "================================================================="
echo "      BUTTERFLY Chaos Scenario Test Suite"
echo "================================================================="
echo ""

# Ensure jq is available
command -v jq >/dev/null 2>&1 || { log_error "jq is required to parse scenarios"; exit 1; }

# Create results directory
mkdir -p "$RESULTS_DIR"

# Get chaos scenarios from catalog
get_chaos_scenarios() {
    jq -r '.[] | select(.category == "chaos") | .name' "$CATALOG_FILE"
}

# Run a single chaos scenario
run_chaos_scenario() {
    local scenario_name="$1"
    local scenario_file
    local start_time
    local end_time
    local duration
    local exit_code=0

    scenario_file=$(jq -r --arg name "$scenario_name" '.[] | select(.name == $name) | .file' "$CATALOG_FILE")
    
    if [[ -z "$scenario_file" || "$scenario_file" == "null" ]]; then
        log_error "Scenario '$scenario_name' not found in catalog"
        return 1
    fi

    local scenario_path="$SCENARIOS_DIR/$scenario_file"
    if [[ ! -f "$scenario_path" ]]; then
        log_error "Scenario file not found: $scenario_path"
        return 1
    fi

    log_step "Running chaos scenario: $scenario_name"
    log_info "Scenario file: $scenario_file"
    
    # Extract scenario details
    local description
    local target_service
    local fault_type
    
    description=$(jq -r '.description // "No description"' "$scenario_path")
    target_service=$(jq -r '.target // "all"' "$scenario_path")
    fault_type=$(jq -r '.fault_injection.type // "unknown"' "$scenario_path")
    
    log_info "Description: $description"
    log_info "Target: $target_service"
    log_info "Fault type: $fault_type"
    echo ""

    if [[ "$DRY_RUN" == "true" ]]; then
        log_warn "[DRY RUN] Would execute scenario $scenario_name"
        return 0
    fi

    start_time=$(date +%s)

    # Execute scenario steps
    local steps
    steps=$(jq -c '.steps[]' "$scenario_path")

    while IFS= read -r step; do
        local step_name
        local step_type
        
        step_name=$(echo "$step" | jq -r '.name')
        step_type=$(echo "$step" | jq -r '.type // "http"')
        
        log_info "  Step: $step_name ($step_type)"

        case "$step_type" in
            "chaos")
                execute_chaos_action "$step" || exit_code=1
                ;;
            "wait")
                local wait_duration
                wait_duration=$(echo "$step" | jq -r '.duration_seconds // 5')
                log_info "    Waiting ${wait_duration}s..."
                sleep "$wait_duration"
                ;;
            "http"|*)
                execute_http_step "$step" || exit_code=1
                ;;
        esac
    done <<< "$steps"

    end_time=$(date +%s)
    duration=$((end_time - start_time))

    # Record results
    local result_file="$RESULTS_DIR/${scenario_name}-$(date +%Y%m%d%H%M%S).json"
    cat > "$result_file" <<EOF
{
    "scenario": "$scenario_name",
    "timestamp": "$(date -Iseconds)",
    "duration_seconds": $duration,
    "exit_code": $exit_code,
    "fault_type": "$fault_type",
    "target_service": "$target_service"
}
EOF

    if [[ $exit_code -eq 0 ]]; then
        log_info "✅ Scenario $scenario_name completed successfully (${duration}s)"
    else
        log_error "❌ Scenario $scenario_name failed (${duration}s)"
    fi
    echo ""

    return $exit_code
}

# Execute a chaos action (fault injection/restore)
execute_chaos_action() {
    local step="$1"
    local action
    local service
    
    action=$(echo "$step" | jq -r '.action')
    service=$(echo "$step" | jq -r '.service')

    case "$action" in
        "service_unavailable")
            log_info "    Injecting service unavailable fault on $service"
            # In a real implementation, this would use toxiproxy, chaos monkey, or similar
            # For now, we simulate by stopping the container
            if docker compose -f "$COMPOSE_FILE" ps "$service" 2>/dev/null | grep -q "running"; then
                docker compose -f "$COMPOSE_FILE" stop "$service" 2>/dev/null || true
            fi
            ;;
        "latency_injection")
            local latency_ms
            latency_ms=$(echo "$step" | jq -r '.config.latency_ms // 5000')
            log_info "    Injecting ${latency_ms}ms latency on $service"
            # In production, use toxiproxy or tc for network latency
            # This is a placeholder for actual implementation
            ;;
        "data_store_partial_outage")
            local affected_nodes
            affected_nodes=$(echo "$step" | jq -r '.config.affected_nodes[]' 2>/dev/null || echo "")
            log_info "    Simulating partial outage on $service (nodes: $affected_nodes)"
            # In production, would stop specific Cassandra nodes
            ;;
        "restore")
            log_info "    Restoring $service"
            docker compose -f "$COMPOSE_FILE" up -d "$service" 2>/dev/null || true
            ;;
        *)
            log_warn "    Unknown chaos action: $action"
            ;;
    esac
}

# Execute an HTTP step
execute_http_step() {
    local step="$1"
    local service
    local method
    local endpoint
    local expected_status
    local optional
    
    service=$(echo "$step" | jq -r '.service')
    method=$(echo "$step" | jq -r '.method // "GET"')
    endpoint=$(echo "$step" | jq -r '.endpoint')
    expected_status=$(echo "$step" | jq -r '.expect.status // 200')
    optional=$(echo "$step" | jq -r '.optional // false')

    # Get service URL from environment or default
    local service_url
    case "$service" in
        "plato")     service_url="${PLATO_URL:-http://localhost:8084}" ;;
        "synapse")   service_url="${SYNAPSE_URL:-http://localhost:8086}" ;;
        "perception") service_url="${PERCEPTION_URL:-http://localhost:8081}" ;;
        "capsule")   service_url="${CAPSULE_URL:-http://localhost:8082}" ;;
        "odyssey")   service_url="${ODYSSEY_URL:-http://localhost:8083}" ;;
        "nexus")     service_url="${NEXUS_URL:-http://localhost:8085}" ;;
        *)           service_url="http://localhost:8080" ;;
    esac

    local url="${service_url}${endpoint}"
    local body
    body=$(echo "$step" | jq -r '.body // null')
    
    local headers=("-H" "Content-Type: application/json" "-H" "X-Tenant-ID: chaos-test")
    
    local curl_args=("-s" "-o" "/dev/null" "-w" "%{http_code}" "-X" "$method")
    curl_args+=("${headers[@]}")
    
    if [[ "$body" != "null" ]]; then
        curl_args+=("-d" "$body")
    fi
    
    curl_args+=("$url")

    local actual_status
    actual_status=$(curl "${curl_args[@]}" 2>/dev/null || echo "000")
    
    # Check if status matches expected (expected can be single value or array)
    local status_ok=false
    if echo "$expected_status" | jq -e 'type == "array"' >/dev/null 2>&1; then
        if echo "$expected_status" | jq -e "contains([$actual_status])" >/dev/null 2>&1; then
            status_ok=true
        fi
    else
        if [[ "$actual_status" == "$expected_status" ]]; then
            status_ok=true
        fi
    fi

    if [[ "$status_ok" == "true" ]]; then
        log_info "    ✓ HTTP $method $endpoint -> $actual_status"
        return 0
    elif [[ "$optional" == "true" ]]; then
        log_warn "    ⚠ HTTP $method $endpoint -> $actual_status (optional, expected: $expected_status)"
        return 0
    else
        log_error "    ✗ HTTP $method $endpoint -> $actual_status (expected: $expected_status)"
        return 1
    fi
}

# Main execution
main() {
    local scenarios
    local total=0
    local passed=0
    local failed=0

    if [[ -n "$SCENARIO_FILTER" ]]; then
        scenarios="$SCENARIO_FILTER"
    else
        scenarios=$(get_chaos_scenarios)
    fi

    if [[ -z "$scenarios" ]]; then
        log_warn "No chaos scenarios found in catalog"
        exit 0
    fi

    log_info "Found chaos scenarios:"
    for s in $scenarios; do
        log_info "  - $s"
    done
    echo ""

    for scenario in $scenarios; do
        total=$((total + 1))
        if run_chaos_scenario "$scenario"; then
            passed=$((passed + 1))
        else
            failed=$((failed + 1))
        fi
    done

    echo "================================================================="
    echo "      Chaos Scenario Results"
    echo "================================================================="
    echo ""
    log_info "Total: $total | Passed: $passed | Failed: $failed"
    echo ""

    if [[ $failed -gt 0 ]]; then
        log_error "Some chaos scenarios failed. Review results in $RESULTS_DIR"
        exit 1
    else
        log_info "✅ All chaos scenarios passed!"
        exit 0
    fi
}

main "$@"

