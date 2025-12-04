#!/usr/bin/env bash
#
# BUTTERFLY Development Environment Setup Script
#
# This script sets up your local development environment for the BUTTERFLY ecosystem.
# It checks prerequisites, installs dependencies, and configures git hooks.
#
# Usage:
#   ./scripts/setup.sh                    # Full setup
#   ./scripts/setup.sh --check            # Only check prerequisites
#   ./scripts/setup.sh --start            # Setup + start dev infrastructure
#   ./scripts/setup.sh --module PERCEPTION  # Start specific module
#   ./scripts/setup.sh --full             # Start all modules
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Minimum versions
MIN_JAVA_VERSION=17
MIN_MAVEN_VERSION="3.9"
MIN_NODE_VERSION=20
MIN_DOCKER_VERSION=20

# Module configuration - maps module names to their compose files and health endpoints
declare -A MODULE_COMPOSE_FILES=(
    ["PERCEPTION"]="PERCEPTION/docker-compose.dev.yml"
    ["CAPSULE"]="CAPSULE/docker-compose.yml"
    ["ODYSSEY"]="ODYSSEY/docker-compose.yml"
    ["PLATO"]="PLATO/docker-compose.yml"
    ["NEXUS"]="butterfly-nexus/docker-compose.dev.yml"
)

declare -A MODULE_HEALTH_ENDPOINTS=(
    ["PERCEPTION"]="http://localhost:8080/actuator/health"
    ["CAPSULE"]="http://localhost:8081/actuator/health"
    ["ODYSSEY"]="http://localhost:8082/actuator/health"
    ["PLATO"]="http://localhost:8083/actuator/health"
    ["NEXUS"]="http://localhost:8084/actuator/health"
)

declare -A MODULE_PORTS=(
    ["PERCEPTION"]="8080"
    ["CAPSULE"]="8081"
    ["ODYSSEY"]="8082"
    ["PLATO"]="8083"
    ["NEXUS"]="8084"
)

# All available modules
ALL_MODULES=("PERCEPTION" "CAPSULE" "ODYSSEY" "PLATO" "NEXUS")

#------------------------------------------------------------------------------
# Helper functions
#------------------------------------------------------------------------------

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    echo ""
    echo -e "${BLUE}=== $1 ===${NC}"
    echo ""
}

version_gte() {
    # Returns 0 if $1 >= $2
    printf '%s\n%s\n' "$2" "$1" | sort -V -C
}

#------------------------------------------------------------------------------
# Prerequisite checks
#------------------------------------------------------------------------------

check_java() {
    log_info "Checking Java..."
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed. Please install JDK $MIN_JAVA_VERSION or higher."
        return 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt "$MIN_JAVA_VERSION" ]; then
        log_error "Java version $JAVA_VERSION found, but $MIN_JAVA_VERSION or higher is required."
        return 1
    fi
    
    log_success "Java $JAVA_VERSION"
    return 0
}

check_maven() {
    log_info "Checking Maven..."
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed. Please install Maven $MIN_MAVEN_VERSION or higher."
        return 1
    fi
    
    MAVEN_VERSION=$(mvn -version 2>&1 | head -n 1 | grep -oP '[\d.]+' | head -1)
    if ! version_gte "$MAVEN_VERSION" "$MIN_MAVEN_VERSION"; then
        log_error "Maven version $MAVEN_VERSION found, but $MIN_MAVEN_VERSION or higher is required."
        return 1
    fi
    
    log_success "Maven $MAVEN_VERSION"
    return 0
}

check_node() {
    log_info "Checking Node.js..."
    if ! command -v node &> /dev/null; then
        log_error "Node.js is not installed. Please install Node.js $MIN_NODE_VERSION or higher."
        return 1
    fi
    
    NODE_VERSION=$(node -v | sed 's/v//' | cut -d'.' -f1)
    if [ "$NODE_VERSION" -lt "$MIN_NODE_VERSION" ]; then
        log_error "Node.js version $NODE_VERSION found, but $MIN_NODE_VERSION or higher is required."
        return 1
    fi
    
    log_success "Node.js v$(node -v | sed 's/v//')"
    return 0
}

check_npm() {
    log_info "Checking npm..."
    if ! command -v npm &> /dev/null; then
        log_error "npm is not installed. It should come with Node.js."
        return 1
    fi
    
    log_success "npm $(npm -v)"
    return 0
}

check_docker() {
    log_info "Checking Docker..."
    if ! command -v docker &> /dev/null; then
        log_warn "Docker is not installed. Some features (e2e tests, dev stack) will be unavailable."
        return 0
    fi
    
    if ! docker info &> /dev/null; then
        log_warn "Docker daemon is not running. Please start Docker to use dev stack."
        return 0
    fi
    
    DOCKER_VERSION=$(docker version --format '{{.Server.Version}}' 2>/dev/null | cut -d'.' -f1)
    log_success "Docker $DOCKER_VERSION"
    return 0
}

check_docker_compose() {
    log_info "Checking Docker Compose..."
    if docker compose version &> /dev/null; then
        COMPOSE_VERSION=$(docker compose version --short 2>/dev/null)
        log_success "Docker Compose $COMPOSE_VERSION"
    elif command -v docker-compose &> /dev/null; then
        COMPOSE_VERSION=$(docker-compose version --short 2>/dev/null)
        log_success "Docker Compose (standalone) $COMPOSE_VERSION"
    else
        log_warn "Docker Compose is not installed. Dev stack features will be unavailable."
    fi
    return 0
}

check_prerequisites() {
    log_section "Checking Prerequisites"
    
    local failed=0
    
    check_java || failed=1
    check_maven || failed=1
    check_node || failed=1
    check_npm || failed=1
    check_docker
    check_docker_compose
    
    if [ $failed -eq 1 ]; then
        log_error "Some prerequisites are missing. Please install them and try again."
        exit 1
    fi
    
    log_success "All required prerequisites are installed!"
}

#------------------------------------------------------------------------------
# Setup steps
#------------------------------------------------------------------------------

install_butterfly_common() {
    log_section "Installing butterfly-common"
    
    log_info "Building and installing butterfly-common to local Maven repository..."
    cd "$PROJECT_ROOT"
    mvn -f butterfly-common/pom.xml install -DskipTests -B -q
    log_success "butterfly-common installed successfully"
}

install_node_dependencies() {
    log_section "Installing Node.js Dependencies"
    
    cd "$PROJECT_ROOT"
    
    log_info "Installing root package dependencies (Husky, lint-staged, commitlint)..."
    npm install
    
    log_info "Setting up Husky git hooks..."
    npx husky || true
    
    log_success "Node.js dependencies and git hooks installed"
}

install_portal_dependencies() {
    log_section "Installing Portal Dependencies"
    
    if [ -d "$PROJECT_ROOT/PERCEPTION/perception-portal" ]; then
        log_info "Installing PERCEPTION portal dependencies..."
        cd "$PROJECT_ROOT/PERCEPTION/perception-portal"
        npm install
        log_success "Portal dependencies installed"
    else
        log_warn "PERCEPTION portal not found, skipping..."
    fi
}

start_dev_stack() {
    log_section "Starting Development Infrastructure"
    
    log_info "Starting Kafka and supporting services..."
    cd "$PROJECT_ROOT"
    FASTPATH_SKIP_STUB=1 ./scripts/dev-up.sh
    
    log_success "Development infrastructure started"
    log_info "Use './scripts/dev-down.sh' to stop the stack"
}

#------------------------------------------------------------------------------
# Module-specific bootstrap functions
#------------------------------------------------------------------------------

setup_env_file() {
    local module=$1
    local module_dir=""
    
    case $module in
        PERCEPTION|CAPSULE|ODYSSEY|PLATO)
            module_dir="$PROJECT_ROOT/$module"
            ;;
        NEXUS)
            module_dir="$PROJECT_ROOT/butterfly-nexus"
            ;;
    esac
    
    # Check for .env.example and create .env if it doesn't exist
    if [ -f "$module_dir/.env.example" ] && [ ! -f "$module_dir/.env" ]; then
        log_info "Creating .env from .env.example for $module..."
        cp "$module_dir/.env.example" "$module_dir/.env"
        log_success "Created $module/.env - review and update as needed"
    fi
}

wait_for_health() {
    local endpoint=$1
    local service_name=$2
    local max_attempts=${3:-30}
    local attempt=1
    
    log_info "Waiting for $service_name to be healthy..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$endpoint" > /dev/null 2>&1; then
            log_success "$service_name is healthy"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo ""
    log_warn "$service_name health check timed out after $((max_attempts * 2)) seconds"
    return 1
}

start_module() {
    local module=$1
    local compose_file="${MODULE_COMPOSE_FILES[$module]}"
    local health_endpoint="${MODULE_HEALTH_ENDPOINTS[$module]}"
    local port="${MODULE_PORTS[$module]}"
    
    log_section "Starting $module"
    
    # Check if compose file exists
    if [ ! -f "$PROJECT_ROOT/$compose_file" ]; then
        log_error "Compose file not found: $compose_file"
        log_info "Available modules: ${ALL_MODULES[*]}"
        return 1
    fi
    
    # Setup environment file if needed
    setup_env_file "$module"
    
    # Check if port is already in use
    if command -v lsof &> /dev/null && lsof -i ":$port" > /dev/null 2>&1; then
        log_warn "Port $port is already in use. $module may already be running."
        return 0
    fi
    
    # Start the module
    log_info "Starting $module using $compose_file..."
    cd "$PROJECT_ROOT"
    
    if docker compose -f "$compose_file" up -d; then
        log_success "$module containers started"
    else
        log_error "Failed to start $module"
        return 1
    fi
    
    # Wait for health check (optional, don't fail if it times out)
    wait_for_health "$health_endpoint" "$module" 30 || true
    
    return 0
}

stop_module() {
    local module=$1
    local compose_file="${MODULE_COMPOSE_FILES[$module]}"
    
    if [ ! -f "$PROJECT_ROOT/$compose_file" ]; then
        log_warn "Compose file not found: $compose_file"
        return 0
    fi
    
    log_info "Stopping $module..."
    cd "$PROJECT_ROOT"
    docker compose -f "$compose_file" down || true
    log_success "$module stopped"
}

start_all_modules() {
    log_section "Starting All Modules"
    
    # First start the shared infrastructure (Kafka)
    log_info "Starting shared infrastructure (Kafka)..."
    cd "$PROJECT_ROOT"
    FASTPATH_SKIP_STUB=1 ./scripts/dev-up.sh
    
    # Wait for Kafka to be ready
    sleep 5
    
    # Start each module
    local failed=0
    for module in "${ALL_MODULES[@]}"; do
        if ! start_module "$module"; then
            log_warn "Failed to start $module, continuing with other modules..."
            failed=1
        fi
    done
    
    if [ $failed -eq 1 ]; then
        log_warn "Some modules failed to start. Check logs above for details."
    else
        log_success "All modules started successfully!"
    fi
    
    print_module_status
}

print_module_status() {
    log_section "Module Status"
    
    echo ""
    printf "%-12s %-8s %-40s\n" "MODULE" "PORT" "STATUS"
    printf "%-12s %-8s %-40s\n" "------" "----" "------"
    
    for module in "${ALL_MODULES[@]}"; do
        local port="${MODULE_PORTS[$module]}"
        local health_endpoint="${MODULE_HEALTH_ENDPOINTS[$module]}"
        local status="unknown"
        
        if curl -sf "$health_endpoint" > /dev/null 2>&1; then
            status="${GREEN}healthy${NC}"
        elif command -v lsof &> /dev/null && lsof -i ":$port" > /dev/null 2>&1; then
            status="${YELLOW}starting${NC}"
        else
            status="${RED}stopped${NC}"
        fi
        
        printf "%-12s %-8s " "$module" "$port"
        echo -e "$status"
    done
    echo ""
}

list_modules() {
    log_section "Available Modules"
    
    echo ""
    printf "%-12s %-8s %-40s\n" "MODULE" "PORT" "COMPOSE FILE"
    printf "%-12s %-8s %-40s\n" "------" "----" "------------"
    
    for module in "${ALL_MODULES[@]}"; do
        local port="${MODULE_PORTS[$module]}"
        local compose_file="${MODULE_COMPOSE_FILES[$module]}"
        local exists=""
        
        if [ -f "$PROJECT_ROOT/$compose_file" ]; then
            exists="✓"
        else
            exists="✗ (missing)"
            compose_file="$compose_file $exists"
        fi
        
        printf "%-12s %-8s %-40s\n" "$module" "$port" "$compose_file"
    done
    echo ""
}

print_next_steps() {
    log_section "Setup Complete!"
    
    echo "Your development environment is ready. Here are some useful commands:"
    echo ""
    echo "  Building modules:"
    echo "    mvn -f PERCEPTION/pom.xml clean install    # Build PERCEPTION"
    echo "    mvn -f PLATO/pom.xml clean install         # Build PLATO"
    echo "    mvn -f CAPSULE/pom.xml clean install       # Build CAPSULE"
    echo "    mvn -f ODYSSEY/pom.xml clean install       # Build ODYSSEY"
    echo ""
    echo "  Running tests:"
    echo "    mvn -f PERCEPTION/pom.xml test             # Backend unit tests"
    echo "    cd PERCEPTION/perception-portal && npm test # Portal unit tests"
    echo ""
    echo "  Development stack:"
    echo "    ./scripts/dev-up.sh                        # Start Kafka + services"
    echo "    ./scripts/dev-down.sh                      # Stop services"
    echo ""
    echo "  Module management:"
    echo "    ./scripts/setup.sh --list                  # List available modules"
    echo "    ./scripts/setup.sh --status                # Check module health"
    echo "    ./scripts/setup.sh --module PERCEPTION     # Start specific module"
    echo "    ./scripts/setup.sh --full                  # Start all modules"
    echo "    ./scripts/setup.sh --stop PERCEPTION       # Stop specific module"
    echo "    ./scripts/setup.sh --stop-all              # Stop all modules"
    echo ""
    echo "  Pre-commit hooks are now active. Commits will be validated for:"
    echo "    - Conventional commit message format"
    echo "    - TypeScript/JavaScript linting"
    echo ""
    echo "See DEVELOPMENT_OVERVIEW.md for more details."
}

#------------------------------------------------------------------------------
# Main
#------------------------------------------------------------------------------

print_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Setup Options:"
    echo "  --check              Only check prerequisites, don't install anything"
    echo "  --start              Also start the development infrastructure after setup"
    echo ""
    echo "Module Options:"
    echo "  --module MODULE      Start a specific module (PERCEPTION, CAPSULE, ODYSSEY, PLATO, NEXUS)"
    echo "  --full               Start all modules (includes shared infrastructure)"
    echo "  --stop MODULE        Stop a specific module"
    echo "  --stop-all           Stop all modules"
    echo "  --status             Show status of all modules"
    echo "  --list               List available modules"
    echo ""
    echo "Examples:"
    echo "  $0                           # Full setup"
    echo "  $0 --check                   # Only check prerequisites"
    echo "  $0 --start                   # Setup + start shared infrastructure"
    echo "  $0 --module PERCEPTION       # Start PERCEPTION module"
    echo "  $0 --full                    # Start all modules"
    echo "  $0 --status                  # Check module health status"
    echo ""
    echo "See DEVELOPMENT_OVERVIEW.md for more details."
}

main() {
    cd "$PROJECT_ROOT"
    
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║           BUTTERFLY Development Environment Setup            ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    
    local check_only=false
    local start_stack=false
    local start_full=false
    local target_module=""
    local stop_module_name=""
    local stop_all=false
    local show_status=false
    local show_list=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --check)
                check_only=true
                shift
                ;;
            --start)
                start_stack=true
                shift
                ;;
            --module)
                target_module="${2:-}"
                if [ -z "$target_module" ]; then
                    log_error "Missing module name. Use --list to see available modules."
                    exit 1
                fi
                shift 2
                ;;
            --full)
                start_full=true
                shift
                ;;
            --stop)
                stop_module_name="${2:-}"
                if [ -z "$stop_module_name" ]; then
                    log_error "Missing module name for --stop"
                    exit 1
                fi
                shift 2
                ;;
            --stop-all)
                stop_all=true
                shift
                ;;
            --status)
                show_status=true
                shift
                ;;
            --list)
                show_list=true
                shift
                ;;
            --help|-h)
                print_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                print_help
                exit 1
                ;;
        esac
    done
    
    # Handle quick actions that don't require full setup
    if [ "$show_list" = true ]; then
        list_modules
        exit 0
    fi
    
    if [ "$show_status" = true ]; then
        print_module_status
        exit 0
    fi
    
    if [ "$stop_all" = true ]; then
        for module in "${ALL_MODULES[@]}"; do
            stop_module "$module"
        done
        log_success "All modules stopped"
        exit 0
    fi
    
    if [ -n "$stop_module_name" ]; then
        stop_module "$stop_module_name"
        exit 0
    fi
    
    # Check prerequisites for all operations
    check_prerequisites
    
    if [ "$check_only" = true ]; then
        exit 0
    fi
    
    # Module-specific start (skip full setup)
    if [ -n "$target_module" ]; then
        # Validate module name
        local valid_module=false
        for m in "${ALL_MODULES[@]}"; do
            if [ "$m" = "$target_module" ]; then
                valid_module=true
                break
            fi
        done
        
        if [ "$valid_module" = false ]; then
            log_error "Unknown module: $target_module"
            list_modules
            exit 1
        fi
        
        start_module "$target_module"
        exit 0
    fi
    
    # Full stack start (skip setup)
    if [ "$start_full" = true ]; then
        start_all_modules
        exit 0
    fi
    
    # Default: full setup
    install_butterfly_common
    install_node_dependencies
    install_portal_dependencies
    
    if [ "$start_stack" = true ]; then
        start_dev_stack
    fi
    
    print_next_steps
}

main "$@"

