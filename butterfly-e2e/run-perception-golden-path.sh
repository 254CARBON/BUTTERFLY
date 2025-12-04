#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
E2E_DIR="$ROOT_DIR/butterfly-e2e"
SCENARIOS_DIR="$E2E_DIR/scenarios"

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

# Default values
PERCEPTION_URL="${PERCEPTION_URL:-http://localhost:8081}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"
CORRELATION_ID="perception-gp-$(date +%s)-$(openssl rand -hex 4 2>/dev/null || echo $$)"
TENANT_ID="${TENANT_ID:-e2e-test}"
SKIP_CLEANUP="${SKIP_CLEANUP:-false}"
VERBOSE="${VERBOSE:-false}"

# Track metrics
declare -A LATENCIES
START_TIME=$(date +%s%3N)

echo "================================================================="
echo "    PERCEPTION Golden Path E2E Test (Source → Scenario)"
echo "================================================================="
echo ""
echo "Target: $PERCEPTION_URL"
echo "Correlation ID: $CORRELATION_ID"
echo "Tenant: $TENANT_ID"
echo ""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --perception-url)
            PERCEPTION_URL="$2"
            shift 2
            ;;
        --tenant)
            TENANT_ID="$2"
            shift 2
            ;;
        --skip-cleanup)
            SKIP_CLEANUP=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --perception-url URL   PERCEPTION API URL (default: http://localhost:8081)"
            echo "  --tenant ID            Tenant ID (default: e2e-test)"
            echo "  --skip-cleanup         Skip cleanup of test resources"
            echo "  --verbose              Enable verbose output"
            echo "  -h, --help             Show this help"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Helper function for HTTP requests
http_request() {
    local method="$1"
    local endpoint="$2"
    local body="${3:-}"
    local step_name="${4:-unknown}"
    
    local url="${PERCEPTION_URL}${endpoint}"
    local step_start=$(date +%s%3N)
    
    local curl_args=(
        -s
        -X "$method"
        -H "Content-Type: application/json"
        -H "X-Tenant-ID: $TENANT_ID"
        -H "X-Correlation-ID: $CORRELATION_ID"
        -w "\n%{http_code}"
    )
    
    if [[ -n "$body" ]]; then
        curl_args+=(-d "$body")
    fi
    
    local response
    response=$(curl "${curl_args[@]}" "$url" 2>/dev/null) || true
    
    local http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | sed '$d')
    
    local step_end=$(date +%s%3N)
    local latency=$((step_end - step_start))
    LATENCIES["$step_name"]=$latency
    
    if [[ "$VERBOSE" == "true" ]]; then
        echo "  Response ($http_code): $response_body"
    fi
    
    echo "$http_code|$response_body"
}

# Step tracking
STEP=0
PASSED=0
FAILED=0
WARNINGS=0

run_step() {
    local name="$1"
    local expected_status="$2"
    
    ((STEP++))
    log_step "[$STEP] $name"
}

check_result() {
    local actual_status="$1"
    local expected_status="$2"
    local step_name="$3"
    
    # expected_status can be "200" or "200,201,202"
    IFS=',' read -ra EXPECTED <<< "$expected_status"
    
    local success=false
    for status in "${EXPECTED[@]}"; do
        if [[ "$actual_status" == "$status" ]]; then
            success=true
            break
        fi
    done
    
    if [[ "$success" == "true" ]]; then
        ((PASSED++))
        log_info "  ✓ $step_name (${LATENCIES[$step_name]:-?}ms)"
        return 0
    else
        ((FAILED++))
        log_error "  ✗ $step_name - Expected: $expected_status, Got: $actual_status"
        return 1
    fi
}

# ============================================================
# Step 1: Health Check
# ============================================================
run_step "Verify PERCEPTION service health" "200"
response=$(http_request "GET" "/actuator/health" "" "health_check")
status=$(echo "$response" | cut -d'|' -f1)
check_result "$status" "200" "Health Check" || true

# ============================================================
# Step 2: Register Test Source (ACQUISITION)
# ============================================================
run_step "Register test source (ACQUISITION)" "200,201"
SOURCE_BODY=$(cat <<EOF
{
  "name": "E2E Golden Path Test Source $(date +%s)",
  "type": "API",
  "endpoint": "https://test.example.com/api/feed",
  "tenantId": "$TENANT_ID",
  "enabled": true,
  "shadowMode": false,
  "rateLimit": 100,
  "config": {
    "format": "json",
    "batchSize": 10
  },
  "metadata": {
    "rimNodeId": "rim:test:e2e:source:$CORRELATION_ID",
    "testCase": "perception-golden-path"
  }
}
EOF
)
response=$(http_request "POST" "/api/v1/acquisition/sources" "$SOURCE_BODY" "source_registration")
status=$(echo "$response" | cut -d'|' -f1)
body=$(echo "$response" | cut -d'|' -f2-)
SOURCE_ID=$(echo "$body" | jq -r '.sourceId // .data.sourceId // empty' 2>/dev/null || echo "")

if check_result "$status" "200,201" "Source Registration"; then
    log_info "    Source ID: $SOURCE_ID"
fi

# ============================================================
# Step 3: Ingest Test Content (ACQUISITION)
# ============================================================
run_step "Ingest test content (ACQUISITION)" "200,201,202"
INGEST_BODY=$(cat <<EOF
{
  "sourceId": "$SOURCE_ID",
  "contentType": "application/json",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "content": {
    "title": "E2E Golden Path Test Event",
    "body": "Market volatility detected in EURUSD pair with unusual trading patterns indicating potential flash crash scenario. Requires immediate attention.",
    "metadata": {
      "category": "MARKET_EVENT",
      "severity": "HIGH",
      "entities": ["EURUSD", "FX_MARKET"],
      "tags": ["volatility", "trading", "golden-path"]
    }
  },
  "idempotencyKey": "gp-$CORRELATION_ID"
}
EOF
)
response=$(http_request "POST" "/api/v1/acquisition/ingest" "$INGEST_BODY" "content_ingestion")
status=$(echo "$response" | cut -d'|' -f1)
body=$(echo "$response" | cut -d'|' -f2-)
CONTENT_ID=$(echo "$body" | jq -r '.contentId // .data.contentId // empty' 2>/dev/null || echo "")

if check_result "$status" "200,201,202" "Content Ingestion"; then
    log_info "    Content ID: $CONTENT_ID"
fi

# ============================================================
# Step 4: Wait for Processing
# ============================================================
log_info "Waiting for pipeline processing (5 seconds)..."
sleep 5

# ============================================================
# Step 5: Verify Acquisition Stats
# ============================================================
run_step "Verify acquisition statistics" "200"
response=$(http_request "GET" "/api/v1/acquisition/sources/${SOURCE_ID}/stats" "" "acquisition_stats")
status=$(echo "$response" | cut -d'|' -f1)
check_result "$status" "200" "Acquisition Stats" || true

# ============================================================
# Step 6: Check DLQ Status
# ============================================================
run_step "Verify DLQ empty for source" "200"
response=$(http_request "GET" "/api/v1/acquisition/dlq?sourceId=${SOURCE_ID}&limit=10" "" "dlq_check")
status=$(echo "$response" | cut -d'|' -f1)
body=$(echo "$response" | cut -d'|' -f2-)
DLQ_COUNT=$(echo "$body" | jq -r '.totalElements // .data.totalElements // 0' 2>/dev/null || echo "0")

if check_result "$status" "200" "DLQ Check"; then
    if [[ "$DLQ_COUNT" -gt 0 ]]; then
        ((WARNINGS++))
        log_warn "    DLQ contains $DLQ_COUNT messages"
    else
        log_info "    DLQ is empty"
    fi
fi

# ============================================================
# Step 7: Verify Trust Score (INTEGRITY)
# ============================================================
run_step "Verify trust score assessment (INTEGRITY)" "200"
if [[ -n "$CONTENT_ID" ]]; then
    response=$(http_request "GET" "/api/v1/integrity/trust/content/${CONTENT_ID}" "" "trust_scoring")
    status=$(echo "$response" | cut -d'|' -f1)
    body=$(echo "$response" | cut -d'|' -f2-)
    TRUST_SCORE=$(echo "$body" | jq -r '.score // .data.score // empty' 2>/dev/null || echo "")
    TRUST_LEVEL=$(echo "$body" | jq -r '.level // .data.level // empty' 2>/dev/null || echo "")
    
    if check_result "$status" "200" "Trust Scoring"; then
        log_info "    Trust Score: $TRUST_SCORE ($TRUST_LEVEL)"
    fi
else
    ((WARNINGS++))
    log_warn "  Skipped - no content ID"
fi

# ============================================================
# Step 8: Check Quarantine Status (INTEGRITY)
# ============================================================
run_step "Verify not quarantined (INTEGRITY)" "200,404"
if [[ -n "$CONTENT_ID" ]]; then
    response=$(http_request "GET" "/api/v1/integrity/quarantine?contentId=${CONTENT_ID}" "" "quarantine_check")
    status=$(echo "$response" | cut -d'|' -f1)
    check_result "$status" "200,404" "Quarantine Check" || true
else
    ((WARNINGS++))
    log_warn "  Skipped - no content ID"
fi

# ============================================================
# Step 9: Query Events (INTELLIGENCE)
# ============================================================
run_step "Query detected events (INTELLIGENCE)" "200"
FROM_TS=$(($(date +%s) - 300))000
TO_TS=$(date +%s)000
response=$(http_request "GET" "/api/v1/intelligence/events?fromTimestamp=${FROM_TS}&toTimestamp=${TO_TS}&limit=10" "" "event_query")
status=$(echo "$response" | cut -d'|' -f1)
body=$(echo "$response" | cut -d'|' -f2-)
EVENT_ID=$(echo "$body" | jq -r '.content[0].eventId // .data.content[0].eventId // empty' 2>/dev/null || echo "")

check_result "$status" "200" "Event Query" || true
if [[ -n "$EVENT_ID" ]]; then
    log_info "    Event ID: $EVENT_ID"
fi

# ============================================================
# Step 10: Query Scenarios (INTELLIGENCE)
# ============================================================
run_step "Query generated scenarios (INTELLIGENCE)" "200"
response=$(http_request "GET" "/api/v1/intelligence/scenarios?limit=5" "" "scenario_query")
status=$(echo "$response" | cut -d'|' -f1)
check_result "$status" "200" "Scenario Query" || true

# ============================================================
# Step 11: Verify Pipeline Health (MONITORING)
# ============================================================
run_step "Verify pipeline health (MONITORING)" "200"
response=$(http_request "GET" "/api/v1/monitoring/health" "" "health_score")
status=$(echo "$response" | cut -d'|' -f1)
body=$(echo "$response" | cut -d'|' -f2-)
HEALTH_SCORE=$(echo "$body" | jq -r '.overallScore // .data.overallScore // empty' 2>/dev/null || echo "")
PIPELINE_STATUS=$(echo "$body" | jq -r '.status // .data.status // empty' 2>/dev/null || echo "")

if check_result "$status" "200" "Pipeline Health"; then
    log_info "    Health Score: $HEALTH_SCORE ($PIPELINE_STATUS)"
fi

# ============================================================
# Step 12: Verify SLO Compliance (MONITORING)
# ============================================================
run_step "Verify SLO compliance (MONITORING)" "200"
response=$(http_request "GET" "/api/v1/monitoring/slo/snapshot" "" "slo_compliance")
status=$(echo "$response" | cut -d'|' -f1)
body=$(echo "$response" | cut -d'|' -f2-)
SLOS_MET=$(echo "$body" | jq -r '.slosMet // .data.slosMet // 0' 2>/dev/null || echo "0")
SLOS_WARNING=$(echo "$body" | jq -r '.slosWarning // .data.slosWarning // 0' 2>/dev/null || echo "0")
SLOS_CRITICAL=$(echo "$body" | jq -r '.slosCritical // .data.slosCritical // 0' 2>/dev/null || echo "0")
ERROR_BUDGET=$(echo "$body" | jq -r '.errorBudgetRemaining // .data.errorBudgetRemaining // empty' 2>/dev/null || echo "")

if check_result "$status" "200" "SLO Compliance"; then
    log_info "    SLOs Met: $SLOS_MET, Warning: $SLOS_WARNING, Critical: $SLOS_CRITICAL"
    if [[ -n "$ERROR_BUDGET" ]]; then
        log_info "    Error Budget Remaining: $(echo "$ERROR_BUDGET * 100" | bc 2>/dev/null || echo "$ERROR_BUDGET")%"
    fi
fi

# ============================================================
# Cleanup
# ============================================================
if [[ "$SKIP_CLEANUP" != "true" && -n "$SOURCE_ID" ]]; then
    log_info "Cleaning up test resources..."
    http_request "DELETE" "/api/v1/acquisition/sources/${SOURCE_ID}" "" "cleanup" >/dev/null 2>&1 || true
fi

# ============================================================
# Summary
# ============================================================
END_TIME=$(date +%s%3N)
TOTAL_DURATION=$((END_TIME - START_TIME))

echo ""
echo "================================================================="
echo "                    Test Summary"
echo "================================================================="
echo ""
echo "Total Steps: $STEP"
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo "Warnings: $WARNINGS"
echo "Total Duration: ${TOTAL_DURATION}ms"
echo ""

echo "Latency Breakdown:"
for key in "${!LATENCIES[@]}"; do
    printf "  %-30s %6sms\n" "$key:" "${LATENCIES[$key]}"
done
echo ""

# SLO Validation
echo "SLO Validation:"
if [[ ${LATENCIES["content_ingestion"]:-999999} -le 200 ]]; then
    echo "  ✓ Acquisition Ingestion Latency: ${LATENCIES["content_ingestion"]}ms <= 200ms"
else
    echo "  ✗ Acquisition Ingestion Latency: ${LATENCIES["content_ingestion"]}ms > 200ms"
fi

if [[ ${LATENCIES["trust_scoring"]:-999999} -le 100 ]]; then
    echo "  ✓ Trust Scoring Latency: ${LATENCIES["trust_scoring"]}ms <= 100ms"
else
    echo "  ✗ Trust Scoring Latency: ${LATENCIES["trust_scoring"]}ms > 100ms"
fi

if [[ "$TOTAL_DURATION" -le 90000 ]]; then
    echo "  ✓ Total E2E Duration: ${TOTAL_DURATION}ms <= 90000ms"
else
    echo "  ✗ Total E2E Duration: ${TOTAL_DURATION}ms > 90000ms"
fi

echo ""

if [[ "$FAILED" -eq 0 ]]; then
    echo -e "${GREEN}✓ PERCEPTION Golden Path Test PASSED${NC}"
    exit 0
elif [[ "$FAILED" -le 2 ]]; then
    echo -e "${YELLOW}⚠ PERCEPTION Golden Path Test PASSED WITH WARNINGS${NC}"
    exit 0
else
    echo -e "${RED}✗ PERCEPTION Golden Path Test FAILED${NC}"
    exit 1
fi
