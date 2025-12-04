#!/bin/bash
# =============================================================================
# Phase 1 Completion Criteria Checker
# =============================================================================
# Quick check of Phase 1 completion criteria without running full validation.
# Use this for status checks and CI gates.
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
NEXUS_URL="${NEXUS_URL:-http://localhost:8084}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}       Phase 1 Completion Criteria Check                    ${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# Counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
SKIPPED_CHECKS=0

check() {
    local name=$1
    local status=$2  # pass, fail, skip
    local message=$3
    
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    
    case $status in
        pass)
            PASSED_CHECKS=$((PASSED_CHECKS + 1))
            echo -e "${GREEN}✓${NC} $name: $message"
            ;;
        fail)
            FAILED_CHECKS=$((FAILED_CHECKS + 1))
            echo -e "${RED}✗${NC} $name: $message"
            ;;
        skip)
            SKIPPED_CHECKS=$((SKIPPED_CHECKS + 1))
            echo -e "${YELLOW}○${NC} $name: $message"
            ;;
    esac
}

# -----------------------------------------------------------------------------
# Scenario Coverage Check
# -----------------------------------------------------------------------------

echo ""
echo -e "${BLUE}Scenario Coverage${NC}"
echo "─────────────────"

CATALOG_FILE="scenarios/catalog.json"
if [ -f "$CATALOG_FILE" ]; then
    SCENARIO_COUNT=$(jq '. | length' "$CATALOG_FILE")
    if [ "$SCENARIO_COUNT" -ge 17 ]; then
        check "Scenario count" "pass" "$SCENARIO_COUNT scenarios defined (≥17 required)"
    else
        check "Scenario count" "fail" "$SCENARIO_COUNT scenarios defined (<17 required)"
    fi
    
    # Check for required categories
    for category in "golden-path" "temporal-navigation" "failure-injection"; do
        count=$(jq --arg cat "$category" '[.[] | select(.category == $cat or .golden == true)] | length' "$CATALOG_FILE" 2>/dev/null || echo "0")
        if [ "$count" -gt 0 ]; then
            check "$category scenarios" "pass" "$count scenario(s) defined"
        else
            check "$category scenarios" "fail" "No scenarios defined"
        fi
    done
else
    check "Scenario catalog" "fail" "catalog.json not found"
fi

# -----------------------------------------------------------------------------
# Documentation Check
# -----------------------------------------------------------------------------

echo ""
echo -e "${BLUE}Documentation${NC}"
echo "─────────────"

# Kafka topic registry
if [ -f "../docs/integration/kafka-topic-registry.md" ]; then
    topic_count=$(grep -c "| \`" ../docs/integration/kafka-topic-registry.md 2>/dev/null || echo "0")
    if [ "$topic_count" -ge 10 ]; then
        check "Kafka topic registry" "pass" "$topic_count topics documented"
    else
        check "Kafka topic registry" "fail" "Insufficient topic documentation ($topic_count topics)"
    fi
else
    check "Kafka topic registry" "fail" "kafka-topic-registry.md not found"
fi

# Schema versioning policy
if [ -f "../docs/contracts/SCHEMA_VERSIONING.md" ]; then
    check "Schema versioning policy" "pass" "SCHEMA_VERSIONING.md exists"
else
    check "Schema versioning policy" "fail" "SCHEMA_VERSIONING.md not found"
fi

# -----------------------------------------------------------------------------
# Avro Schema Check
# -----------------------------------------------------------------------------

echo ""
echo -e "${BLUE}Avro Schemas${NC}"
echo "────────────"

AVRO_DIR="../butterfly-common/src/main/avro"
if [ -d "$AVRO_DIR" ]; then
    schema_count=$(find "$AVRO_DIR" -name "*.avsc" | wc -l)
    if [ "$schema_count" -gt 0 ]; then
        check "Avro schemas" "pass" "$schema_count schemas defined"
        
        # Check for schema_version field in key schemas
        for schema in "NexusTemporalEvent" "NexusReasoningEvent" "PlatoEvent"; do
            if [ -f "$AVRO_DIR/${schema}.avsc" ]; then
                if grep -q '"schema_version"' "$AVRO_DIR/${schema}.avsc"; then
                    check "${schema} versioning" "pass" "schema_version field present"
                else
                    check "${schema} versioning" "fail" "schema_version field missing"
                fi
            fi
        done
    else
        check "Avro schemas" "fail" "No schemas found"
    fi
else
    check "Avro schemas" "fail" "Avro directory not found"
fi

# -----------------------------------------------------------------------------
# CI Pipeline Check
# -----------------------------------------------------------------------------

echo ""
echo -e "${BLUE}CI Pipeline${NC}"
echo "───────────"

if [ -f "../.github/workflows/contracts-guardrails.yml" ]; then
    if grep -q "avro-schema-compatibility" "../.github/workflows/contracts-guardrails.yml"; then
        check "Schema compatibility CI" "pass" "avro-schema-compatibility job defined"
    else
        check "Schema compatibility CI" "fail" "avro-schema-compatibility job not found"
    fi
else
    check "Schema compatibility CI" "fail" "contracts-guardrails.yml not found"
fi

# -----------------------------------------------------------------------------
# Configuration Check
# -----------------------------------------------------------------------------

echo ""
echo -e "${BLUE}Configuration${NC}"
echo "─────────────"

NEXUS_CONFIG="../butterfly-nexus/src/main/resources/application.yml"
if [ -f "$NEXUS_CONFIG" ]; then
    # Check for SLO configuration
    if grep -q "temporal:" "$NEXUS_CONFIG" && grep -q "slo:" "$NEXUS_CONFIG"; then
        check "Temporal SLO config" "pass" "SLO configuration present in NEXUS"
    else
        check "Temporal SLO config" "fail" "SLO configuration missing"
    fi
    
    # Check for client configuration
    if grep -q "clients:" "$NEXUS_CONFIG"; then
        check "Client config" "pass" "Client configuration present"
    else
        check "Client config" "fail" "Client configuration missing"
    fi
else
    check "NEXUS config" "fail" "application.yml not found"
fi

# -----------------------------------------------------------------------------
# Monitoring Check
# -----------------------------------------------------------------------------

echo ""
echo -e "${BLUE}Monitoring${NC}"
echo "──────────"

# Check for Prometheus alerts
if [ -f "../butterfly-nexus/config/prometheus-alerts.yml" ]; then
    alert_count=$(grep -c "alert:" "../butterfly-nexus/config/prometheus-alerts.yml" 2>/dev/null || echo "0")
    if [ "$alert_count" -ge 3 ]; then
        check "Prometheus alerts" "pass" "$alert_count alerts defined"
    else
        check "Prometheus alerts" "fail" "Insufficient alerts ($alert_count defined)"
    fi
else
    check "Prometheus alerts" "fail" "prometheus-alerts.yml not found"
fi

# Check for Grafana dashboard
if [ -f "../butterfly-nexus/config/grafana/temporal-slo-dashboard.json" ]; then
    check "Grafana dashboard" "pass" "temporal-slo-dashboard.json exists"
else
    check "Grafana dashboard" "fail" "temporal-slo-dashboard.json not found"
fi

# -----------------------------------------------------------------------------
# DLQ Infrastructure Check
# -----------------------------------------------------------------------------

echo ""
echo -e "${BLUE}DLQ Infrastructure${NC}"
echo "──────────────────"

if [ -f "../butterfly-common/src/main/java/com/z254/butterfly/common/kafka/dlq/DlqPublisher.java" ]; then
    check "DlqPublisher" "pass" "DlqPublisher.java exists"
else
    check "DlqPublisher" "fail" "DlqPublisher.java not found"
fi

if [ -f "../butterfly-nexus/src/main/java/com/z254/butterfly/nexus/api/DlqAdminController.java" ]; then
    check "DLQ Admin API" "pass" "DlqAdminController.java exists"
else
    check "DLQ Admin API" "fail" "DlqAdminController.java not found"
fi

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------

echo ""
echo "─────────────────────────────────────────────────────────────"
echo ""
echo -e "Total Checks: $TOTAL_CHECKS"
echo -e "  ${GREEN}Passed:${NC}  $PASSED_CHECKS"
echo -e "  ${RED}Failed:${NC}  $FAILED_CHECKS"
echo -e "  ${YELLOW}Skipped:${NC} $SKIPPED_CHECKS"
echo ""

if [ "$FAILED_CHECKS" -eq 0 ]; then
    echo -e "${GREEN}============================================================${NC}"
    echo -e "${GREEN}       All criteria checks passed!                          ${NC}"
    echo -e "${GREEN}============================================================${NC}"
    echo ""
    echo "Run full validation: ./run-phase1-validation.sh"
    exit 0
else
    echo -e "${RED}============================================================${NC}"
    echo -e "${RED}       $FAILED_CHECKS criteria check(s) failed               ${NC}"
    echo -e "${RED}============================================================${NC}"
    echo ""
    echo "Please address failing criteria before proceeding."
    exit 1
fi

