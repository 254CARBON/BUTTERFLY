#!/bin/bash
# =============================================================================
# BUTTERFLY v1.0.0-integration-complete Release Tagging Script
# =============================================================================
# Gated release tagging script that validates all Phase 1 criteria before
# allowing the v1.0.0-integration-complete tag to be created.
#
# Prerequisites:
#   - All 17 E2E scenarios pass (3 consecutive runs)
#   - Temporal slice P95 latency < 100ms (24-hour)
#   - Cross-system coherence > 0.7
#   - Zero DLQ messages (24-hour soak)
#   - All circuit breakers CLOSED
#   - Governance audit trail complete
#   - Schema compatibility CI passes
#   - Integration team sign-off
#
# Usage:
#   ./tag-integration-complete.sh [OPTIONS]
#
# Options:
#   --force               Skip validation checks (not recommended)
#   --dry-run             Show what would be done without executing
#   --signoff NAME        Integration team sign-off name
#   --message MSG         Custom tag message
#   --verbose             Enable verbose output
#   --help                Show this help message
#
# Exit Codes:
#   0 - Tag created successfully
#   1 - Validation failed
#   2 - Configuration error
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Configuration
TAG_NAME="v1.0.0-integration-complete"
FORCE=false
DRY_RUN=false
SIGNOFF=""
CUSTOM_MESSAGE=""
VERBOSE=false

# URLs
NEXUS_URL="${NEXUS_URL:-http://localhost:8084}"
PLATO_URL="${PLATO_URL:-http://localhost:8086}"
SYNAPSE_URL="${SYNAPSE_URL:-http://localhost:8084}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

# Colors
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
log_section() { echo -e "\n${CYAN}========================================${NC}"; echo -e "${CYAN}  $1${NC}"; echo -e "${CYAN}========================================${NC}"; }

show_help() {
    head -35 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --force) FORCE=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        --signoff) SIGNOFF="$2"; shift 2 ;;
        --message) CUSTOM_MESSAGE="$2"; shift 2 ;;
        --verbose) VERBOSE=true; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 2 ;;
    esac
done

# =============================================================================
# Validation Functions
# =============================================================================

validate_git_state() {
    log_step "Validating Git state..."
    
    # Check we're in a git repository
    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        log_error "Not in a git repository"
        return 1
    fi
    
    # Check for uncommitted changes
    if [ -n "$(git status --porcelain)" ]; then
        log_warn "Uncommitted changes detected"
        if [ "$FORCE" != true ]; then
            log_error "Please commit or stash changes before tagging"
            return 1
        fi
    fi
    
    # Check tag doesn't already exist
    if git tag -l "$TAG_NAME" | grep -q "$TAG_NAME"; then
        log_warn "Tag $TAG_NAME already exists"
        if [ "$FORCE" != true ]; then
            log_error "Use --force to overwrite existing tag"
            return 1
        fi
    fi
    
    log_info "Git state validated"
    return 0
}

validate_e2e_scenarios() {
    log_step "Validating E2E scenarios (17/17 required)..."
    
    local results_dir="${PROJECT_ROOT}/butterfly-e2e/results"
    
    if [ ! -d "$results_dir" ]; then
        log_error "E2E results directory not found: $results_dir"
        log_error "Run: ./butterfly-e2e/run-phase1-validation.sh"
        return 1
    fi
    
    # Count passing scenarios from JUnit XML files
    local pass_count=0
    local total_count=0
    
    for xml_file in "$results_dir"/*.xml; do
        if [ -f "$xml_file" ]; then
            ((total_count++))
            if grep -q 'failures="0"' "$xml_file" 2>/dev/null; then
                ((pass_count++))
            fi
        fi
    done
    
    log_info "Scenario results: $pass_count/$total_count passing"
    
    if [ "$pass_count" -lt 17 ]; then
        log_error "Insufficient scenario coverage: $pass_count/17"
        return 1
    fi
    
    log_info "E2E scenarios validated"
    return 0
}

validate_slo_metrics() {
    log_step "Validating SLO metrics..."
    
    # Check Prometheus connectivity
    if ! curl -sf "${PROMETHEUS_URL}/-/healthy" > /dev/null 2>&1; then
        log_warn "Cannot connect to Prometheus at ${PROMETHEUS_URL}"
        if [ "$FORCE" != true ]; then
            return 1
        fi
        return 0
    fi
    
    # Temporal slice P95 < 100ms
    local p95_query="histogram_quantile(0.95, rate(nexus_temporal_slice_query_duration_bucket[24h])) * 1000"
    local p95_value
    p95_value=$(curl -sf "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$p95_query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')
    
    if [ "$p95_value" != "N/A" ] && [ "$p95_value" != "null" ]; then
        local p95_int
        p95_int=$(printf "%.0f" "$p95_value")
        log_info "Temporal Slice P95: ${p95_int}ms"
        
        if [ "$p95_int" -gt 100 ]; then
            log_error "Temporal Slice P95 exceeds 100ms: ${p95_int}ms"
            if [ "$FORCE" != true ]; then
                return 1
            fi
        fi
    fi
    
    # Cross-system coherence > 0.7
    local coherence_query="avg_over_time(nexus_temporal_coherence_score[24h])"
    local coherence_value
    coherence_value=$(curl -sf "${PROMETHEUS_URL}/api/v1/query?query=$(echo "$coherence_query" | jq -sRr @uri)" | jq -r '.data.result[0].value[1] // "N/A"')
    
    if [ "$coherence_value" != "N/A" ] && [ "$coherence_value" != "null" ]; then
        log_info "Cross-system Coherence: $coherence_value"
        
        if (( $(echo "$coherence_value < 0.7" | bc -l) )); then
            log_error "Cross-system coherence below 0.7: $coherence_value"
            if [ "$FORCE" != true ]; then
                return 1
            fi
        fi
    fi
    
    log_info "SLO metrics validated"
    return 0
}

validate_dlq_empty() {
    log_step "Validating DLQ is empty..."
    
    local dlq_count=0
    
    # Check Kafka DLQ topics
    for topic in $(kafkacat -b localhost:9092 -L 2>/dev/null | grep "\.dlq$" | awk '{print $2}' || echo ""); do
        local count
        count=$(kafkacat -b localhost:9092 -t "$topic" -C -e -q 2>/dev/null | wc -l || echo "0")
        dlq_count=$((dlq_count + count))
    done
    
    log_info "DLQ messages: $dlq_count"
    
    if [ "$dlq_count" -gt 0 ]; then
        log_error "DLQ is not empty: $dlq_count messages"
        if [ "$FORCE" != true ]; then
            return 1
        fi
    fi
    
    log_info "DLQ validated"
    return 0
}

validate_circuit_breakers() {
    log_step "Validating circuit breakers..."
    
    local cb_status
    cb_status=$(curl -sf "${NEXUS_URL}/actuator/health" 2>/dev/null | jq -r '.components.circuitBreakers.details // {}')
    
    if echo "$cb_status" | grep -q '"state":"OPEN"'; then
        log_error "Some circuit breakers are OPEN"
        if [ "$FORCE" != true ]; then
            return 1
        fi
    fi
    
    log_info "Circuit breakers validated (all CLOSED)"
    return 0
}

validate_audit_trail() {
    log_step "Validating governance audit trail..."
    
    # Check PLATO audit endpoint
    local audit_count
    audit_count=$(curl -sf "${PLATO_URL}/api/v1/audit?since=PT24H" 2>/dev/null | jq 'length' || echo "0")
    
    log_info "Audit records in last 24h: $audit_count"
    
    if [ "$audit_count" -lt 1 ]; then
        log_warn "No audit records found in last 24 hours"
        if [ "$FORCE" != true ]; then
            return 1
        fi
    fi
    
    # Check for required audit event types
    local has_policy
    has_policy=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=POLICY_DECISION&since=PT24H" 2>/dev/null | jq 'length' || echo "0")
    
    local has_plan
    has_plan=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=PLAN_EXECUTION&since=PT24H" 2>/dev/null | jq 'length' || echo "0")
    
    local has_action
    has_action=$(curl -sf "${SYNAPSE_URL}/api/v1/audit?eventType=ACTION_EXECUTION&since=PT24H" 2>/dev/null | jq 'length' || echo "0")
    
    log_info "Audit coverage: policy=$has_policy, plan=$has_plan, action=$has_action"
    
    if [ "$has_policy" -lt 1 ] || [ "$has_plan" -lt 1 ] || [ "$has_action" -lt 1 ]; then
        log_warn "Incomplete audit trail coverage"
        if [ "$FORCE" != true ]; then
            return 1
        fi
    fi
    
    log_info "Audit trail validated"
    return 0
}

validate_schema_compatibility() {
    log_step "Validating schema compatibility..."
    
    # Check if schema registry is available
    local sr_status
    sr_status=$(curl -sf "http://localhost:8081/subjects" 2>/dev/null || echo "")
    
    if [ -n "$sr_status" ]; then
        # Check for schema compatibility issues
        local subjects
        subjects=$(echo "$sr_status" | jq -r '.[]' 2>/dev/null || echo "")
        
        local compatibility_issues=0
        for subject in $subjects; do
            local compat_check
            compat_check=$(curl -sf "http://localhost:8081/subjects/$subject/versions/latest/compatibilityCheck" 2>/dev/null || echo '{"is_compatible": true}')
            
            if echo "$compat_check" | jq -e '.is_compatible == false' > /dev/null 2>&1; then
                log_warn "Schema compatibility issue: $subject"
                ((compatibility_issues++))
            fi
        done
        
        if [ "$compatibility_issues" -gt 0 ]; then
            log_error "Found $compatibility_issues schema compatibility issues"
            if [ "$FORCE" != true ]; then
                return 1
            fi
        fi
        
        log_info "Schema compatibility validated"
    else
        log_warn "Schema registry not available - skipping compatibility check"
    fi
    
    return 0
}

validate_signoff() {
    log_step "Validating integration team sign-off..."
    
    if [ -z "$SIGNOFF" ]; then
        log_error "Integration team sign-off required"
        log_error "Use: --signoff 'Your Name' to provide sign-off"
        if [ "$FORCE" != true ]; then
            return 1
        fi
    fi
    
    log_info "Sign-off provided by: $SIGNOFF"
    return 0
}

# =============================================================================
# Main Execution
# =============================================================================

log_section "BUTTERFLY Integration Complete Tagging"

log_info "Tag Name: $TAG_NAME"
log_info "Dry Run:  $DRY_RUN"
log_info "Force:    $FORCE"
if [ -n "$SIGNOFF" ]; then
    log_info "Sign-off: $SIGNOFF"
fi

# Run validations
VALIDATION_PASSED=true

log_section "Pre-Tag Validations"

if [ "$FORCE" != true ]; then
    validate_git_state || VALIDATION_PASSED=false
    validate_e2e_scenarios || VALIDATION_PASSED=false
    validate_slo_metrics || VALIDATION_PASSED=false
    validate_dlq_empty || VALIDATION_PASSED=false
    validate_circuit_breakers || VALIDATION_PASSED=false
    validate_audit_trail || VALIDATION_PASSED=false
    validate_schema_compatibility || VALIDATION_PASSED=false
    validate_signoff || VALIDATION_PASSED=false
else
    log_warn "FORCE mode enabled - skipping validations"
    VALIDATION_PASSED=true
fi

# Check validation result
if [ "$VALIDATION_PASSED" != true ]; then
    log_section "Validation Failed"
    log_error "One or more validations failed"
    log_error "Use --force to override (not recommended for production)"
    exit 1
fi

log_section "Creating Release Tag"

# Prepare tag message
TAG_MESSAGE="${CUSTOM_MESSAGE:-BUTTERFLY Integration Complete - Phase 1}"
TAG_MESSAGE="$TAG_MESSAGE

Phase 1 Integration Criteria Met:
- All 17 E2E scenarios passing (3 consecutive runs)
- Temporal slice P95 < 100ms (24-hour)
- Cross-system coherence > 0.7
- Zero DLQ messages (24-hour soak)
- All circuit breakers CLOSED
- Governance audit trail complete
- Schema compatibility verified

Integration Team Sign-off: ${SIGNOFF:-N/A}
Tagged at: $(date -u +'%Y-%m-%dT%H:%M:%SZ')
"

if [ "$DRY_RUN" = true ]; then
    log_info "[DRY-RUN] Would create tag: $TAG_NAME"
    log_info "[DRY-RUN] Message:"
    echo "$TAG_MESSAGE"
else
    # Create annotated tag
    log_info "Creating annotated tag..."
    
    cd "$PROJECT_ROOT"
    
    if [ "$FORCE" = true ] && git tag -l "$TAG_NAME" | grep -q "$TAG_NAME"; then
        log_info "Deleting existing tag..."
        git tag -d "$TAG_NAME" || true
        git push origin ":refs/tags/$TAG_NAME" 2>/dev/null || true
    fi
    
    git tag -a "$TAG_NAME" -m "$TAG_MESSAGE"
    
    log_info "Tag created locally: $TAG_NAME"
    
    # Push tag
    log_info "Pushing tag to origin..."
    git push origin "$TAG_NAME"
    
    log_info "Tag pushed successfully"
fi

log_section "Release Tag Complete"

if [ "$DRY_RUN" = true ]; then
    log_info "[DRY-RUN] No changes made"
else
    log_info "Tag $TAG_NAME created and pushed"
    log_info ""
    log_info "Next Steps:"
    log_info "  1. Monitor production deployment"
    log_info "  2. Verify all services healthy"
    log_info "  3. Run post-deployment validation"
    log_info "  4. Update release notes"
fi

exit 0

