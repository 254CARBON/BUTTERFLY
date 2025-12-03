#!/bin/bash
# =============================================================================
# BUTTERFLY PostgreSQL Backup Script
# =============================================================================
# Creates logical backups of PostgreSQL databases with pg_dump.
# Supports WAL archiving for point-in-time recovery.
#
# Usage:
#   ./backup-postgres.sh [OPTIONS]
#
# Options:
#   --namespace NS      Kubernetes namespace (default: butterfly)
#   --databases DBS     Comma-separated databases (default: perception,odyssey)
#   --output-dir DIR    Output directory (default: /tmp/butterfly-backup/postgres)
#   --s3-bucket BUCKET  S3 bucket for upload (optional)
#   --format FMT        Output format: custom, plain, directory (default: custom)
#   --compress          Enable compression (default: true)
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
DATABASES="${DATABASES:-perception,odyssey}"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/butterfly-backup/postgres}"
S3_BUCKET="${S3_BUCKET:-}"
FORMAT="${FORMAT:-custom}"
COMPRESS="${COMPRESS:-true}"
DRY_RUN="${DRY_RUN:-false}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_NAME="postgres-backup-${TIMESTAMP}"

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
        --databases) DATABASES="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --s3-bucket) S3_BUCKET="$2"; shift 2 ;;
        --format) FORMAT="$2"; shift 2 ;;
        --compress) COMPRESS="true"; shift ;;
        --no-compress) COMPRESS="false"; shift ;;
        --dry-run) DRY_RUN="true"; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 1 ;;
    esac
done

# Validate prerequisites
command -v kubectl &>/dev/null || { log_error "kubectl not found"; exit 1; }

# Get PostgreSQL pod
PG_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=postgres,role=primary -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
if [[ -z "${PG_POD}" ]]; then
    # Try without role label
    PG_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
fi

if [[ -z "${PG_POD}" ]]; then
    log_error "No PostgreSQL pod found in namespace ${NAMESPACE}"
    exit 1
fi

log_info "PostgreSQL pod: ${PG_POD}"
log_info "Databases: ${DATABASES}"
log_info "Format: ${FORMAT}"

if [[ "${DRY_RUN}" == "true" ]]; then
    log_warn "DRY RUN MODE - No changes will be made"
fi

# Create output directory
BACKUP_DIR="${OUTPUT_DIR}/${BACKUP_NAME}"
if [[ "${DRY_RUN}" != "true" ]]; then
    mkdir -p "${BACKUP_DIR}"
fi

# =============================================================================
# 1. Check Replication Status
# =============================================================================
log_info "Checking PostgreSQL replication status..."

if [[ "${DRY_RUN}" != "true" ]]; then
    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
        psql -U postgres -c "SELECT * FROM pg_stat_replication;" \
        > "${BACKUP_DIR}/replication-status.txt" 2>/dev/null || true
    
    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
        psql -U postgres -c "SELECT pg_current_wal_lsn();" \
        > "${BACKUP_DIR}/wal-position.txt" 2>/dev/null || true
fi

# =============================================================================
# 2. Backup Global Objects (roles, tablespaces)
# =============================================================================
log_info "Backing up global objects..."

if [[ "${DRY_RUN}" != "true" ]]; then
    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
        pg_dumpall -U postgres --globals-only \
        > "${BACKUP_DIR}/globals.sql" 2>/dev/null || true
fi

# =============================================================================
# 3. Backup Each Database
# =============================================================================
log_info "Backing up databases..."

IFS=',' read -ra DB_ARRAY <<< "${DATABASES}"
for db in "${DB_ARRAY[@]}"; do
    log_info "  Backing up database: ${db}"
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        case "${FORMAT}" in
            custom)
                DUMP_FILE="${BACKUP_DIR}/${db}.dump"
                kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
                    pg_dump -U postgres -Fc -f "/tmp/${db}.dump" "${db}" 2>/dev/null || {
                        log_warn "Failed to dump ${db}"
                        continue
                    }
                kubectl cp "${NAMESPACE}/${PG_POD}:/tmp/${db}.dump" "${DUMP_FILE}"
                kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- rm -f "/tmp/${db}.dump"
                ;;
            plain)
                DUMP_FILE="${BACKUP_DIR}/${db}.sql"
                if [[ "${COMPRESS}" == "true" ]]; then
                    DUMP_FILE="${DUMP_FILE}.gz"
                    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
                        bash -c "pg_dump -U postgres ${db} | gzip > /tmp/${db}.sql.gz" 2>/dev/null || {
                            log_warn "Failed to dump ${db}"
                            continue
                        }
                    kubectl cp "${NAMESPACE}/${PG_POD}:/tmp/${db}.sql.gz" "${DUMP_FILE}"
                    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- rm -f "/tmp/${db}.sql.gz"
                else
                    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
                        pg_dump -U postgres -f "/tmp/${db}.sql" "${db}" 2>/dev/null || {
                            log_warn "Failed to dump ${db}"
                            continue
                        }
                    kubectl cp "${NAMESPACE}/${PG_POD}:/tmp/${db}.sql" "${DUMP_FILE}"
                    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- rm -f "/tmp/${db}.sql"
                fi
                ;;
            directory)
                kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
                    pg_dump -U postgres -Fd -j 4 -f "/tmp/${db}_dir" "${db}" 2>/dev/null || {
                        log_warn "Failed to dump ${db}"
                        continue
                    }
                kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
                    tar -czf "/tmp/${db}_dir.tar.gz" -C /tmp "${db}_dir"
                kubectl cp "${NAMESPACE}/${PG_POD}:/tmp/${db}_dir.tar.gz" "${BACKUP_DIR}/${db}_dir.tar.gz"
                kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- rm -rf "/tmp/${db}_dir" "/tmp/${db}_dir.tar.gz"
                ;;
        esac
    fi
done

# =============================================================================
# 4. Export Schema Only (for quick reference)
# =============================================================================
log_info "Exporting schema definitions..."

if [[ "${DRY_RUN}" != "true" ]]; then
    for db in "${DB_ARRAY[@]}"; do
        kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
            pg_dump -U postgres --schema-only "${db}" \
            > "${BACKUP_DIR}/${db}-schema.sql" 2>/dev/null || true
    done
fi

# =============================================================================
# 5. Backup pg_hba.conf and postgresql.conf
# =============================================================================
log_info "Backing up configuration files..."

if [[ "${DRY_RUN}" != "true" ]]; then
    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
        cat /var/lib/postgresql/data/pg_hba.conf \
        > "${BACKUP_DIR}/pg_hba.conf" 2>/dev/null || true
    
    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
        cat /var/lib/postgresql/data/postgresql.conf \
        > "${BACKUP_DIR}/postgresql.conf" 2>/dev/null || true
fi

# =============================================================================
# 6. Record WAL Position for PITR
# =============================================================================
log_info "Recording WAL position for PITR..."

if [[ "${DRY_RUN}" != "true" ]]; then
    WAL_LSN=$(kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
        psql -U postgres -t -c "SELECT pg_current_wal_lsn();" 2>/dev/null | tr -d ' ')
    
    echo "${WAL_LSN}" > "${BACKUP_DIR}/wal_lsn.txt"
fi

# =============================================================================
# 7. Create Metadata
# =============================================================================
log_info "Creating backup metadata..."

if [[ "${DRY_RUN}" != "true" ]]; then
    cat > "${BACKUP_DIR}/metadata.json" << EOF
{
    "backup_type": "postgres",
    "backup_name": "${BACKUP_NAME}",
    "timestamp": "${TIMESTAMP}",
    "namespace": "${NAMESPACE}",
    "databases": "$(echo ${DATABASES} | tr ',' ' ')",
    "format": "${FORMAT}",
    "compressed": ${COMPRESS},
    "pod": "${PG_POD}",
    "wal_lsn": "${WAL_LSN:-unknown}",
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
# 9. Upload to S3 (if configured)
# =============================================================================
if [[ -n "${S3_BUCKET}" ]]; then
    log_info "Uploading to S3: ${S3_BUCKET}"
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        aws s3 cp "${ARCHIVE_PATH}" "s3://${S3_BUCKET}/postgres/${BACKUP_NAME}.tar.gz"
        aws s3 cp "${ARCHIVE_PATH}.sha256" "s3://${S3_BUCKET}/postgres/${BACKUP_NAME}.tar.gz.sha256"
        log_info "Upload complete"
    fi
fi

# =============================================================================
# Summary
# =============================================================================
log_info "PostgreSQL backup complete!"
if [[ "${DRY_RUN}" != "true" ]]; then
    log_info "Backup location: ${BACKUP_DIR}"
    log_info "Archive: ${ARCHIVE_PATH}"
    log_info "WAL LSN: ${WAL_LSN:-unknown}"
    du -sh "${ARCHIVE_PATH}" 2>/dev/null || true
fi

