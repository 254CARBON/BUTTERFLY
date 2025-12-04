#!/usr/bin/env bash
# =============================================================================
# BUTTERFLY Automated DLQ Replay Script
# =============================================================================
# Automatically triggers DLQ replay after chaos scenarios recover.
# Polls DLQ topics for pending messages, triggers replay via PERCEPTION API,
# and validates success rates against SLOs.
#
# Usage:
#   ./auto-dlq-replay.sh [OPTIONS]
#
# Options:
#   --perception-url URL    PERCEPTION API URL (default: http://localhost:8081)
#   --prometheus-url URL    Prometheus URL for metrics export (optional)
#   --pushgateway-url URL   Prometheus Pushgateway URL (optional)
#   --topics TOPICS         Comma-separated DLQ topics to replay (default: all enabled)
#   --tenant-id ID          Tenant ID for scoped replay (optional)
#   --dry-run               Preview what would be replayed without executing
#   --max-records N         Maximum records to replay per topic (default: 1000)
#   --rate-limit N          Messages per second rate limit (default: 100)
#   --output FILE           Output JSON file for results
#   --operator NAME         Operator name for audit trail (default: resilience-ci)
#   --ticket ID             Ticket reference for audit trail
#   --help                  Show this help message
#
# Exit Codes:
#   0 - DLQ replay completed successfully (≥93% success rate)
#   1 - DLQ replay failed or below SLO
#   2 - Configuration error
#
# Requirements:
#   - curl, jq
#   - PERCEPTION DLQ replay API accessible
# =============================================================================

set -euo pipefail

# Script metadata
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "$0")"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Default configuration
PERCEPTION_URL="${PERCEPTION_URL:-http://localhost:8081}"
PROMETHEUS_URL="${PROMETHEUS_URL:-}"
PUSHGATEWAY_URL="${PUSHGATEWAY_URL:-}"
DLQ_TOPICS=""
TENANT_ID=""
DRY_RUN=false
MAX_RECORDS=1000
RATE_LIMIT=100
OUTPUT_FILE=""
OPERATOR="resilience-ci"
TICKET="RESILIENCE-CI-${TIMESTAMP}"

# SLO thresholds
SLO_SUCCESS_RATE=93  # ≥93% success rate
SLO_MESSAGE_LOSS=1   # <1% message loss

# Results tracking
declare -A TOPIC_RESULTS
TOTAL_MATCHED=0
TOTAL_REPLAYED=0
TOTAL_FAILED=0
TOTAL_SKIPPED=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

show_help() {
    head -35 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --perception-url) PERCEPTION_URL="$2"; shift 2 ;;
            --prometheus-url) PROMETHEUS_URL="$2"; shift 2 ;;
            --pushgateway-url) PUSHGATEWAY_URL="$2"; shift 2 ;;
            --topics) DLQ_TOPICS="$2"; shift 2 ;;
            --tenant-id) TENANT_ID="$2"; shift 2 ;;
            --dry-run) DRY_RUN=true; shift ;;
            --max-records) MAX_RECORDS="$2"; shift 2 ;;
            --rate-limit) RATE_LIMIT="$2"; shift 2 ;;
            --output) OUTPUT_FILE="$2"; shift 2 ;;
            --operator) OPERATOR="$2"; shift 2 ;;
            --ticket) TICKET="$2"; shift 2 ;;
            --help|-h) show_help ;;
            *) log_error "Unknown option: $1"; exit 2 ;;
        esac
    done
}

# Validate prerequisites
validate_prerequisites() {
    command -v curl &>/dev/null || { log_error "curl is required"; exit 2; }
    command -v jq &>/dev/null || { log_error "jq is required"; exit 2; }
}

# Check PERCEPTION API health
check_perception_health() {
    log_step "Checking PERCEPTION API health..."
    
    local health_url="${PERCEPTION_URL}/actuator/health"
    local status
    
    for i in {1..3}; do
        status=$(curl -s -o /dev/null -w "%{http_code}" "$health_url" 2>/dev/null || echo "000")
        
        if [[ "$status" == "200" ]]; then
            log_info "PERCEPTION API is healthy"
            return 0
        fi
        
        sleep 2
    done
    
    log_error "PERCEPTION API not responding at ${PERCEPTION_URL}"
    return 1
}

# Get list of DLQ topics from PERCEPTION API or use defaults
get_dlq_topics() {
    if [[ -n "$DLQ_TOPICS" ]]; then
        echo "$DLQ_TOPICS" | tr ',' '\n'
        return
    fi
    
    # Try to get enabled topics from PERCEPTION API
    local topics_response
    topics_response=$(curl -s "${PERCEPTION_URL}/api/v1/dlq/topics" 2>/dev/null || echo "")
    
    if [[ -n "$topics_response" ]] && echo "$topics_response" | jq -e '.topics' &>/dev/null; then
        echo "$topics_response" | jq -r '.topics[]'
        return
    fi
    
    # Default DLQ topics based on common patterns
    cat << 'EOF'
acquisition.errors.http
acquisition.errors.rss
acquisition.errors.file
acquisition.errors.crawl
acquisition.errors.partner.api
acquisition.errors.partner.queue
perception.events.dlq
perception.signals.dlq
EOF
}

# Check DLQ topic depth
check_dlq_depth() {
    local topic="$1"
    
    local dlq_info
    dlq_info=$(curl -s "${PERCEPTION_URL}/api/v1/dlq/events?topic=${topic}&limit=0" 2>/dev/null || echo "")
    
    if [[ -n "$dlq_info" ]] && echo "$dlq_info" | jq -e '.count' &>/dev/null; then
        echo "$dlq_info" | jq -r '.count'
    else
        echo "0"
    fi
}

# Trigger DLQ replay for a topic
replay_topic() {
    local topic="$1"
    local start_time=$(date +%s)
    
    log_step "Replaying DLQ topic: $topic"
    
    # Build request body
    local request_body
    request_body=$(cat << EOF
{
    "topic": "${topic}",
    "target": "RAW",
    "dryRun": ${DRY_RUN},
    "maxRecords": ${MAX_RECORDS},
    "rateLimitPerSecond": ${RATE_LIMIT},
    "operator": "${OPERATOR}",
    "ticket": "${TICKET}",
    "reason": "Automated DLQ replay triggered by resilience CI stage"
EOF
)

    # Add tenant ID if specified
    if [[ -n "$TENANT_ID" ]]; then
        request_body=$(echo "$request_body" | jq --arg tid "$TENANT_ID" '. + {tenantId: $tid}')
    fi

    request_body="${request_body}}"

    # Execute replay
    local response
    local http_code
    
    response=$(curl -s -w "\n%{http_code}" \
        -X POST "${PERCEPTION_URL}/api/v1/dlq/replay" \
        -H "Content-Type: application/json" \
        -H "X-Tenant-ID: ${TENANT_ID:-resilience-ci}" \
        -d "$request_body" 2>/dev/null || echo -e "\n000")
    
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    if [[ "$http_code" == "200" || "$http_code" == "202" ]]; then
        # Parse response
        local matched replayed failed skipped success_rate
        
        matched=$(echo "$body" | jq -r '.matched // 0')
        replayed=$(echo "$body" | jq -r '.replayed // 0')
        failed=$(echo "$body" | jq -r '.failed // 0')
        skipped=$(echo "$body" | jq -r '.skipped // 0')
        
        if [[ $matched -gt 0 ]]; then
            success_rate=$(echo "scale=2; $replayed * 100 / $matched" | bc)
        else
            success_rate="100.00"
        fi
        
        # Update totals
        TOTAL_MATCHED=$((TOTAL_MATCHED + matched))
        TOTAL_REPLAYED=$((TOTAL_REPLAYED + replayed))
        TOTAL_FAILED=$((TOTAL_FAILED + failed))
        TOTAL_SKIPPED=$((TOTAL_SKIPPED + skipped))
        
        TOPIC_RESULTS["$topic"]="matched:${matched},replayed:${replayed},failed:${failed},skipped:${skipped},rate:${success_rate}%,duration:${duration}s"
        
        log_info "  Topic: $topic"
        log_info "    Matched: $matched"
        log_info "    Replayed: $replayed"
        log_info "    Failed: $failed"
        log_info "    Skipped: $skipped"
        log_info "    Success Rate: ${success_rate}%"
        log_info "    Duration: ${duration}s"
        
        if (( $(echo "$success_rate >= $SLO_SUCCESS_RATE" | bc -l) )); then
            log_info "    ✓ Meets SLO (≥${SLO_SUCCESS_RATE}%)"
            return 0
        else
            log_warn "    ⚠ Below SLO (${success_rate}% < ${SLO_SUCCESS_RATE}%)"
            return 1
        fi
    else
        log_error "  Failed to replay topic $topic (HTTP $http_code)"
        log_error "  Response: $body"
        TOPIC_RESULTS["$topic"]="error:HTTP_${http_code},duration:${duration}s"
        return 1
    fi
}

# Export metrics to Prometheus Pushgateway
export_metrics() {
    if [[ -z "$PUSHGATEWAY_URL" ]]; then
        return 0
    fi
    
    log_step "Exporting metrics to Prometheus Pushgateway..."
    
    local overall_success_rate=0
    if [[ $TOTAL_MATCHED -gt 0 ]]; then
        overall_success_rate=$(echo "scale=4; $TOTAL_REPLAYED / $TOTAL_MATCHED" | bc)
    fi
    
    cat << EOF | curl -s --data-binary @- "${PUSHGATEWAY_URL}/metrics/job/butterfly_dlq_replay/instance/resilience_ci" || log_warn "Failed to push metrics"
# HELP butterfly_dlq_replay_matched_total Total DLQ messages matched for replay
# TYPE butterfly_dlq_replay_matched_total counter
butterfly_dlq_replay_matched_total ${TOTAL_MATCHED}
# HELP butterfly_dlq_replay_replayed_total Total DLQ messages successfully replayed
# TYPE butterfly_dlq_replay_replayed_total counter
butterfly_dlq_replay_replayed_total ${TOTAL_REPLAYED}
# HELP butterfly_dlq_replay_failed_total Total DLQ messages that failed replay
# TYPE butterfly_dlq_replay_failed_total counter
butterfly_dlq_replay_failed_total ${TOTAL_FAILED}
# HELP butterfly_dlq_replay_success_rate DLQ replay success rate (0-1)
# TYPE butterfly_dlq_replay_success_rate gauge
butterfly_dlq_replay_success_rate ${overall_success_rate}
# HELP butterfly_dlq_replay_timestamp_seconds Unix timestamp of last replay
# TYPE butterfly_dlq_replay_timestamp_seconds gauge
butterfly_dlq_replay_timestamp_seconds $(date +%s)
EOF
    
    log_info "Metrics exported to Pushgateway"
}

# Write output JSON file
write_output() {
    if [[ -z "$OUTPUT_FILE" ]]; then
        return 0
    fi
    
    local overall_success_rate=0
    local message_loss_rate=0
    
    if [[ $TOTAL_MATCHED -gt 0 ]]; then
        overall_success_rate=$(echo "scale=2; $TOTAL_REPLAYED * 100 / $TOTAL_MATCHED" | bc)
        message_loss_rate=$(echo "scale=4; $TOTAL_FAILED / $TOTAL_MATCHED" | bc)
    fi
    
    local slo_met="true"
    if (( $(echo "$overall_success_rate < $SLO_SUCCESS_RATE" | bc -l) )); then
        slo_met="false"
    fi
    
    cat > "$OUTPUT_FILE" << EOF
{
    "timestamp": "$(date -Iseconds)",
    "operator": "${OPERATOR}",
    "ticket": "${TICKET}",
    "dry_run": ${DRY_RUN},
    "matched": ${TOTAL_MATCHED},
    "replayed": ${TOTAL_REPLAYED},
    "failed": ${TOTAL_FAILED},
    "skipped": ${TOTAL_SKIPPED},
    "success_rate": ${overall_success_rate},
    "message_loss_rate": ${message_loss_rate},
    "slo_met": ${slo_met},
    "slo_targets": {
        "success_rate_threshold": ${SLO_SUCCESS_RATE},
        "message_loss_threshold": ${SLO_MESSAGE_LOSS}
    },
    "topics": {
$(for topic in "${!TOPIC_RESULTS[@]}"; do
    echo "        \"${topic}\": \"${TOPIC_RESULTS[$topic]}\","
done | sed '$ s/,$//')
    }
}
EOF
    
    log_info "Results written to: $OUTPUT_FILE"
}

# Print summary
print_summary() {
    local overall_success_rate=0
    if [[ $TOTAL_MATCHED -gt 0 ]]; then
        overall_success_rate=$(echo "scale=2; $TOTAL_REPLAYED * 100 / $TOTAL_MATCHED" | bc)
    fi
    
    echo ""
    echo "================================================================="
    echo "      DLQ Replay Summary"
    echo "================================================================="
    echo ""
    log_info "Total Matched:   ${TOTAL_MATCHED}"
    log_info "Total Replayed:  ${TOTAL_REPLAYED}"
    log_info "Total Failed:    ${TOTAL_FAILED}"
    log_info "Total Skipped:   ${TOTAL_SKIPPED}"
    log_info "Success Rate:    ${overall_success_rate}%"
    echo ""
    
    if [[ ${#TOPIC_RESULTS[@]} -gt 0 ]]; then
        log_info "Per-Topic Results:"
        for topic in "${!TOPIC_RESULTS[@]}"; do
            log_info "  - $topic: ${TOPIC_RESULTS[$topic]}"
        done
    fi
    
    echo ""
    
    if (( $(echo "$overall_success_rate >= $SLO_SUCCESS_RATE" | bc -l) )); then
        log_info "✅ DLQ replay meets SLO (≥${SLO_SUCCESS_RATE}%)"
        return 0
    else
        log_error "❌ DLQ replay below SLO (${overall_success_rate}% < ${SLO_SUCCESS_RATE}%)"
        return 1
    fi
}

# Main execution
main() {
    parse_args "$@"
    
    echo ""
    echo "================================================================="
    echo "      BUTTERFLY Automated DLQ Replay"
    echo "================================================================="
    echo ""
    
    validate_prerequisites
    
    if ! check_perception_health; then
        exit 2
    fi
    
    # Get topics to replay
    local topics
    mapfile -t topics < <(get_dlq_topics)
    
    if [[ ${#topics[@]} -eq 0 ]]; then
        log_warn "No DLQ topics found to replay"
        exit 0
    fi
    
    log_info "Topics to replay: ${#topics[@]}"
    for t in "${topics[@]}"; do
        log_info "  - $t"
    done
    echo ""
    
    # Check each topic for pending messages and replay
    local topics_with_messages=0
    
    for topic in "${topics[@]}"; do
        local depth
        depth=$(check_dlq_depth "$topic")
        
        if [[ "$depth" -gt 0 ]]; then
            log_info "Topic $topic has $depth pending messages"
            ((topics_with_messages++))
            replay_topic "$topic" || true
            echo ""
        else
            log_info "Topic $topic has no pending messages - skipping"
        fi
    done
    
    if [[ $topics_with_messages -eq 0 ]]; then
        log_info "No DLQ topics have pending messages - nothing to replay"
        
        # Still write output for tracking
        if [[ -n "$OUTPUT_FILE" ]]; then
            cat > "$OUTPUT_FILE" << EOF
{
    "timestamp": "$(date -Iseconds)",
    "operator": "${OPERATOR}",
    "ticket": "${TICKET}",
    "dry_run": ${DRY_RUN},
    "matched": 0,
    "replayed": 0,
    "failed": 0,
    "skipped": 0,
    "success_rate": 100,
    "message_loss_rate": 0,
    "slo_met": true,
    "topics": {}
}
EOF
        fi
        
        exit 0
    fi
    
    # Export metrics
    export_metrics
    
    # Write output file
    write_output
    
    # Print summary and exit with appropriate code
    if print_summary; then
        exit 0
    else
        exit 1
    fi
}

main "$@"

