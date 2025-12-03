#!/bin/bash
# =============================================================================
# BUTTERFLY Kafka Backup Script
# =============================================================================
# Backs up Kafka topic configurations, consumer offsets, and optionally data.
# 
# Usage:
#   ./backup-kafka.sh [OPTIONS]
#
# Options:
#   --namespace NS      Kubernetes namespace (default: butterfly)
#   --output-dir DIR    Output directory (default: /tmp/butterfly-backup/kafka)
#   --s3-bucket BUCKET  S3 bucket for upload (optional)
#   --include-data      Include topic data export (WARNING: can be large)
#   --dry-run           Show what would be done without executing
#   --help              Show this help message
#
# Requirements:
#   - kubectl configured with cluster access
#   - aws CLI configured (if using S3 upload)
#   - jq for JSON processing
# =============================================================================

set -euo pipefail

# Configuration defaults
NAMESPACE="${NAMESPACE:-butterfly}"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/butterfly-backup/kafka}"
S3_BUCKET="${S3_BUCKET:-}"
INCLUDE_DATA="${INCLUDE_DATA:-false}"
DRY_RUN="${DRY_RUN:-false}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_NAME="kafka-backup-${TIMESTAMP}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

show_help() {
    head -30 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --namespace) NAMESPACE="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --s3-bucket) S3_BUCKET="$2"; shift 2 ;;
        --include-data) INCLUDE_DATA="true"; shift ;;
        --dry-run) DRY_RUN="true"; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 1 ;;
    esac
done

# Validate prerequisites
command -v kubectl &>/dev/null || { log_error "kubectl not found"; exit 1; }
command -v jq &>/dev/null || { log_error "jq not found"; exit 1; }

# Get Kafka pod
KAFKA_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=kafka -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [[ -z "${KAFKA_POD}" ]]; then
    log_error "No Kafka pod found in namespace ${NAMESPACE}"
    exit 1
fi

log_info "Using Kafka pod: ${KAFKA_POD}"
log_info "Backup name: ${BACKUP_NAME}"

if [[ "${DRY_RUN}" == "true" ]]; then
    log_warn "DRY RUN MODE - No changes will be made"
fi

# Create output directory
BACKUP_DIR="${OUTPUT_DIR}/${BACKUP_NAME}"
if [[ "${DRY_RUN}" != "true" ]]; then
    mkdir -p "${BACKUP_DIR}"
fi

# =============================================================================
# 1. Backup Topic Configurations
# =============================================================================
log_info "Backing up topic configurations..."

if [[ "${DRY_RUN}" != "true" ]]; then
    # List all topics
    kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
        kafka-topics.sh --bootstrap-server localhost:9092 --list \
        > "${BACKUP_DIR}/topics.txt"
    
    # Get detailed configuration for each topic
    while IFS= read -r topic; do
        if [[ -n "${topic}" ]]; then
            log_info "  Backing up topic: ${topic}"
            kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic "${topic}" \
                > "${BACKUP_DIR}/topic-${topic}.txt" 2>/dev/null || true
            
            kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                kafka-configs.sh --bootstrap-server localhost:9092 --describe \
                --entity-type topics --entity-name "${topic}" \
                > "${BACKUP_DIR}/topic-config-${topic}.txt" 2>/dev/null || true
        fi
    done < "${BACKUP_DIR}/topics.txt"
fi

# =============================================================================
# 2. Backup Consumer Group Offsets
# =============================================================================
log_info "Backing up consumer group offsets..."

if [[ "${DRY_RUN}" != "true" ]]; then
    # List all consumer groups
    kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
        kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list \
        > "${BACKUP_DIR}/consumer-groups.txt"
    
    # Get offsets for each consumer group
    while IFS= read -r group; do
        if [[ -n "${group}" ]]; then
            log_info "  Backing up consumer group: ${group}"
            kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
                --describe --group "${group}" \
                > "${BACKUP_DIR}/consumer-group-${group}.txt" 2>/dev/null || true
            
            # Export offsets in CSV format for easy restore
            kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
                --describe --group "${group}" --offsets \
                > "${BACKUP_DIR}/consumer-group-offsets-${group}.csv" 2>/dev/null || true
        fi
    done < "${BACKUP_DIR}/consumer-groups.txt"
fi

# =============================================================================
# 3. Backup Broker Configurations
# =============================================================================
log_info "Backing up broker configurations..."

if [[ "${DRY_RUN}" != "true" ]]; then
    # Get broker IDs
    kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
        kafka-broker-api-versions.sh --bootstrap-server localhost:9092 2>/dev/null | \
        grep -E "^[0-9]+ ->" | awk '{print $1}' > "${BACKUP_DIR}/broker-ids.txt" || true
    
    # Get broker configs
    while IFS= read -r broker_id; do
        if [[ -n "${broker_id}" ]]; then
            log_info "  Backing up broker: ${broker_id}"
            kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                kafka-configs.sh --bootstrap-server localhost:9092 --describe \
                --entity-type brokers --entity-name "${broker_id}" \
                > "${BACKUP_DIR}/broker-config-${broker_id}.txt" 2>/dev/null || true
        fi
    done < "${BACKUP_DIR}/broker-ids.txt"
fi

# =============================================================================
# 4. Optionally Backup Topic Data
# =============================================================================
if [[ "${INCLUDE_DATA}" == "true" ]]; then
    log_warn "Data export enabled - this may take a long time and use significant storage"
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        mkdir -p "${BACKUP_DIR}/data"
        
        # Export last 1000 messages from critical topics
        CRITICAL_TOPICS=(
            "rim.fast-path"
            "perception.events"
            "capsule.snapshots"
            "odyssey.paths"
            "plato.specs"
        )
        
        for topic in "${CRITICAL_TOPICS[@]}"; do
            if grep -q "^${topic}$" "${BACKUP_DIR}/topics.txt" 2>/dev/null; then
                log_info "  Exporting data from: ${topic}"
                kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                    kafka-console-consumer.sh --bootstrap-server localhost:9092 \
                    --topic "${topic}" --from-beginning --max-messages 1000 \
                    --timeout-ms 30000 \
                    > "${BACKUP_DIR}/data/${topic}.json" 2>/dev/null || true
            fi
        done
    fi
fi

# =============================================================================
# 5. Create Metadata File
# =============================================================================
log_info "Creating backup metadata..."

if [[ "${DRY_RUN}" != "true" ]]; then
    cat > "${BACKUP_DIR}/metadata.json" << EOF
{
    "backup_type": "kafka",
    "backup_name": "${BACKUP_NAME}",
    "timestamp": "${TIMESTAMP}",
    "namespace": "${NAMESPACE}",
    "kafka_pod": "${KAFKA_POD}",
    "include_data": ${INCLUDE_DATA},
    "created_by": "$(whoami)",
    "hostname": "$(hostname)"
}
EOF
fi

# =============================================================================
# 6. Create Archive
# =============================================================================
log_info "Creating backup archive..."

if [[ "${DRY_RUN}" != "true" ]]; then
    ARCHIVE_PATH="${OUTPUT_DIR}/${BACKUP_NAME}.tar.gz"
    tar -czf "${ARCHIVE_PATH}" -C "${OUTPUT_DIR}" "${BACKUP_NAME}"
    log_info "Archive created: ${ARCHIVE_PATH}"
    
    # Calculate checksum
    sha256sum "${ARCHIVE_PATH}" > "${ARCHIVE_PATH}.sha256"
fi

# =============================================================================
# 7. Upload to S3 (if configured)
# =============================================================================
if [[ -n "${S3_BUCKET}" ]]; then
    log_info "Uploading to S3: ${S3_BUCKET}"
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        aws s3 cp "${ARCHIVE_PATH}" "s3://${S3_BUCKET}/kafka/${BACKUP_NAME}.tar.gz"
        aws s3 cp "${ARCHIVE_PATH}.sha256" "s3://${S3_BUCKET}/kafka/${BACKUP_NAME}.tar.gz.sha256"
        log_info "Upload complete"
    fi
fi

# =============================================================================
# Summary
# =============================================================================
log_info "Kafka backup complete!"
if [[ "${DRY_RUN}" != "true" ]]; then
    log_info "Backup location: ${BACKUP_DIR}"
    log_info "Archive: ${ARCHIVE_PATH}"
    du -sh "${ARCHIVE_PATH}" 2>/dev/null || true
fi

