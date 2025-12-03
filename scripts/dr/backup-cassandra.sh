#!/bin/bash
# =============================================================================
# BUTTERFLY Cassandra Backup Script
# =============================================================================
# Creates snapshots of Cassandra keyspaces and uploads to S3.
# Supports incremental backups via snapshot names.
#
# Usage:
#   ./backup-cassandra.sh [OPTIONS]
#
# Options:
#   --namespace NS      Kubernetes namespace (default: butterfly)
#   --keyspaces KS      Comma-separated keyspaces (default: capsule,plato)
#   --output-dir DIR    Output directory (default: /tmp/butterfly-backup/cassandra)
#   --s3-bucket BUCKET  S3 bucket for upload (optional)
#   --snapshot-name NM  Custom snapshot name (default: auto-generated)
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
KEYSPACES="${KEYSPACES:-capsule,plato}"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/butterfly-backup/cassandra}"
S3_BUCKET="${S3_BUCKET:-}"
DRY_RUN="${DRY_RUN:-false}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
SNAPSHOT_NAME="${SNAPSHOT_NAME:-butterfly-${TIMESTAMP}}"
BACKUP_NAME="cassandra-backup-${TIMESTAMP}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

show_help() {
    head -28 "$0" | grep -E '^#' | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --namespace) NAMESPACE="$2"; shift 2 ;;
        --keyspaces) KEYSPACES="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --s3-bucket) S3_BUCKET="$2"; shift 2 ;;
        --snapshot-name) SNAPSHOT_NAME="$2"; shift 2 ;;
        --dry-run) DRY_RUN="true"; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 1 ;;
    esac
done

# Validate prerequisites
command -v kubectl &>/dev/null || { log_error "kubectl not found"; exit 1; }

# Get Cassandra pods
CASSANDRA_PODS=$(kubectl get pods -n "${NAMESPACE}" -l app=cassandra -o jsonpath='{.items[*].metadata.name}' 2>/dev/null || echo "")
if [[ -z "${CASSANDRA_PODS}" ]]; then
    log_error "No Cassandra pods found in namespace ${NAMESPACE}"
    exit 1
fi

CASSANDRA_POD=$(echo "${CASSANDRA_PODS}" | awk '{print $1}')
log_info "Primary Cassandra pod: ${CASSANDRA_POD}"
log_info "Snapshot name: ${SNAPSHOT_NAME}"
log_info "Keyspaces: ${KEYSPACES}"

if [[ "${DRY_RUN}" == "true" ]]; then
    log_warn "DRY RUN MODE - No changes will be made"
fi

# Create output directory
BACKUP_DIR="${OUTPUT_DIR}/${BACKUP_NAME}"
if [[ "${DRY_RUN}" != "true" ]]; then
    mkdir -p "${BACKUP_DIR}"
fi

# =============================================================================
# 1. Check Cluster Health
# =============================================================================
log_info "Checking Cassandra cluster health..."

if [[ "${DRY_RUN}" != "true" ]]; then
    kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- nodetool status \
        > "${BACKUP_DIR}/cluster-status.txt"
    
    # Verify all nodes are UN (Up/Normal)
    DOWN_NODES=$(grep -c "DN\|DL" "${BACKUP_DIR}/cluster-status.txt" || echo "0")
    if [[ "${DOWN_NODES}" -gt 0 ]]; then
        log_warn "Found ${DOWN_NODES} down nodes - backup may be incomplete"
    fi
fi

# =============================================================================
# 2. Flush Memtables
# =============================================================================
log_info "Flushing memtables to ensure data consistency..."

IFS=',' read -ra KEYSPACE_ARRAY <<< "${KEYSPACES}"
for keyspace in "${KEYSPACE_ARRAY[@]}"; do
    log_info "  Flushing keyspace: ${keyspace}"
    if [[ "${DRY_RUN}" != "true" ]]; then
        kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- \
            nodetool flush "${keyspace}" || log_warn "Failed to flush ${keyspace}"
    fi
done

# =============================================================================
# 3. Create Snapshots
# =============================================================================
log_info "Creating snapshots..."

for keyspace in "${KEYSPACE_ARRAY[@]}"; do
    log_info "  Snapshotting keyspace: ${keyspace}"
    if [[ "${DRY_RUN}" != "true" ]]; then
        kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- \
            nodetool snapshot -t "${SNAPSHOT_NAME}" "${keyspace}" \
            || log_warn "Failed to snapshot ${keyspace}"
    fi
done

# =============================================================================
# 4. Export Schema
# =============================================================================
log_info "Exporting schema definitions..."

if [[ "${DRY_RUN}" != "true" ]]; then
    for keyspace in "${KEYSPACE_ARRAY[@]}"; do
        log_info "  Exporting schema for: ${keyspace}"
        kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- \
            cqlsh -e "DESCRIBE KEYSPACE ${keyspace}" \
            > "${BACKUP_DIR}/schema-${keyspace}.cql" 2>/dev/null || true
    done
    
    # Export all schemas
    kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- \
        cqlsh -e "DESCRIBE SCHEMA" \
        > "${BACKUP_DIR}/schema-full.cql" 2>/dev/null || true
fi

# =============================================================================
# 5. Copy Snapshot Files
# =============================================================================
log_info "Copying snapshot files..."

if [[ "${DRY_RUN}" != "true" ]]; then
    mkdir -p "${BACKUP_DIR}/snapshots"
    
    for pod in ${CASSANDRA_PODS}; do
        log_info "  Copying from pod: ${pod}"
        
        # Find snapshot directories
        SNAPSHOT_PATHS=$(kubectl exec -n "${NAMESPACE}" "${pod}" -- \
            find /var/lib/cassandra/data -name "${SNAPSHOT_NAME}" -type d 2>/dev/null || echo "")
        
        if [[ -n "${SNAPSHOT_PATHS}" ]]; then
            mkdir -p "${BACKUP_DIR}/snapshots/${pod}"
            
            # Create a tar of all snapshots on the pod
            kubectl exec -n "${NAMESPACE}" "${pod}" -- \
                tar -czf /tmp/snapshot-${SNAPSHOT_NAME}.tar.gz \
                -C /var/lib/cassandra/data \
                $(kubectl exec -n "${NAMESPACE}" "${pod}" -- \
                    find /var/lib/cassandra/data -name "${SNAPSHOT_NAME}" -type d \
                    | sed 's|/var/lib/cassandra/data/||g' | tr '\n' ' ') \
                2>/dev/null || true
            
            # Copy tar to local
            kubectl cp "${NAMESPACE}/${pod}:/tmp/snapshot-${SNAPSHOT_NAME}.tar.gz" \
                "${BACKUP_DIR}/snapshots/${pod}/snapshot.tar.gz" 2>/dev/null || true
        fi
    done
fi

# =============================================================================
# 6. Backup Ring Information
# =============================================================================
log_info "Backing up ring information..."

if [[ "${DRY_RUN}" != "true" ]]; then
    kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- nodetool ring \
        > "${BACKUP_DIR}/ring.txt" 2>/dev/null || true
    
    kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- nodetool describering capsule \
        > "${BACKUP_DIR}/describering-capsule.txt" 2>/dev/null || true
fi

# =============================================================================
# 7. Create Metadata
# =============================================================================
log_info "Creating backup metadata..."

if [[ "${DRY_RUN}" != "true" ]]; then
    cat > "${BACKUP_DIR}/metadata.json" << EOF
{
    "backup_type": "cassandra",
    "backup_name": "${BACKUP_NAME}",
    "snapshot_name": "${SNAPSHOT_NAME}",
    "timestamp": "${TIMESTAMP}",
    "namespace": "${NAMESPACE}",
    "keyspaces": "$(echo ${KEYSPACES} | tr ',' ' ')",
    "pods": "${CASSANDRA_PODS}",
    "created_by": "$(whoami)",
    "hostname": "$(hostname)"
}
EOF
fi

# =============================================================================
# 8. Create Archive
# =============================================================================
log_info "Creating backup archive..."

if [[ "${DRY_RUN}" != "true" ]]; then
    ARCHIVE_PATH="${OUTPUT_DIR}/${BACKUP_NAME}.tar.gz"
    tar -czf "${ARCHIVE_PATH}" -C "${OUTPUT_DIR}" "${BACKUP_NAME}"
    log_info "Archive created: ${ARCHIVE_PATH}"
    
    sha256sum "${ARCHIVE_PATH}" > "${ARCHIVE_PATH}.sha256"
fi

# =============================================================================
# 9. Clean Up Snapshots (optional)
# =============================================================================
log_info "Cleaning up remote snapshots..."

for keyspace in "${KEYSPACE_ARRAY[@]}"; do
    if [[ "${DRY_RUN}" != "true" ]]; then
        kubectl exec -n "${NAMESPACE}" "${CASSANDRA_POD}" -- \
            nodetool clearsnapshot -t "${SNAPSHOT_NAME}" "${keyspace}" \
            || log_warn "Failed to clear snapshot for ${keyspace}"
    fi
done

# =============================================================================
# 10. Upload to S3 (if configured)
# =============================================================================
if [[ -n "${S3_BUCKET}" ]]; then
    log_info "Uploading to S3: ${S3_BUCKET}"
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        aws s3 cp "${ARCHIVE_PATH}" "s3://${S3_BUCKET}/cassandra/${BACKUP_NAME}.tar.gz"
        aws s3 cp "${ARCHIVE_PATH}.sha256" "s3://${S3_BUCKET}/cassandra/${BACKUP_NAME}.tar.gz.sha256"
        log_info "Upload complete"
    fi
fi

# =============================================================================
# Summary
# =============================================================================
log_info "Cassandra backup complete!"
if [[ "${DRY_RUN}" != "true" ]]; then
    log_info "Backup location: ${BACKUP_DIR}"
    log_info "Archive: ${ARCHIVE_PATH}"
    du -sh "${ARCHIVE_PATH}" 2>/dev/null || true
fi

