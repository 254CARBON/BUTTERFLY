#!/usr/bin/env bash
#
# BUTTERFLY Development Environment Setup Script
#
# This script sets up your local development environment for the BUTTERFLY ecosystem.
# It checks prerequisites, installs dependencies, and configures git hooks.
#
# Usage:
#   ./scripts/setup.sh           # Full setup
#   ./scripts/setup.sh --check   # Only check prerequisites
#   ./scripts/setup.sh --start   # Setup + start dev infrastructure
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
    echo "  Pre-commit hooks are now active. Commits will be validated for:"
    echo "    - Conventional commit message format"
    echo "    - TypeScript/JavaScript linting"
    echo ""
    echo "See DEVELOPMENT_OVERVIEW.md for more details."
}

#------------------------------------------------------------------------------
# Main
#------------------------------------------------------------------------------

main() {
    cd "$PROJECT_ROOT"
    
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║           BUTTERFLY Development Environment Setup            ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    
    local check_only=false
    local start_stack=false
    
    for arg in "$@"; do
        case $arg in
            --check)
                check_only=true
                ;;
            --start)
                start_stack=true
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --check    Only check prerequisites, don't install anything"
                echo "  --start    Also start the development infrastructure after setup"
                echo "  --help     Show this help message"
                exit 0
                ;;
        esac
    done
    
    check_prerequisites
    
    if [ "$check_only" = true ]; then
        exit 0
    fi
    
    install_butterfly_common
    install_node_dependencies
    install_portal_dependencies
    
    if [ "$start_stack" = true ]; then
        start_dev_stack
    fi
    
    print_next_steps
}

main "$@"

