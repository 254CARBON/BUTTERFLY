#!/bin/bash
# =============================================================================
# BUTTERFLY Redis Backup Script
# =============================================================================
# Creates RDB snapshots of Redis data.
#
# Usage:
#   ./backup-redis.sh [OPTIONS]
#
# Options:
#   --namespace NS      Kubernetes namespace (default: butterfly)
#   --output-dir DIR    Output directory (default: /tmp/butterfly-backup/redis)
#   --s3-bucket BUCKET  S3 bucket for upload (optional)
#   --dry-run           Show what would be done without executing
#   --help              Show this help message
#
# Requirements:
#   - kubectl configured with cluster access
#   - aws CLI configured (if using S3 upload)
# =============================================================================

set -euo pipefail

# Configuration defaults
NAMESPACE="${NAMESPACE:-butterfly}"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/butterfly-backup/redis}"
S3_BUCKET="${S3_BUCKET:-}"
DRY_RUN="${DRY_RUN:-false}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_NAME="redis-backup-${TIMESTAMP}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

show_help() {
    head -22 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --namespace) NAMESPACE="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --s3-bucket) S3_BUCKET="$2"; shift 2 ;;
        --dry-run) DRY_RUN="true"; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 1 ;;
    esac
done

# Validate prerequisites
command -v kubectl &>/dev/null || { log_error "kubectl not found"; exit 1; }

# Get Redis pod (master)
REDIS_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=redis,role=master -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [[ -z "${REDIS_POD}" ]]; then
    REDIS_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=redis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
fi

if [[ -z "${REDIS_POD}" ]]; then
    log_error "No Redis pod found in namespace ${NAMESPACE}"
    exit 1
fi

log_info "Redis pod: ${REDIS_POD}"

if [[ "${DRY_RUN}" == "true" ]]; then
    log_warn "DRY RUN MODE - No changes will be made"
fi

# Create output directory
BACKUP_DIR="${OUTPUT_DIR}/${BACKUP_NAME}"
if [[ "${DRY_RUN}" != "true" ]]; then
    mkdir -p "${BACKUP_DIR}"
fi

# =============================================================================
# 1. Check Redis Status
# =============================================================================
log_info "Checking Redis status..."

if [[ "${DRY_RUN}" != "true" ]]; then
    kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- redis-cli INFO \
        > "${BACKUP_DIR}/redis-info.txt" 2>/dev/null || true
    
    kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- redis-cli INFO replication \
        > "${BACKUP_DIR}/replication-info.txt" 2>/dev/null || true
fi

# =============================================================================
# 2. Trigger BGSAVE
# =============================================================================
log_info "Triggering background save..."

if [[ "${DRY_RUN}" != "true" ]]; then
    # Get last save time before BGSAVE
    LASTSAVE_BEFORE=$(kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- \
        redis-cli LASTSAVE 2>/dev/null || echo "0")
    
    # Trigger BGSAVE
    kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- redis-cli BGSAVE
    
    # Wait for BGSAVE to complete
    log_info "Waiting for background save to complete..."
    for i in {1..60}; do
        LASTSAVE_AFTER=$(kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- \
            redis-cli LASTSAVE 2>/dev/null || echo "0")
        if [[ "${LASTSAVE_AFTER}" != "${LASTSAVE_BEFORE}" ]]; then
            log_info "Background save completed"
            break
        fi
        sleep 1
    done
fi

# =============================================================================
# 3. Copy RDB File
# =============================================================================
log_info "Copying RDB file..."

if [[ "${DRY_RUN}" != "true" ]]; then
    # Find RDB file location
    RDB_PATH=$(kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- \
        redis-cli CONFIG GET dir 2>/dev/null | tail -1 | tr -d '\r')
    RDB_FILE=$(kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- \
        redis-cli CONFIG GET dbfilename 2>/dev/null | tail -1 | tr -d '\r')
    
    FULL_RDB_PATH="${RDB_PATH}/${RDB_FILE}"
    log_info "RDB path: ${FULL_RDB_PATH}"
    
    # Copy RDB file
    kubectl cp "${NAMESPACE}/${REDIS_POD}:${FULL_RDB_PATH}" "${BACKUP_DIR}/dump.rdb" \
        || log_warn "Failed to copy RDB file"
fi

# =============================================================================
# 4. Export Key Statistics
# =============================================================================
log_info "Exporting key statistics..."

if [[ "${DRY_RUN}" != "true" ]]; then
    kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- redis-cli DBSIZE \
        > "${BACKUP_DIR}/dbsize.txt" 2>/dev/null || true
    
    # Get memory usage
    kubectl exec -n "${NAMESPACE}" "${REDIS_POD}" -- redis-cli INFO memory \
        > "${BACKUP_DIR}/memory-info.txt" 2>/dev/null || true
fi

# =============================================================================
# 5. Create Metadata
# =============================================================================
log_info "Creating backup metadata..."

if [[ "${DRY_RUN}" != "true" ]]; then
    cat > "${BACKUP_DIR}/metadata.json" << EOF
{
    "backup_type": "redis",
    "backup_name": "${BACKUP_NAME}",
    "timestamp": "${TIMESTAMP}",
    "namespace": "${NAMESPACE}",
    "pod": "${REDIS_POD}",
    "rdb_path": "${FULL_RDB_PATH:-unknown}",
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
    
    sha256sum "${ARCHIVE_PATH}" > "${ARCHIVE_PATH}.sha256"
fi

# =============================================================================
# 7. Upload to S3 (if configured)
# =============================================================================
if [[ -n "${S3_BUCKET}" ]]; then
    log_info "Uploading to S3: ${S3_BUCKET}"
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        aws s3 cp "${ARCHIVE_PATH}" "s3://${S3_BUCKET}/redis/${BACKUP_NAME}.tar.gz"
        aws s3 cp "${ARCHIVE_PATH}.sha256" "s3://${S3_BUCKET}/redis/${BACKUP_NAME}.tar.gz.sha256"
        log_info "Upload complete"
    fi
fi

# =============================================================================
# Summary
# =============================================================================
log_info "Redis backup complete!"
if [[ "${DRY_RUN}" != "true" ]]; then
    log_info "Backup location: ${BACKUP_DIR}"
    log_info "Archive: ${ARCHIVE_PATH}"
    du -sh "${ARCHIVE_PATH}" 2>/dev/null || true
fi

