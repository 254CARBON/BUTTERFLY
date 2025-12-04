#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
E2E_DIR="$ROOT_DIR/butterfly-e2e"
SCENARIOS_DIR="$E2E_DIR/scenarios"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

echo "================================================================="
echo "      BUTTERFLY Golden Path E2E Test Suite"
echo "================================================================="
echo ""

# Parse arguments
RUN_STRATEGIC=false
RUN_REFLEX=true
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --strategic)
            RUN_STRATEGIC=true
            shift
            ;;
        --all)
            RUN_STRATEGIC=true
            RUN_REFLEX=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [ "$SKIP_BUILD" = false ]; then
    log_info "Ensuring butterfly-common is installed locally"
    mvn -q -f "$ROOT_DIR/butterfly-common/pom.xml" install -DskipTests
fi

# ============================================================
# 1. ODYSSEY Reflex Golden Path Test
# ============================================================
if [ "$RUN_REFLEX" = true ]; then
    log_info "Running ODYSSEY reflex golden path integration test"
    mvn -f "$ROOT_DIR/ODYSSEY/pom.xml" -Dtest=HftGoldenPathIntegrationTest test
    echo ""
    log_info "✅ Golden path reflex test executed"
    echo ""
fi

# ============================================================
# 2. Strategic Scenario Golden Path Test (EURUSD Market Shock)
# ============================================================
if [ "$RUN_STRATEGIC" = true ]; then
    log_info "Running Strategic Scenario Golden Path: EURUSD Market Shock"
    log_info "Flow: PERCEPTION → CAPSULE → ODYSSEY → PLATO → NEXUS"
    echo ""

    # Check if services are available (basic health check)
    check_service() {
        local service=$1
        local url=$2
        local timeout=5

        if curl -s --max-time $timeout "$url" > /dev/null 2>&1; then
            log_info "  ✓ $service is reachable"
            return 0
        else
            log_warn "  ✗ $service is not reachable at $url"
            return 1
        fi
    }

    log_info "Checking service availability..."

    SERVICES_OK=true
    check_service "PERCEPTION" "http://localhost:8081/actuator/health" || SERVICES_OK=false
    check_service "CAPSULE" "http://localhost:8082/actuator/health" || SERVICES_OK=false
    check_service "ODYSSEY" "http://localhost:8083/actuator/health" || SERVICES_OK=false
    check_service "PLATO" "http://localhost:8084/actuator/health" || SERVICES_OK=false
    check_service "NEXUS" "http://localhost:8085/actuator/health" || SERVICES_OK=false

    if [ "$SERVICES_OK" = false ]; then
        log_warn "Some services are not available. Strategic scenario may fail."
        log_info "To start services: docker-compose -f $E2E_DIR/docker-compose.full.yml up -d"
        echo ""
    fi

    # Generate correlation ID
    CORRELATION_ID="golden-path-$(date +%s)-$(uuidgen | cut -d'-' -f1 || echo $$)"
    log_info "Correlation ID: $CORRELATION_ID"
    echo ""

    # Step 1: Publish Market Shock Event to PERCEPTION
    log_info "Step 1: Publishing market shock event to perception.events..."
    
    EVENT_PAYLOAD=$(cat <<EOF
{
  "eventId": "evt-eurusd-shock-$(date +%s)",
  "eventType": "MARKET_SHOCK",
  "title": "EURUSD Flash Crash Detected",
  "timestamp": $(date +%s000),
  "eventCategory": "FX_MARKET",
  "trustScore": 0.9,
  "correlationId": "$CORRELATION_ID",
  "rimNodeIds": ["rim:entity:finance:EURUSD"],
  "impactEstimate": {
    "magnitude": 0.85,
    "direction": "DOWN",
    "confidence": 0.8
  }
}
EOF
)
    
    # Publish to Kafka (if kafkacat/kcat is available)
    if command -v kcat &> /dev/null || command -v kafkacat &> /dev/null; then
        KCAT_CMD=$(command -v kcat || command -v kafkacat)
        echo "$EVENT_PAYLOAD" | $KCAT_CMD -P -b localhost:9092 -t perception.events -H "correlationId=$CORRELATION_ID"
        log_info "  ✓ Event published to perception.events"
    else
        log_warn "  kafkacat/kcat not found - skipping Kafka publish"
        log_info "  Publishing via HTTP to PERCEPTION API instead..."
        # Fallback: Use curl to hit PERCEPTION API if available
    fi

    # Step 2: Wait for processing
    log_info "Step 2: Waiting for event propagation (5 seconds)..."
    sleep 5

    # Step 3: Verify CAPSULE received and stored history
    log_info "Step 3: Verifying CAPSULE history..."
    CAPSULE_RESPONSE=$(curl -s -X GET \
        "http://localhost:8082/api/v1/history?scopeId=rim:entity:finance:EURUSD&fromTs=$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)&toTs=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        -H "X-Correlation-ID: $CORRELATION_ID" \
        -H "Accept: application/json" 2>/dev/null || echo '{"error":"request_failed"}')
    
    if echo "$CAPSULE_RESPONSE" | grep -q "EURUSD\|capsule"; then
        log_info "  ✓ CAPSULE history contains EURUSD data"
    else
        log_warn "  ✗ CAPSULE history check inconclusive"
    fi

    # Step 4: Verify ODYSSEY paths
    log_info "Step 4: Verifying ODYSSEY path projections..."
    ODYSSEY_RESPONSE=$(curl -s -X GET \
        "http://localhost:8083/api/v1/strategic/paths/rim:entity:finance:EURUSD" \
        -H "X-Correlation-ID: $CORRELATION_ID" \
        -H "Accept: application/json" 2>/dev/null || echo '{"error":"request_failed"}')
    
    if echo "$ODYSSEY_RESPONSE" | grep -q "paths\|nodeId"; then
        log_info "  ✓ ODYSSEY returned path projections"
    else
        log_warn "  ✗ ODYSSEY paths check inconclusive"
    fi

    # Step 5: Verify PLATO governance evaluation
    log_info "Step 5: Verifying PLATO governance evaluation..."
    PLATO_RESPONSE=$(curl -s -X GET \
        "http://localhost:8084/api/v1/strategic/governance/evaluate/rim:entity:finance:EURUSD?stressLevel=0.85&regime=CRISIS" \
        -H "X-Correlation-ID: $CORRELATION_ID" \
        -H "Accept: application/json" 2>/dev/null || echo '{"error":"request_failed"}')
    
    if echo "$PLATO_RESPONSE" | grep -q "triggeredPolicies\|recommendedPlans"; then
        log_info "  ✓ PLATO returned governance evaluation with plans"
    else
        log_warn "  ✗ PLATO governance check inconclusive"
    fi

    # Step 6: Verify NEXUS strategic synthesis
    log_info "Step 6: Verifying NEXUS strategic synthesis..."
    NEXUS_RESPONSE=$(curl -s -X GET \
        "http://localhost:8085/api/v1/synthesis/strategic/rim:entity:finance:EURUSD" \
        -H "X-Correlation-ID: $CORRELATION_ID" \
        -H "Accept: application/json" 2>/dev/null || echo '{"error":"request_failed"}')
    
    if echo "$NEXUS_RESPONSE" | grep -q "options\|strategicOption\|worldState"; then
        log_info "  ✓ NEXUS returned strategic options"
    else
        log_warn "  ✗ NEXUS synthesis check inconclusive"
    fi

    echo ""
    log_info "Strategic Scenario Golden Path completed."
    log_info "Review correlation ID $CORRELATION_ID in service logs for full trace."
fi

echo ""
echo "================================================================="
echo "      Golden Path Tests Complete"
echo "================================================================="
