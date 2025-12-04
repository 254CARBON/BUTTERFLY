#!/usr/bin/env bash
# =============================================================================
# BUTTERFLY API Documentation Generator
#
# Generates OpenAPI specifications from running Spring Boot services.
# This script starts a service, waits for it to be healthy, downloads the
# OpenAPI spec, and then gracefully shuts down the service.
#
# Usage: ./scripts/generate-api-docs.sh [options]
#
# Options:
#   -s, --service     Service module path (e.g., PERCEPTION/perception-api)
#   -p, --port        Port the service runs on (default: 8080)
#   -o, --output      Output directory for generated specs (default: docs/api/openapi)
#   --timeout         Health check timeout in seconds (default: 120)
#   --skip-build      Skip Maven build before running
#   --all             Generate specs for all configured services
#
# Examples:
#   ./scripts/generate-api-docs.sh --service PERCEPTION/perception-api --port 8080
#   ./scripts/generate-api-docs.sh --all
#
# Related Epic: .github/dx-issues/epic-api-docs.md
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default values
DEFAULT_PORT=8080
DEFAULT_TIMEOUT=120
DEFAULT_OUTPUT_DIR="${PROJECT_ROOT}/docs/api/openapi"

# Service PID tracking
SERVICE_PID=""

# Known services configuration (service_path:port:spec_name)
KNOWN_SERVICES=(
    "PERCEPTION/perception-api:8080:perception-api"
    "CAPSULE:8081:capsule-api"
    "ODYSSEY/odyssey-core:8082:odyssey-api"
    "PLATO:8083:plato-api"
    "butterfly-nexus:8084:nexus-api"
)

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

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
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

print_banner() {
    echo -e "${CYAN}"
    cat << 'EOF'
    ____  __  _____________ ____  ______  __
   / __ )/ / / /_  __/_  __/ __ \/ ____/ / /  __  __
  / __  / / / / / /   / / / /_/ / /_    / /  / / / /
 / /_/ / /_/ / / /   / / / _, _/ __/   / /__/ /_/ /
/_____/\____/ /_/   /_/ /_/ |_/_/     /_____|__, /
                                           /____/
       API Documentation Generator v1.0.0
EOF
    echo -e "${NC}"
}

show_help() {
    print_banner
    cat << 'EOF'
USAGE:
    generate-api-docs.sh [options]

OPTIONS:
    -s, --service     Service module path (e.g., PERCEPTION/perception-api)
    -p, --port        Port the service runs on (default: 8080)
    -o, --output      Output directory for generated specs (default: docs/api/openapi)
    --timeout         Health check timeout in seconds (default: 120)
    --skip-build      Skip Maven build before running
    --all             Generate specs for all configured services
    --dry-run         Show what would be done without executing
    -h, --help        Show this help message

EXAMPLES:
    Generate spec for PERCEPTION API:
        ./scripts/generate-api-docs.sh --service PERCEPTION/perception-api --port 8080

    Generate specs for all services:
        ./scripts/generate-api-docs.sh --all

    Skip build (use existing artifacts):
        ./scripts/generate-api-docs.sh --service PERCEPTION/perception-api --skip-build

    Custom output directory:
        ./scripts/generate-api-docs.sh --service PERCEPTION/perception-api -o ./target/openapi

KNOWN SERVICES:
    The --all flag generates specs for these pre-configured services:
EOF
    for svc in "${KNOWN_SERVICES[@]}"; do
        IFS=':' read -r path port name <<< "$svc"
        echo "      - $path (port $port) -> $name.json"
    done
    echo ""
    echo "For CI integration, see: .github/workflows/api-docs.yml"
}

cleanup() {
    if [[ -n "$SERVICE_PID" ]] && kill -0 "$SERVICE_PID" 2>/dev/null; then
        log_info "Shutting down service (PID: $SERVICE_PID)..."
        kill "$SERVICE_PID" 2>/dev/null || true
        wait "$SERVICE_PID" 2>/dev/null || true
        SERVICE_PID=""
    fi
}

trap cleanup EXIT INT TERM

# -----------------------------------------------------------------------------
# Core Functions
# -----------------------------------------------------------------------------

check_dependencies() {
    local missing=()
    
    if ! command -v curl &> /dev/null; then
        missing+=("curl")
    fi
    
    if ! command -v jq &> /dev/null; then
        missing+=("jq")
    fi
    
    if ! command -v mvn &> /dev/null; then
        missing+=("mvn (Maven)")
    fi
    
    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required dependencies: ${missing[*]}"
        log_info "Install them with: brew install ${missing[*]} (macOS) or apt-get install ${missing[*]} (Linux)"
        exit 1
    fi
}

wait_for_health() {
    local port="$1"
    local timeout="$2"
    local elapsed=0
    local interval=2
    
    log_info "Waiting for service to be healthy (timeout: ${timeout}s)..."
    
    while [[ $elapsed -lt $timeout ]]; do
        # Try actuator health endpoint first, then root
        if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            log_success "Service is healthy!"
            return 0
        fi
        
        # Also try the OpenAPI endpoint directly
        if curl -sf "http://localhost:${port}/v3/api-docs" > /dev/null 2>&1; then
            log_success "OpenAPI endpoint is available!"
            return 0
        fi
        
        sleep $interval
        elapsed=$((elapsed + interval))
        echo -n "."
    done
    
    echo ""
    log_error "Service failed to become healthy within ${timeout}s"
    return 1
}

generate_spec() {
    local service_path="$1"
    local port="$2"
    local output_dir="$3"
    local spec_name="$4"
    local skip_build="$5"
    local timeout="$6"
    local dry_run="$7"
    
    local service_dir="${PROJECT_ROOT}/${service_path}"
    local output_file="${output_dir}/${spec_name}.json"
    
    # Validate service directory
    if [[ ! -d "$service_dir" ]]; then
        log_error "Service directory not found: $service_dir"
        return 1
    fi
    
    if [[ ! -f "${service_dir}/pom.xml" ]]; then
        log_error "No pom.xml found in: $service_dir"
        return 1
    fi
    
    if [[ "$dry_run" == true ]]; then
        log_info "[DRY RUN] Would generate: $output_file"
        log_info "  Service: $service_path"
        log_info "  Port: $port"
        log_info "  Build: $(if [[ "$skip_build" == true ]]; then echo "skip"; else echo "yes"; fi)"
        return 0
    fi
    
    log_info "Generating OpenAPI spec for: $service_path"
    log_info "  Port: $port"
    log_info "  Output: $output_file"
    
    # Ensure output directory exists
    mkdir -p "$output_dir"
    
    # Build the service if not skipping
    if [[ "$skip_build" != true ]]; then
        log_info "Building service..."
        if ! mvn -f "$service_dir/pom.xml" package -DskipTests -q; then
            log_error "Maven build failed"
            return 1
        fi
        log_success "Build complete"
    fi
    
    # Start the service in background
    log_info "Starting service on port $port..."
    
    # Use Spring Boot Maven plugin to run the service
    cd "$service_dir"
    mvn spring-boot:run \
        -Dspring-boot.run.arguments="--server.port=${port}" \
        -Dspring-boot.run.jvmArguments="-Xmx512m" \
        -q &
    SERVICE_PID=$!
    cd "$PROJECT_ROOT"
    
    log_info "Service started (PID: $SERVICE_PID)"
    
    # Wait for service to be healthy
    if ! wait_for_health "$port" "$timeout"; then
        log_error "Service failed health check"
        cleanup
        return 1
    fi
    
    # Download OpenAPI spec
    log_info "Downloading OpenAPI specification..."
    
    local openapi_url="http://localhost:${port}/v3/api-docs"
    
    if ! curl -sf "$openapi_url" | jq '.' > "$output_file"; then
        log_error "Failed to download OpenAPI spec from $openapi_url"
        cleanup
        return 1
    fi
    
    # Verify the spec is valid JSON with expected fields
    if ! jq -e '.openapi' "$output_file" > /dev/null 2>&1; then
        log_error "Downloaded spec is not valid OpenAPI format"
        rm -f "$output_file"
        cleanup
        return 1
    fi
    
    local api_version=$(jq -r '.info.version // "unknown"' "$output_file")
    local path_count=$(jq '.paths | length' "$output_file")
    
    log_success "Generated: $output_file"
    log_info "  API Version: $api_version"
    log_info "  Endpoints: $path_count"
    
    # Shutdown service
    cleanup
    
    return 0
}

generate_all_specs() {
    local output_dir="$1"
    local skip_build="$2"
    local timeout="$3"
    local dry_run="$4"
    
    local success_count=0
    local fail_count=0
    
    log_info "Generating OpenAPI specs for all configured services..."
    echo ""
    
    for svc in "${KNOWN_SERVICES[@]}"; do
        IFS=':' read -r path port name <<< "$svc"
        
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        log_info "Processing: $path"
        
        if generate_spec "$path" "$port" "$output_dir" "$name" "$skip_build" "$timeout" "$dry_run"; then
            ((success_count++))
        else
            ((fail_count++))
            log_warn "Skipping failed service: $path"
        fi
        
        echo ""
    done
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_info "Summary: $success_count succeeded, $fail_count failed"
    
    if [[ $fail_count -gt 0 ]]; then
        return 1
    fi
    return 0
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    local service=""
    local port="$DEFAULT_PORT"
    local output_dir="$DEFAULT_OUTPUT_DIR"
    local timeout="$DEFAULT_TIMEOUT"
    local skip_build=false
    local all_services=false
    local dry_run=false
    local spec_name=""
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -s|--service) service="$2"; shift 2 ;;
            -p|--port) port="$2"; shift 2 ;;
            -o|--output) output_dir="$2"; shift 2 ;;
            --timeout) timeout="$2"; shift 2 ;;
            --skip-build) skip_build=true; shift ;;
            --all) all_services=true; shift ;;
            --dry-run) dry_run=true; shift ;;
            -h|--help) show_help; exit 0 ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    print_banner
    check_dependencies
    
    # Generate all or single service
    if [[ "$all_services" == true ]]; then
        generate_all_specs "$output_dir" "$skip_build" "$timeout" "$dry_run"
    elif [[ -n "$service" ]]; then
        # Derive spec name from service path
        spec_name=$(basename "$service" | tr '[:upper:]' '[:lower:]')
        generate_spec "$service" "$port" "$output_dir" "$spec_name" "$skip_build" "$timeout" "$dry_run"
    else
        log_error "Either --service or --all is required"
        show_help
        exit 1
    fi
}

main "$@"
