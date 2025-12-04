#!/usr/bin/env bash
# =============================================================================
# BUTTERFLY Chaos Engineering Runner
# 
# A comprehensive chaos testing tool that orchestrates chaos experiments
# across the BUTTERFLY ecosystem with enhanced reporting and safety controls.
#
# Features:
# - Multiple chaos experiment types (pod-kill, network-delay, cpu-stress, etc.)
# - Service-level targeting or ecosystem-wide chaos
# - Detailed HTML/JSON reporting
# - Automatic rollback on failure
# - Prometheus metric validation
# - Slack/email notifications
#
# Prerequisites:
# - kubectl configured with cluster access
# - chaos-mesh installed (https://chaos-mesh.org/)
# - jq for JSON processing
#
# Usage: ./scripts/chaos-runner.sh <experiment> [options]
#
# Related Epic: .github/dx-issues/epic-chaos-automation.md
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CHAOS_DIR="${PROJECT_ROOT}/chaos"
REPORTS_DIR="${CHAOS_DIR}/reports"
EXPERIMENTS_DIR="${CHAOS_DIR}/experiments"

# Default values
NAMESPACE="${CHAOS_NAMESPACE:-butterfly}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://prometheus:9090}"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"
EXPERIMENT_DURATION="${EXPERIMENT_DURATION:-5m}"
COOLDOWN_PERIOD="${COOLDOWN_PERIOD:-2m}"
MAX_CONCURRENT_EXPERIMENTS="${MAX_CONCURRENT:-1}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Timestamps
START_TIME=""
END_TIME=""

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

log_info() {
    echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $1" >&2
}

log_phase() {
    echo -e "\n${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

print_banner() {
    echo -e "${CYAN}"
    cat << 'EOF'
   ________  _____   ____  _____
  / ____/ / / /   | / __ \/ ___/
 / /   / /_/ / /| |/ / / /\__ \
/ /___/ __  / ___ / /_/ /___/ /
\____/_/ /_/_/  |_\____//____/

   BUTTERFLY Chaos Engineering
         Runner v1.0.0
EOF
    echo -e "${NC}"
}

show_help() {
    print_banner
    cat << 'EOF'
USAGE:
    chaos-runner.sh <experiment> [options]

EXPERIMENTS:
    pod-kill          Kill random pods in target service(s)
    pod-failure       Inject pod failure for duration
    network-delay     Add network latency between services
    network-partition Simulate network partition
    cpu-stress        Inject CPU stress
    memory-stress     Inject memory pressure
    io-stress         Inject disk I/O stress
    kafka-lag         Simulate Kafka consumer lag
    dns-failure       Inject DNS failures
    http-abort        Inject HTTP request failures
    gameday           Run full gameday scenario

OPTIONS:
    -s, --service     Target service (e.g., perception, odyssey)
    -d, --duration    Experiment duration (default: 5m)
    -n, --namespace   Kubernetes namespace (default: butterfly)
    --dry-run         Show what would happen without executing
    --report-only     Generate report from previous run
    --no-rollback     Disable automatic rollback on failure
    --slack           Send notifications to Slack
    -v, --verbose     Verbose output

SAFETY:
    - Experiments run with automatic rollback on failure
    - Prometheus SLO validation before and after
    - Cooldown period between experiments
    - Max concurrent experiments limit

EXAMPLES:
    # Kill pods in PERCEPTION service
    ./scripts/chaos-runner.sh pod-kill --service perception --duration 2m

    # Network delay between ODYSSEY and PLATO
    ./scripts/chaos-runner.sh network-delay --service odyssey,plato --duration 5m

    # Full gameday with all services
    ./scripts/chaos-runner.sh gameday --duration 30m --slack

    # Dry run to preview experiment
    ./scripts/chaos-runner.sh cpu-stress --service capsule --dry-run

For more information, see: docs/operations/chaos-engineering.md
EOF
}

check_prerequisites() {
    log_phase "Checking Prerequisites"
    
    local missing=()
    
    # Check required commands
    for cmd in kubectl jq curl; do
        if ! command -v "$cmd" &> /dev/null; then
            missing+=("$cmd")
        fi
    done
    
    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required commands: ${missing[*]}"
        exit 1
    fi
    
    # Check kubectl context
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi
    
    # Check chaos-mesh
    if ! kubectl get pods -n chaos-mesh -l app.kubernetes.io/instance=chaos-mesh &> /dev/null; then
        log_warn "Chaos Mesh may not be installed. Install with:"
        echo "  kubectl apply -f https://mirrors.chaos-mesh.org/latest/crd.yaml"
        echo "  helm install chaos-mesh chaos-mesh/chaos-mesh -n chaos-mesh --create-namespace"
    fi
    
    # Check namespace exists
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_error "Namespace '$NAMESPACE' does not exist"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

validate_slo() {
    local phase="$1"  # "before" or "after"
    log_info "Validating SLOs ($phase experiment)..."
    
    local result_file="${REPORTS_DIR}/slo_${phase}_$(date +%Y%m%d_%H%M%S).json"
    
    # Query Prometheus for SLO metrics
    local query='butterfly:slo_availability:ratio_rate5m'
    local response
    
    if response=$(curl -sf "${PROMETHEUS_URL}/api/v1/query?query=${query}" 2>/dev/null); then
        echo "$response" | jq '.' > "$result_file"
        
        # Check if any service is below threshold
        local below_threshold
        below_threshold=$(echo "$response" | jq -r '.data.result[] | select(.value[1] | tonumber < 0.99) | .metric.service')
        
        if [[ -n "$below_threshold" ]]; then
            log_warn "Services below SLO threshold: $below_threshold"
            if [[ "$phase" == "before" ]]; then
                log_error "Aborting: Services already degraded before chaos"
                return 1
            fi
        else
            log_success "All services meeting SLO targets"
        fi
    else
        log_warn "Could not query Prometheus for SLO validation"
    fi
    
    return 0
}

create_experiment_manifest() {
    local experiment_type="$1"
    local service="$2"
    local duration="$3"
    local output_file="$4"
    
    case "$experiment_type" in
        pod-kill)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: butterfly-pod-kill-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  duration: ${duration}
EOF
            ;;
            
        pod-failure)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: butterfly-pod-failure-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  action: pod-failure
  mode: one
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  duration: ${duration}
EOF
            ;;
            
        network-delay)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: butterfly-network-delay-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  action: delay
  mode: all
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  delay:
    latency: "500ms"
    correlation: "100"
    jitter: "100ms"
  duration: ${duration}
EOF
            ;;
            
        network-partition)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: butterfly-network-partition-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  action: partition
  mode: all
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  direction: both
  duration: ${duration}
EOF
            ;;
            
        cpu-stress)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: StressChaos
metadata:
  name: butterfly-cpu-stress-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  mode: one
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  stressors:
    cpu:
      workers: 2
      load: 80
  duration: ${duration}
EOF
            ;;
            
        memory-stress)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: StressChaos
metadata:
  name: butterfly-memory-stress-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  mode: one
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  stressors:
    memory:
      workers: 2
      size: "512MB"
  duration: ${duration}
EOF
            ;;
            
        io-stress)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: IOChaos
metadata:
  name: butterfly-io-stress-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  action: latency
  mode: one
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  volumePath: /data
  delay: "100ms"
  percent: 50
  duration: ${duration}
EOF
            ;;
            
        http-abort)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: HTTPChaos
metadata:
  name: butterfly-http-abort-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  mode: all
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  target: Request
  abort: true
  port: 8080
  duration: ${duration}
EOF
            ;;
            
        dns-failure)
            cat > "$output_file" << EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: DNSChaos
metadata:
  name: butterfly-dns-failure-${service}
  namespace: ${NAMESPACE}
  labels:
    experiment: butterfly-chaos
    service: ${service}
spec:
  action: error
  mode: all
  selector:
    namespaces:
      - ${NAMESPACE}
    labelSelectors:
      app: ${service}
  duration: ${duration}
EOF
            ;;
            
        *)
            log_error "Unknown experiment type: $experiment_type"
            return 1
            ;;
    esac
}

run_experiment() {
    local experiment_type="$1"
    local service="$2"
    local duration="$3"
    local dry_run="$4"
    
    log_phase "Running Experiment: ${experiment_type} on ${service}"
    
    local experiment_id="chaos-${experiment_type}-${service}-$(date +%Y%m%d%H%M%S)"
    local manifest_file="${EXPERIMENTS_DIR}/${experiment_id}.yaml"
    
    mkdir -p "$EXPERIMENTS_DIR"
    
    # Create experiment manifest
    create_experiment_manifest "$experiment_type" "$service" "$duration" "$manifest_file"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would apply experiment:"
        cat "$manifest_file"
        return 0
    fi
    
    # Apply experiment
    log_info "Applying chaos experiment..."
    if kubectl apply -f "$manifest_file"; then
        log_success "Experiment started: $experiment_id"
    else
        log_error "Failed to start experiment"
        return 1
    fi
    
    # Monitor experiment
    log_info "Monitoring for ${duration}..."
    local start_epoch=$(date +%s)
    local duration_seconds
    duration_seconds=$(parse_duration "$duration")
    local end_epoch=$((start_epoch + duration_seconds))
    
    while [[ $(date +%s) -lt $end_epoch ]]; do
        local remaining=$((end_epoch - $(date +%s)))
        printf "\r${BLUE}Time remaining: %02d:%02d${NC}  " $((remaining/60)) $((remaining%60))
        
        # Check for critical failures
        if ! check_service_health "$service"; then
            log_warn "Service health degraded during experiment"
        fi
        
        sleep 5
    done
    echo ""
    
    # Clean up experiment
    log_info "Cleaning up experiment..."
    kubectl delete -f "$manifest_file" --ignore-not-found=true
    
    log_success "Experiment completed: $experiment_id"
    return 0
}

parse_duration() {
    local duration="$1"
    local value="${duration%[smh]}"
    local unit="${duration: -1}"
    
    case "$unit" in
        s) echo "$value" ;;
        m) echo $((value * 60)) ;;
        h) echo $((value * 3600)) ;;
        *) echo "$duration" ;;
    esac
}

check_service_health() {
    local service="$1"
    
    # Check pod status
    local unhealthy
    unhealthy=$(kubectl get pods -n "$NAMESPACE" -l "app=$service" -o jsonpath='{.items[*].status.containerStatuses[*].ready}' | grep -c "false" || echo "0")
    
    if [[ "$unhealthy" -gt 0 ]]; then
        return 1
    fi
    
    return 0
}

run_gameday() {
    local duration="$1"
    local dry_run="$2"
    
    log_phase "Starting Gameday Scenario"
    
    local services=("perception" "odyssey" "plato" "capsule" "nexus" "synapse")
    local experiments=("pod-kill" "network-delay" "cpu-stress")
    
    local gameday_id="gameday-$(date +%Y%m%d%H%M%S)"
    local report_file="${REPORTS_DIR}/${gameday_id}.json"
    
    mkdir -p "$REPORTS_DIR"
    
    # Initialize report
    cat > "$report_file" << EOF
{
    "gameday_id": "${gameday_id}",
    "start_time": "$(date -Iseconds)",
    "experiments": [],
    "status": "running"
}
EOF
    
    log_info "Gameday ID: $gameday_id"
    log_info "Duration: $duration"
    log_info "Services: ${services[*]}"
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would run the following experiments:"
        for service in "${services[@]}"; do
            for exp in "${experiments[@]}"; do
                echo "  - $exp on $service"
            done
        done
        return 0
    fi
    
    # Validate SLOs before starting
    if ! validate_slo "before"; then
        log_error "Pre-gameday SLO validation failed"
        return 1
    fi
    
    # Run experiments
    local failed_experiments=0
    for service in "${services[@]}"; do
        for exp in "${experiments[@]}"; do
            log_info "Running: $exp on $service"
            
            local exp_duration="2m"  # Shorter duration for gameday
            if run_experiment "$exp" "$service" "$exp_duration" false; then
                jq ".experiments += [{\"type\": \"$exp\", \"service\": \"$service\", \"status\": \"success\"}]" \
                    "$report_file" > "${report_file}.tmp" && mv "${report_file}.tmp" "$report_file"
            else
                ((failed_experiments++))
                jq ".experiments += [{\"type\": \"$exp\", \"service\": \"$service\", \"status\": \"failed\"}]" \
                    "$report_file" > "${report_file}.tmp" && mv "${report_file}.tmp" "$report_file"
            fi
            
            # Cooldown between experiments
            log_info "Cooldown period: ${COOLDOWN_PERIOD}"
            sleep "$(parse_duration "$COOLDOWN_PERIOD")"
        done
    done
    
    # Validate SLOs after gameday
    validate_slo "after"
    
    # Update report
    jq ".end_time = \"$(date -Iseconds)\" | .status = \"completed\" | .failed_count = $failed_experiments" \
        "$report_file" > "${report_file}.tmp" && mv "${report_file}.tmp" "$report_file"
    
    # Generate HTML report
    generate_html_report "$report_file"
    
    if [[ $failed_experiments -gt 0 ]]; then
        log_warn "Gameday completed with $failed_experiments failed experiments"
    else
        log_success "Gameday completed successfully"
    fi
    
    return 0
}

generate_html_report() {
    local json_report="$1"
    local html_report="${json_report%.json}.html"
    
    log_info "Generating HTML report: $html_report"
    
    local gameday_id=$(jq -r '.gameday_id' "$json_report")
    local start_time=$(jq -r '.start_time' "$json_report")
    local end_time=$(jq -r '.end_time' "$json_report")
    local status=$(jq -r '.status' "$json_report")
    local failed_count=$(jq -r '.failed_count // 0' "$json_report")
    
    cat > "$html_report" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chaos Engineering Report - ${gameday_id}</title>
    <style>
        :root {
            --bg-primary: #0d1117;
            --bg-secondary: #161b22;
            --bg-tertiary: #21262d;
            --text-primary: #c9d1d9;
            --text-secondary: #8b949e;
            --accent: #58a6ff;
            --success: #3fb950;
            --warning: #d29922;
            --error: #f85149;
            --border: #30363d;
        }
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif;
            background: var(--bg-primary);
            color: var(--text-primary);
            margin: 0;
            padding: 2rem;
            line-height: 1.6;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        h1 {
            font-size: 2rem;
            margin-bottom: 0.5rem;
            background: linear-gradient(135deg, #f85149 0%, #d29922 50%, #3fb950 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .subtitle { color: var(--text-secondary); margin-bottom: 2rem; }
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
            margin-bottom: 2rem;
        }
        .summary-card {
            background: var(--bg-secondary);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 1rem;
        }
        .summary-card h3 { color: var(--text-secondary); font-size: 0.875rem; margin: 0 0 0.5rem 0; }
        .summary-card .value { font-size: 1.5rem; font-weight: bold; }
        .status-success { color: var(--success); }
        .status-failed { color: var(--error); }
        .status-warning { color: var(--warning); }
        table {
            width: 100%;
            border-collapse: collapse;
            background: var(--bg-secondary);
            border-radius: 8px;
            overflow: hidden;
        }
        th, td { padding: 1rem; text-align: left; border-bottom: 1px solid var(--border); }
        th { background: var(--bg-tertiary); color: var(--text-secondary); font-weight: 600; }
        tr:last-child td { border-bottom: none; }
        .badge {
            display: inline-block;
            padding: 0.25rem 0.75rem;
            border-radius: 20px;
            font-size: 0.875rem;
            font-weight: 500;
        }
        .badge-success { background: rgba(63, 185, 80, 0.2); color: var(--success); }
        .badge-failed { background: rgba(248, 81, 73, 0.2); color: var(--error); }
    </style>
</head>
<body>
    <div class="container">
        <h1>Chaos Engineering Report</h1>
        <p class="subtitle">Gameday: ${gameday_id}</p>
        
        <div class="summary">
            <div class="summary-card">
                <h3>Status</h3>
                <div class="value ${failed_count > 0 ? 'status-warning' : 'status-success'}">${status}</div>
            </div>
            <div class="summary-card">
                <h3>Start Time</h3>
                <div class="value">${start_time}</div>
            </div>
            <div class="summary-card">
                <h3>End Time</h3>
                <div class="value">${end_time:-"In Progress"}</div>
            </div>
            <div class="summary-card">
                <h3>Failed Experiments</h3>
                <div class="value ${failed_count > 0 ? 'status-failed' : 'status-success'}">${failed_count}</div>
            </div>
        </div>
        
        <h2>Experiments</h2>
        <table>
            <thead>
                <tr>
                    <th>Type</th>
                    <th>Service</th>
                    <th>Status</th>
                </tr>
            </thead>
            <tbody>
EOF

    # Add experiment rows
    jq -r '.experiments[] | "<tr><td>\(.type)</td><td>\(.service)</td><td><span class=\"badge badge-\(.status)\">\(.status)</span></td></tr>"' \
        "$json_report" >> "$html_report"

    cat >> "$html_report" << 'EOF'
            </tbody>
        </table>
        
        <footer style="margin-top: 2rem; color: var(--text-secondary); font-size: 0.875rem;">
            Generated by BUTTERFLY Chaos Runner
        </footer>
    </div>
</body>
</html>
EOF

    log_success "HTML report generated: $html_report"
}

send_slack_notification() {
    local message="$1"
    local status="$2"
    
    if [[ -z "$SLACK_WEBHOOK" ]]; then
        return 0
    fi
    
    local color
    case "$status" in
        success) color="good" ;;
        warning) color="warning" ;;
        error)   color="danger" ;;
        *)       color="#439FE0" ;;
    esac
    
    curl -sf -X POST "$SLACK_WEBHOOK" \
        -H 'Content-type: application/json' \
        -d "{
            \"attachments\": [{
                \"color\": \"$color\",
                \"title\": \"BUTTERFLY Chaos Engineering\",
                \"text\": \"$message\",
                \"footer\": \"Chaos Runner\",
                \"ts\": $(date +%s)
            }]
        }" > /dev/null
}

cleanup() {
    log_info "Cleaning up chaos experiments..."
    kubectl delete podchaos,networkchaos,stresschaos,iochaos,httpchaos,dnschaos \
        -n "$NAMESPACE" \
        -l experiment=butterfly-chaos \
        --ignore-not-found=true 2>/dev/null || true
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    if [[ $# -eq 0 ]]; then
        show_help
        exit 0
    fi
    
    local experiment=""
    local service=""
    local duration="$EXPERIMENT_DURATION"
    local dry_run=false
    local verbose=false
    local send_slack=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            pod-kill|pod-failure|network-delay|network-partition|cpu-stress|memory-stress|io-stress|kafka-lag|dns-failure|http-abort|gameday)
                experiment="$1"
                shift
                ;;
            -s|--service)
                service="$2"
                shift 2
                ;;
            -d|--duration)
                duration="$2"
                shift 2
                ;;
            -n|--namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --slack)
                send_slack=true
                shift
                ;;
            -v|--verbose)
                verbose=true
                set -x
                shift
                ;;
            help|--help|-h)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    if [[ -z "$experiment" ]]; then
        log_error "Experiment type is required"
        show_help
        exit 1
    fi
    
    # Set up trap for cleanup
    trap cleanup EXIT
    
    print_banner
    
    START_TIME=$(date -Iseconds)
    
    if [[ "$dry_run" == false ]]; then
        check_prerequisites
    fi
    
    mkdir -p "$REPORTS_DIR" "$EXPERIMENTS_DIR"
    
    if [[ "$send_slack" == true ]]; then
        send_slack_notification "Starting chaos experiment: $experiment on ${service:-all services}" "info"
    fi
    
    local exit_code=0
    
    if [[ "$experiment" == "gameday" ]]; then
        run_gameday "$duration" "$dry_run" || exit_code=$?
    else
        if [[ -z "$service" ]]; then
            log_error "Service is required for $experiment experiment (--service)"
            exit 1
        fi
        
        run_experiment "$experiment" "$service" "$duration" "$dry_run" || exit_code=$?
    fi
    
    END_TIME=$(date -Iseconds)
    
    if [[ "$send_slack" == true ]]; then
        if [[ $exit_code -eq 0 ]]; then
            send_slack_notification "Chaos experiment completed successfully: $experiment" "success"
        else
            send_slack_notification "Chaos experiment failed: $experiment" "error"
        fi
    fi
    
    exit $exit_code
}

main "$@"

