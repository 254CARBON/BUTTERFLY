#!/bin/bash
# =============================================================================
# BUTTERFLY Chaos Experiment Runner
# =============================================================================
# This script provides a safe way to run chaos experiments against the
# BUTTERFLY platform with proper safeguards and monitoring.
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXPERIMENTS_DIR="${SCRIPT_DIR}/experiments"
NAMESPACE="${BUTTERFLY_NAMESPACE:-butterfly}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed"
        exit 1
    fi
    
    # Check Chaos Mesh CRDs
    if ! kubectl get crd podchaos.chaos-mesh.org &> /dev/null; then
        log_error "Chaos Mesh is not installed in the cluster"
        echo "Install with: helm install chaos-mesh chaos-mesh/chaos-mesh -n chaos-mesh --create-namespace"
        exit 1
    fi
    
    # Check namespace exists
    if ! kubectl get namespace "${NAMESPACE}" &> /dev/null; then
        log_error "Namespace ${NAMESPACE} does not exist"
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

# Safety check - confirm environment
safety_check() {
    local env="${1:-unknown}"
    
    if [ "$env" = "prod" ] || [ "$env" = "production" ]; then
        log_error "REFUSING TO RUN CHAOS EXPERIMENTS IN PRODUCTION"
        echo ""
        echo "Chaos experiments should NEVER be run in production without explicit approval."
        echo "If you truly need to run this, use: FORCE_PROD=true $0 $@"
        
        if [ "${FORCE_PROD}" != "true" ]; then
            exit 1
        fi
        
        log_warn "FORCE_PROD is set - proceeding with extreme caution"
        read -p "Type 'I ACCEPT THE RISK' to continue: " confirmation
        if [ "$confirmation" != "I ACCEPT THE RISK" ]; then
            log_error "Confirmation failed. Aborting."
            exit 1
        fi
    fi
}

# Pre-flight health check
preflight_check() {
    log_info "Running pre-flight health checks..."
    
    local services=("perception" "capsule" "odyssey" "plato" "butterfly-nexus" "synapse")
    local ports=(8085 8081 8080 8082 8083 8084)
    local all_healthy=true
    
    for i in "${!services[@]}"; do
        local svc="${services[$i]}"
        local port="${ports[$i]}"
        
        if kubectl exec -n "${NAMESPACE}" deploy/"${svc}" -- curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            log_success "${svc} is healthy"
        else
            log_warn "${svc} health check failed"
            all_healthy=false
        fi
    done
    
    if [ "$all_healthy" = false ]; then
        log_warn "Some services are not healthy. Continue anyway? (y/N)"
        read -r response
        if [ "$response" != "y" ] && [ "$response" != "Y" ]; then
            log_error "Aborting due to unhealthy services"
            exit 1
        fi
    fi
}

# Run a single experiment
run_experiment() {
    local experiment_file="$1"
    local experiment_name=$(basename "$experiment_file" .yaml)
    
    log_info "Starting experiment: ${experiment_name}"
    echo "  File: ${experiment_file}"
    echo "  Namespace: ${NAMESPACE}"
    echo ""
    
    # Apply the experiment
    kubectl apply -f "${experiment_file}" -n "${NAMESPACE}"
    
    log_success "Experiment ${experiment_name} started"
    echo ""
    echo "Monitor with:"
    echo "  kubectl get podchaos,networkchaos,stresschaos,workflow -n ${NAMESPACE} -w"
    echo ""
    echo "Stop with:"
    echo "  kubectl delete -f ${experiment_file} -n ${NAMESPACE}"
}

# Stop all experiments
stop_all() {
    log_info "Stopping all chaos experiments in namespace ${NAMESPACE}..."
    
    kubectl delete podchaos --all -n "${NAMESPACE}" 2>/dev/null || true
    kubectl delete networkchaos --all -n "${NAMESPACE}" 2>/dev/null || true
    kubectl delete stresschaos --all -n "${NAMESPACE}" 2>/dev/null || true
    kubectl delete workflow --all -n "${NAMESPACE}" 2>/dev/null || true
    
    log_success "All experiments stopped"
}

# List available experiments
list_experiments() {
    echo "Available experiments:"
    echo ""
    
    for file in "${EXPERIMENTS_DIR}"/*.yaml; do
        if [ -f "$file" ]; then
            local name=$(basename "$file" .yaml)
            local desc=$(grep -m1 "^# Purpose:" "$file" | sed 's/# Purpose: //' || echo "No description")
            echo "  ${name}"
            echo "    ${desc}"
            echo ""
        fi
    done
}

# Show status
show_status() {
    log_info "Current chaos experiments status:"
    echo ""
    
    echo "=== PodChaos ==="
    kubectl get podchaos -n "${NAMESPACE}" 2>/dev/null || echo "None"
    echo ""
    
    echo "=== NetworkChaos ==="
    kubectl get networkchaos -n "${NAMESPACE}" 2>/dev/null || echo "None"
    echo ""
    
    echo "=== StressChaos ==="
    kubectl get stresschaos -n "${NAMESPACE}" 2>/dev/null || echo "None"
    echo ""
    
    echo "=== Workflows ==="
    kubectl get workflow -n "${NAMESPACE}" 2>/dev/null || echo "None"
}

# Main
main() {
    case "${1:-}" in
        run)
            check_prerequisites
            safety_check "${2:-staging}"
            preflight_check
            
            if [ -z "${3:-}" ]; then
                log_error "Usage: $0 run <environment> <experiment-name>"
                echo ""
                list_experiments
                exit 1
            fi
            
            local experiment_file="${EXPERIMENTS_DIR}/${3}.yaml"
            if [ ! -f "$experiment_file" ]; then
                log_error "Experiment file not found: ${experiment_file}"
                list_experiments
                exit 1
            fi
            
            run_experiment "$experiment_file"
            ;;
            
        stop)
            stop_all
            ;;
            
        status)
            show_status
            ;;
            
        list)
            list_experiments
            ;;
            
        workflow)
            check_prerequisites
            safety_check "${2:-staging}"
            preflight_check
            
            log_info "Starting full ecosystem resilience workflow..."
            kubectl apply -f "${EXPERIMENTS_DIR}/full-ecosystem-resilience.yaml" -n "${NAMESPACE}"
            
            echo ""
            echo "Workflow started. Monitor with:"
            echo "  kubectl get workflow butterfly-ecosystem-resilience-test -n ${NAMESPACE} -w"
            ;;
            
        *)
            echo "BUTTERFLY Chaos Experiment Runner"
            echo ""
            echo "Usage: $0 <command> [options]"
            echo ""
            echo "Commands:"
            echo "  run <env> <experiment>   Run a single experiment"
            echo "  workflow <env>           Run full ecosystem resilience workflow"
            echo "  stop                     Stop all running experiments"
            echo "  status                   Show status of running experiments"
            echo "  list                     List available experiments"
            echo ""
            echo "Environment: staging, dev (never prod)"
            echo ""
            echo "Examples:"
            echo "  $0 run staging odyssey-down"
            echo "  $0 run dev capsule-degraded"
            echo "  $0 workflow staging"
            echo "  $0 stop"
            ;;
    esac
}

main "$@"

