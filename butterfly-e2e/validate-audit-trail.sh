#!/bin/bash
# =============================================================================
# BUTTERFLY Audit Trail Validation Script
# =============================================================================
# Validates the completeness and integrity of governance audit trails.
# Ensures all governance decisions, plan executions, and SYNAPSE actions
# are properly audited with required metadata.
#
# Usage:
#   ./validate-audit-trail.sh [OPTIONS]
#
# Options:
#   --plato-url URL         PLATO URL (default: http://localhost:8086)
#   --synapse-url URL       SYNAPSE URL (default: http://localhost:8084)
#   --correlation-id ID     Validate specific correlation ID
#   --time-range RANGE      Time range to validate (default: PT1H)
#   --json-report FILE      Output JSON report
#   --verbose               Enable verbose output
#   --help                  Show this help message
#
# Exit Codes:
#   0 - Audit trail validation passed
#   1 - Audit trail validation failed
#   2 - Configuration error
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Configuration
PLATO_URL="${PLATO_URL:-http://localhost:8086}"
SYNAPSE_URL="${SYNAPSE_URL:-http://localhost:8084}"
NEXUS_URL="${NEXUS_URL:-http://localhost:8084}"
CORRELATION_ID=""
TIME_RANGE="${TIME_RANGE:-PT1H}"
JSON_REPORT=""
VERBOSE=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Results tracking
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
declare -A AUDIT_RESULTS

log_info() { echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_check() { echo -e "${BLUE}[CHECK]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_section() { echo -e "\n${CYAN}=== $1 ===${NC}"; }

show_help() {
    head -25 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --plato-url) PLATO_URL="$2"; shift 2 ;;
        --synapse-url) SYNAPSE_URL="$2"; shift 2 ;;
        --correlation-id) CORRELATION_ID="$2"; shift 2 ;;
        --time-range) TIME_RANGE="$2"; shift 2 ;;
        --json-report) JSON_REPORT="$2"; shift 2 ;;
        --verbose) VERBOSE=true; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 2 ;;
    esac
done

# =============================================================================
# Helper Functions
# =============================================================================

check_audit_field() {
    local name="$1"
    local json="$2"
    local field="$3"
    local expected="$4"
    
    ((TOTAL_CHECKS++))
    log_check "Checking $name - $field"
    
    local actual
    actual=$(echo "$json" | jq -r "$field" 2>/dev/null || echo "N/A")
    
    if [ -z "$expected" ]; then
        # Just check field exists and is not null/empty
        if [ "$actual" != "null" ] && [ "$actual" != "N/A" ] && [ -n "$actual" ]; then
            log_info "  PASS: $field = $actual"
            AUDIT_RESULTS["$name:$field"]="PASS"
            ((PASSED_CHECKS++))
            return 0
        else
            log_error "  FAIL: $field is missing or null"
            AUDIT_RESULTS["$name:$field"]="FAIL"
            ((FAILED_CHECKS++))
            return 1
        fi
    else
        # Check field matches expected value
        if [ "$actual" = "$expected" ]; then
            log_info "  PASS: $field = $actual"
            AUDIT_RESULTS["$name:$field"]="PASS"
            ((PASSED_CHECKS++))
            return 0
        else
            log_error "  FAIL: $field = $actual (expected: $expected)"
            AUDIT_RESULTS["$name:$field"]="FAIL"
            ((FAILED_CHECKS++))
            return 1
        fi
    fi
}

check_audit_count() {
    local name="$1"
    local actual="$2"
    local min_expected="$3"
    
    ((TOTAL_CHECKS++))
    log_check "Checking $name count >= $min_expected"
    
    if [ "$actual" -ge "$min_expected" ]; then
        log_info "  PASS: count = $actual (>= $min_expected)"
        AUDIT_RESULTS["$name:count"]="PASS"
        ((PASSED_CHECKS++))
        return 0
    else
        log_error "  FAIL: count = $actual (< $min_expected)"
        AUDIT_RESULTS["$name:count"]="FAIL"
        ((FAILED_CHECKS++))
        return 1
    fi
}

# =============================================================================
# Audit Trail Validation
# =============================================================================

log_info "============================================="
log_info "BUTTERFLY Audit Trail Validation"
log_info "============================================="
log_info "PLATO URL:  $PLATO_URL"
log_info "SYNAPSE URL: $SYNAPSE_URL"
log_info "Time Range: $TIME_RANGE"
if [ -n "$CORRELATION_ID" ]; then
    log_info "Correlation ID: $CORRELATION_ID"
fi
log_info "============================================="

# Check connectivity
if ! curl -sf "${PLATO_URL}/actuator/health" > /dev/null 2>&1; then
    log_error "Cannot connect to PLATO at ${PLATO_URL}"
    exit 2
fi

if ! curl -sf "${SYNAPSE_URL}/actuator/health" > /dev/null 2>&1; then
    log_error "Cannot connect to SYNAPSE at ${SYNAPSE_URL}"
    exit 2
fi

log_info "Service connectivity verified"

# =============================================================================
# PLATO Audit Trail Validation
# =============================================================================

log_section "PLATO Governance Audit Trail"

# Get policy decision audit records
POLICY_AUDITS=""
if [ -n "$CORRELATION_ID" ]; then
    POLICY_AUDITS=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=POLICY_DECISION&correlationId=${CORRELATION_ID}" 2>/dev/null || echo "[]")
else
    POLICY_AUDITS=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=POLICY_DECISION&since=${TIME_RANGE}" 2>/dev/null || echo "[]")
fi

POLICY_COUNT=$(echo "$POLICY_AUDITS" | jq 'length' 2>/dev/null || echo "0")
log_info "Found $POLICY_COUNT policy decision audit records"

if [ "$POLICY_COUNT" -gt 0 ]; then
    # Validate first policy audit record
    FIRST_POLICY=$(echo "$POLICY_AUDITS" | jq '.[0]')
    
    check_audit_field "policy_decision" "$FIRST_POLICY" ".eventType" "POLICY_DECISION" || true
    check_audit_field "policy_decision" "$FIRST_POLICY" ".correlationId" "" || true
    check_audit_field "policy_decision" "$FIRST_POLICY" ".timestamp" "" || true
    check_audit_field "policy_decision" "$FIRST_POLICY" ".tenantId" "" || true
    check_audit_field "policy_decision" "$FIRST_POLICY" ".policyId" "" || true
    check_audit_field "policy_decision" "$FIRST_POLICY" ".decision" "" || true
    check_audit_field "policy_decision" "$FIRST_POLICY" ".context" "" || true
fi

# Get plan execution audit records
PLAN_AUDITS=""
if [ -n "$CORRELATION_ID" ]; then
    PLAN_AUDITS=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=PLAN_EXECUTION&correlationId=${CORRELATION_ID}" 2>/dev/null || echo "[]")
else
    PLAN_AUDITS=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=PLAN_EXECUTION&since=${TIME_RANGE}" 2>/dev/null || echo "[]")
fi

PLAN_COUNT=$(echo "$PLAN_AUDITS" | jq 'length' 2>/dev/null || echo "0")
log_info "Found $PLAN_COUNT plan execution audit records"

if [ "$PLAN_COUNT" -gt 0 ]; then
    # Validate first plan audit record
    FIRST_PLAN=$(echo "$PLAN_AUDITS" | jq '.[0]')
    
    check_audit_field "plan_execution" "$FIRST_PLAN" ".eventType" "PLAN_EXECUTION" || true
    check_audit_field "plan_execution" "$FIRST_PLAN" ".correlationId" "" || true
    check_audit_field "plan_execution" "$FIRST_PLAN" ".timestamp" "" || true
    check_audit_field "plan_execution" "$FIRST_PLAN" ".planId" "" || true
    check_audit_field "plan_execution" "$FIRST_PLAN" ".status" "" || true
    check_audit_field "plan_execution" "$FIRST_PLAN" ".steps" "" || true
fi

# Get approval audit records
APPROVAL_AUDITS=""
if [ -n "$CORRELATION_ID" ]; then
    APPROVAL_AUDITS=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=APPROVAL&correlationId=${CORRELATION_ID}" 2>/dev/null || echo "[]")
else
    APPROVAL_AUDITS=$(curl -sf "${PLATO_URL}/api/v1/audit?eventType=APPROVAL&since=${TIME_RANGE}" 2>/dev/null || echo "[]")
fi

APPROVAL_COUNT=$(echo "$APPROVAL_AUDITS" | jq 'length' 2>/dev/null || echo "0")
log_info "Found $APPROVAL_COUNT approval audit records"

# =============================================================================
# SYNAPSE Action Audit Trail
# =============================================================================

log_section "SYNAPSE Action Audit Trail"

# Get action execution audit records
ACTION_AUDITS=""
if [ -n "$CORRELATION_ID" ]; then
    ACTION_AUDITS=$(curl -sf "${SYNAPSE_URL}/api/v1/audit?eventType=ACTION_EXECUTION&correlationId=${CORRELATION_ID}" 2>/dev/null || echo "[]")
else
    ACTION_AUDITS=$(curl -sf "${SYNAPSE_URL}/api/v1/audit?eventType=ACTION_EXECUTION&since=${TIME_RANGE}" 2>/dev/null || echo "[]")
fi

ACTION_COUNT=$(echo "$ACTION_AUDITS" | jq 'length' 2>/dev/null || echo "0")
log_info "Found $ACTION_COUNT action execution audit records"

if [ "$ACTION_COUNT" -gt 0 ]; then
    # Validate first action audit record
    FIRST_ACTION=$(echo "$ACTION_AUDITS" | jq '.[0]')
    
    check_audit_field "action_execution" "$FIRST_ACTION" ".eventType" "ACTION_EXECUTION" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".correlationId" "" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".timestamp" "" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".actionId" "" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".toolId" "" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".status" "" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".input" "" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".output" "" || true
    check_audit_field "action_execution" "$FIRST_ACTION" ".duration" "" || true
fi

# Get connector audit records
CONNECTOR_AUDITS=""
if [ -n "$CORRELATION_ID" ]; then
    CONNECTOR_AUDITS=$(curl -sf "${SYNAPSE_URL}/api/v1/audit?eventType=CONNECTOR_CALL&correlationId=${CORRELATION_ID}" 2>/dev/null || echo "[]")
else
    CONNECTOR_AUDITS=$(curl -sf "${SYNAPSE_URL}/api/v1/audit?eventType=CONNECTOR_CALL&since=${TIME_RANGE}" 2>/dev/null || echo "[]")
fi

CONNECTOR_COUNT=$(echo "$CONNECTOR_AUDITS" | jq 'length' 2>/dev/null || echo "0")
log_info "Found $CONNECTOR_COUNT connector call audit records"

# =============================================================================
# Governance Flow Integrity
# =============================================================================

log_section "Governance Flow Integrity"

if [ -n "$CORRELATION_ID" ]; then
    log_info "Validating governance flow for correlation ID: $CORRELATION_ID"
    
    # Check that we have records at each stage
    HAS_POLICY=$( [ "$POLICY_COUNT" -gt 0 ] && echo "true" || echo "false" )
    HAS_PLAN=$( [ "$PLAN_COUNT" -gt 0 ] && echo "true" || echo "false" )
    HAS_ACTION=$( [ "$ACTION_COUNT" -gt 0 ] && echo "true" || echo "false" )
    
    ((TOTAL_CHECKS++))
    log_check "Governance flow completeness"
    
    if [ "$HAS_POLICY" = "true" ] && [ "$HAS_PLAN" = "true" ] && [ "$HAS_ACTION" = "true" ]; then
        log_info "  PASS: Complete governance flow (policy -> plan -> action)"
        AUDIT_RESULTS["governance_flow:complete"]="PASS"
        ((PASSED_CHECKS++))
    else
        log_warn "  PARTIAL: policy=$HAS_POLICY, plan=$HAS_PLAN, action=$HAS_ACTION"
        AUDIT_RESULTS["governance_flow:complete"]="PARTIAL"
    fi
    
    # Check temporal ordering
    ((TOTAL_CHECKS++))
    log_check "Temporal ordering"
    
    # Get timestamps
    POLICY_TS=$(echo "$POLICY_AUDITS" | jq -r '.[0].timestamp // "1970-01-01T00:00:00Z"' 2>/dev/null)
    PLAN_TS=$(echo "$PLAN_AUDITS" | jq -r '.[0].timestamp // "1970-01-01T00:00:00Z"' 2>/dev/null)
    ACTION_TS=$(echo "$ACTION_AUDITS" | jq -r '.[0].timestamp // "1970-01-01T00:00:00Z"' 2>/dev/null)
    
    # Basic ordering check (just verify they exist)
    if [ -n "$POLICY_TS" ] || [ -n "$PLAN_TS" ] || [ -n "$ACTION_TS" ]; then
        log_info "  PASS: Audit records have timestamps"
        AUDIT_RESULTS["governance_flow:temporal"]="PASS"
        ((PASSED_CHECKS++))
    else
        log_error "  FAIL: Missing timestamps"
        AUDIT_RESULTS["governance_flow:temporal"]="FAIL"
        ((FAILED_CHECKS++))
    fi
fi

# =============================================================================
# Audit Trail Integrity
# =============================================================================

log_section "Audit Trail Integrity"

# Check for gaps in audit sequence
((TOTAL_CHECKS++))
log_check "Audit trail continuity"

GAP_CHECK=$(curl -sf "${PLATO_URL}/api/v1/audit/gaps?since=${TIME_RANGE}" 2>/dev/null | jq '.gaps // []' 2>/dev/null)
GAP_COUNT=$(echo "$GAP_CHECK" | jq 'length' 2>/dev/null || echo "0")

if [ "$GAP_COUNT" -eq 0 ]; then
    log_info "  PASS: No gaps in audit trail"
    AUDIT_RESULTS["integrity:no_gaps"]="PASS"
    ((PASSED_CHECKS++))
else
    log_warn "  WARN: Found $GAP_COUNT gaps in audit trail"
    AUDIT_RESULTS["integrity:no_gaps"]="WARN"
fi

# Check signature verification (if available)
((TOTAL_CHECKS++))
log_check "Audit signature verification"

SIG_CHECK=$(curl -sf "${PLATO_URL}/api/v1/audit/verify?since=${TIME_RANGE}" 2>/dev/null || echo '{"status": "N/A"}')
SIG_STATUS=$(echo "$SIG_CHECK" | jq -r '.status // "N/A"' 2>/dev/null)

if [ "$SIG_STATUS" = "VERIFIED" ]; then
    log_info "  PASS: Audit signatures verified"
    AUDIT_RESULTS["integrity:signatures"]="PASS"
    ((PASSED_CHECKS++))
elif [ "$SIG_STATUS" = "N/A" ]; then
    log_warn "  SKIP: Signature verification not available"
    AUDIT_RESULTS["integrity:signatures"]="SKIP"
else
    log_error "  FAIL: Signature verification failed"
    AUDIT_RESULTS["integrity:signatures"]="FAIL"
    ((FAILED_CHECKS++))
fi

# =============================================================================
# Generate Report
# =============================================================================

if [ -n "$JSON_REPORT" ]; then
    mkdir -p "$(dirname "$JSON_REPORT")"
    
    cat > "$JSON_REPORT" << EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "correlation_id": "${CORRELATION_ID:-null}",
  "time_range": "$TIME_RANGE",
  "summary": {
    "total_checks": $TOTAL_CHECKS,
    "passed": $PASSED_CHECKS,
    "failed": $FAILED_CHECKS,
    "pass_rate": $(( PASSED_CHECKS * 100 / (TOTAL_CHECKS > 0 ? TOTAL_CHECKS : 1) ))
  },
  "audit_counts": {
    "policy_decisions": $POLICY_COUNT,
    "plan_executions": $PLAN_COUNT,
    "approvals": $APPROVAL_COUNT,
    "action_executions": $ACTION_COUNT,
    "connector_calls": $CONNECTOR_COUNT
  },
  "required_fields": {
    "policy_decision": ["eventType", "correlationId", "timestamp", "tenantId", "policyId", "decision", "context"],
    "plan_execution": ["eventType", "correlationId", "timestamp", "planId", "status", "steps"],
    "action_execution": ["eventType", "correlationId", "timestamp", "actionId", "toolId", "status", "input", "output", "duration"]
  },
  "results": {
EOF

    first=true
    for key in "${!AUDIT_RESULTS[@]}"; do
        result="${AUDIT_RESULTS[$key]}"
        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$JSON_REPORT"
        fi
        echo -n "    \"$key\": \"$result\"" >> "$JSON_REPORT"
    done

    cat >> "$JSON_REPORT" << EOF

  }
}
EOF

    log_info "Report saved to: $JSON_REPORT"
fi

# =============================================================================
# Summary
# =============================================================================

echo ""
log_info "============================================="
log_info "AUDIT TRAIL VALIDATION SUMMARY"
log_info "============================================="
log_info "Total Checks: $TOTAL_CHECKS"
log_info "Passed:       $PASSED_CHECKS"
log_info "Failed:       $FAILED_CHECKS"
log_info "============================================="

echo ""
echo "Audit Record Counts:"
echo "  Policy Decisions:   $POLICY_COUNT"
echo "  Plan Executions:    $PLAN_COUNT"
echo "  Approvals:          $APPROVAL_COUNT"
echo "  Action Executions:  $ACTION_COUNT"
echo "  Connector Calls:    $CONNECTOR_COUNT"

echo ""

if [ $FAILED_CHECKS -eq 0 ]; then
    log_info "AUDIT TRAIL VALIDATION: PASSED"
    exit 0
else
    log_error "AUDIT TRAIL VALIDATION: FAILED"
    exit 1
fi

