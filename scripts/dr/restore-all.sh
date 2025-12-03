#!/bin/bash
# =============================================================================
# BUTTERFLY Orchestrated Restore Script
# =============================================================================
# Performs a coordinated restore of all BUTTERFLY data stores.
# Handles restore order and service coordination.
#
# Usage:
#   ./restore-all.sh [OPTIONS]
#
# Options:
#   --namespace NS          Kubernetes namespace (default: butterfly)
#   --backup-dir DIR        Backup directory containing all backups
#   --s3-bucket BUCKET      S3 bucket to download from (alternative to --backup-dir)
#   --backup-timestamp TS   Timestamp of backup to restore (e.g., 20251203-143000)
#   --components COMPS      Comma-separated components (default: all)
#                           Options: kafka,cassandra,postgres,redis,ignite
#   --skip-validation       Skip post-restore validation
#   --dry-run               Show what would be done without executing
#   --force                 Skip confirmation prompts
#   --help                  Show this help message
#
# IMPORTANT: This script will stop services during restore!
#
# Requirements:
#   - kubectl configured with cluster access
#   - aws CLI configured (if using S3)
#   - Sufficient permissions to scale deployments
# =============================================================================

set -euo pipefail

# Configuration defaults
NAMESPACE="${NAMESPACE:-butterfly}"
BACKUP_DIR="${BACKUP_DIR:-}"
S3_BUCKET="${S3_BUCKET:-}"
BACKUP_TIMESTAMP="${BACKUP_TIMESTAMP:-}"
COMPONENTS="${COMPONENTS:-all}"
SKIP_VALIDATION="${SKIP_VALIDATION:-false}"
DRY_RUN="${DRY_RUN:-false}"
FORCE="${FORCE:-false}"
WORK_DIR="/tmp/butterfly-restore-$(date +%Y%m%d-%H%M%S)"

# Colors for output
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
while [[ $# -gt 0 ]]; do
    case $1 in
        --namespace) NAMESPACE="$2"; shift 2 ;;
        --backup-dir) BACKUP_DIR="$2"; shift 2 ;;
        --s3-bucket) S3_BUCKET="$2"; shift 2 ;;
        --backup-timestamp) BACKUP_TIMESTAMP="$2"; shift 2 ;;
        --components) COMPONENTS="$2"; shift 2 ;;
        --skip-validation) SKIP_VALIDATION="true"; shift ;;
        --dry-run) DRY_RUN="true"; shift ;;
        --force) FORCE="true"; shift ;;
        --help) show_help ;;
        *) log_error "Unknown option: $1"; exit 1 ;;
    esac
done

# Validate inputs
if [[ -z "${BACKUP_DIR}" && -z "${S3_BUCKET}" ]]; then
    log_error "Must specify either --backup-dir or --s3-bucket"
    exit 1
fi

if [[ -n "${S3_BUCKET}" && -z "${BACKUP_TIMESTAMP}" ]]; then
    log_error "Must specify --backup-timestamp when using --s3-bucket"
    exit 1
fi

# Validate prerequisites
command -v kubectl &>/dev/null || { log_error "kubectl not found"; exit 1; }
if [[ -n "${S3_BUCKET}" ]]; then
    command -v aws &>/dev/null || { log_error "aws CLI not found"; exit 1; }
fi

# Confirmation
if [[ "${DRY_RUN}" == "true" ]]; then
    log_warn "DRY RUN MODE - No changes will be made"
elif [[ "${FORCE}" != "true" ]]; then
    echo ""
    log_warn "WARNING: This will restore data and restart services!"
    log_warn "Namespace: ${NAMESPACE}"
    log_warn "Components: ${COMPONENTS}"
    echo ""
    read -p "Are you sure you want to continue? (yes/no): " CONFIRM
    if [[ "${CONFIRM}" != "yes" ]]; then
        log_info "Restore cancelled"
        exit 0
    fi
fi

# Create work directory
mkdir -p "${WORK_DIR}"
log_info "Work directory: ${WORK_DIR}"

# =============================================================================
# STEP 0: Download Backups from S3 (if needed)
# =============================================================================
if [[ -n "${S3_BUCKET}" ]]; then
    log_step "Step 0: Downloading backups from S3..."
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        BACKUP_DIR="${WORK_DIR}/backups"
        mkdir -p "${BACKUP_DIR}"
        
        # Download all component backups
        for component in kafka cassandra postgres redis; do
            aws s3 cp "s3://${S3_BUCKET}/${component}/${component}-backup-${BACKUP_TIMESTAMP}.tar.gz" \
                "${BACKUP_DIR}/" 2>/dev/null || log_warn "No ${component} backup found"
        done
        
        # Extract archives
        for archive in "${BACKUP_DIR}"/*.tar.gz; do
            if [[ -f "${archive}" ]]; then
                tar -xzf "${archive}" -C "${BACKUP_DIR}"
            fi
        done
    fi
fi

# Determine which components to restore
if [[ "${COMPONENTS}" == "all" ]]; then
    RESTORE_COMPONENTS="cassandra postgres redis kafka"
else
    RESTORE_COMPONENTS="${COMPONENTS//,/ }"
fi

# =============================================================================
# STEP 1: Stop Application Services
# =============================================================================
log_step "Step 1: Stopping application services..."

SERVICES="nexus plato odyssey perception capsule"

if [[ "${DRY_RUN}" != "true" ]]; then
    for svc in ${SERVICES}; do
        log_info "  Scaling down ${svc}..."
        kubectl scale deployment/${svc} --replicas=0 -n "${NAMESPACE}" 2>/dev/null || true
    done
    
    log_info "Waiting for pods to terminate..."
    kubectl wait --for=delete pod -l 'app.kubernetes.io/part-of=butterfly' \
        -n "${NAMESPACE}" --timeout=120s 2>/dev/null || true
fi

# =============================================================================
# STEP 2: Restore Cassandra
# =============================================================================
if echo "${RESTORE_COMPONENTS}" | grep -q "cassandra"; then
    log_step "Step 2: Restoring Cassandra..."
    
    CASS_BACKUP="${BACKUP_DIR}/cassandra-backup-${BACKUP_TIMESTAMP}"
    if [[ -d "${CASS_BACKUP}" ]]; then
        log_info "Found Cassandra backup: ${CASS_BACKUP}"
        
        if [[ "${DRY_RUN}" != "true" ]]; then
            # Stop Cassandra
            kubectl scale statefulset/cassandra --replicas=0 -n "${NAMESPACE}" 2>/dev/null || true
            sleep 10
            
            # Start single node for restore
            kubectl scale statefulset/cassandra --replicas=1 -n "${NAMESPACE}"
            kubectl wait --for=condition=ready pod/cassandra-0 -n "${NAMESPACE}" --timeout=300s
            
            # Restore schema
            if [[ -f "${CASS_BACKUP}/schema-full.cql" ]]; then
                log_info "Restoring schema..."
                kubectl cp "${CASS_BACKUP}/schema-full.cql" "${NAMESPACE}/cassandra-0:/tmp/schema.cql"
                kubectl exec -n "${NAMESPACE}" cassandra-0 -- cqlsh -f /tmp/schema.cql || true
            fi
            
            # Restore snapshots
            if [[ -d "${CASS_BACKUP}/snapshots" ]]; then
                log_info "Restoring snapshot data..."
                for snapshot_tar in "${CASS_BACKUP}/snapshots"/*/*.tar.gz; do
                    if [[ -f "${snapshot_tar}" ]]; then
                        kubectl cp "${snapshot_tar}" "${NAMESPACE}/cassandra-0:/tmp/snapshot.tar.gz"
                        kubectl exec -n "${NAMESPACE}" cassandra-0 -- \
                            tar -xzf /tmp/snapshot.tar.gz -C /var/lib/cassandra/data || true
                    fi
                done
            fi
            
            # Scale to full cluster
            kubectl scale statefulset/cassandra --replicas=3 -n "${NAMESPACE}"
        fi
    else
        log_warn "Cassandra backup not found at ${CASS_BACKUP}"
    fi
fi

# =============================================================================
# STEP 3: Restore PostgreSQL
# =============================================================================
if echo "${RESTORE_COMPONENTS}" | grep -q "postgres"; then
    log_step "Step 3: Restoring PostgreSQL..."
    
    PG_BACKUP="${BACKUP_DIR}/postgres-backup-${BACKUP_TIMESTAMP}"
    if [[ -d "${PG_BACKUP}" ]]; then
        log_info "Found PostgreSQL backup: ${PG_BACKUP}"
        
        if [[ "${DRY_RUN}" != "true" ]]; then
            PG_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=postgres -o jsonpath='{.items[0].metadata.name}')
            
            # Restore globals
            if [[ -f "${PG_BACKUP}/globals.sql" ]]; then
                log_info "Restoring global objects..."
                kubectl cp "${PG_BACKUP}/globals.sql" "${NAMESPACE}/${PG_POD}:/tmp/globals.sql"
                kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
                    psql -U postgres -f /tmp/globals.sql 2>/dev/null || true
            fi
            
            # Restore each database
            for dump_file in "${PG_BACKUP}"/*.dump; do
                if [[ -f "${dump_file}" ]]; then
                    db_name=$(basename "${dump_file}" .dump)
                    log_info "Restoring database: ${db_name}"
                    kubectl cp "${dump_file}" "${NAMESPACE}/${PG_POD}:/tmp/${db_name}.dump"
                    kubectl exec -n "${NAMESPACE}" "${PG_POD}" -- \
                        pg_restore -U postgres -d "${db_name}" -c /tmp/${db_name}.dump 2>/dev/null || true
                fi
            done
        fi
    else
        log_warn "PostgreSQL backup not found at ${PG_BACKUP}"
    fi
fi

# =============================================================================
# STEP 4: Restore Redis
# =============================================================================
if echo "${RESTORE_COMPONENTS}" | grep -q "redis"; then
    log_step "Step 4: Restoring Redis..."
    
    REDIS_BACKUP="${BACKUP_DIR}/redis-backup-${BACKUP_TIMESTAMP}"
    if [[ -d "${REDIS_BACKUP}" ]]; then
        log_info "Found Redis backup: ${REDIS_BACKUP}"
        
        if [[ "${DRY_RUN}" != "true" ]]; then
            # Stop Redis
            kubectl scale statefulset/redis --replicas=0 -n "${NAMESPACE}" 2>/dev/null || true
            sleep 5
            
            # Copy RDB file
            if [[ -f "${REDIS_BACKUP}/dump.rdb" ]]; then
                log_info "Restoring RDB file..."
                # Start single instance
                kubectl scale statefulset/redis --replicas=1 -n "${NAMESPACE}"
                kubectl wait --for=condition=ready pod/redis-0 -n "${NAMESPACE}" --timeout=120s
                
                kubectl cp "${REDIS_BACKUP}/dump.rdb" "${NAMESPACE}/redis-0:/data/dump.rdb"
                
                # Restart to load RDB
                kubectl delete pod redis-0 -n "${NAMESPACE}"
                kubectl wait --for=condition=ready pod/redis-0 -n "${NAMESPACE}" --timeout=120s
            fi
            
            # Scale to full cluster
            kubectl scale statefulset/redis --replicas=3 -n "${NAMESPACE}"
        fi
    else
        log_warn "Redis backup not found at ${REDIS_BACKUP}"
    fi
fi

# =============================================================================
# STEP 5: Restore Kafka (configs and offsets only)
# =============================================================================
if echo "${RESTORE_COMPONENTS}" | grep -q "kafka"; then
    log_step "Step 5: Restoring Kafka configuration..."
    
    KAFKA_BACKUP="${BACKUP_DIR}/kafka-backup-${BACKUP_TIMESTAMP}"
    if [[ -d "${KAFKA_BACKUP}" ]]; then
        log_info "Found Kafka backup: ${KAFKA_BACKUP}"
        
        if [[ "${DRY_RUN}" != "true" ]]; then
            KAFKA_POD=$(kubectl get pods -n "${NAMESPACE}" -l app=kafka -o jsonpath='{.items[0].metadata.name}')
            
            # Recreate topics if needed
            if [[ -f "${KAFKA_BACKUP}/topics.txt" ]]; then
                log_info "Verifying topics exist..."
                while IFS= read -r topic; do
                    if [[ -n "${topic}" ]]; then
                        kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                            kafka-topics.sh --bootstrap-server localhost:9092 \
                            --describe --topic "${topic}" &>/dev/null || {
                            log_info "  Creating topic: ${topic}"
                            kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
                                kafka-topics.sh --bootstrap-server localhost:9092 \
                                --create --topic "${topic}" \
                                --partitions 12 --replication-factor 3 2>/dev/null || true
                        }
                    fi
                done < "${KAFKA_BACKUP}/topics.txt"
            fi
            
            # Reset consumer offsets from backup
            log_info "Consumer group offsets should be reset manually if needed"
        fi
    else
        log_warn "Kafka backup not found at ${KAFKA_BACKUP}"
    fi
fi

# =============================================================================
# STEP 6: Restart Application Services
# =============================================================================
log_step "Step 6: Restarting application services..."

if [[ "${DRY_RUN}" != "true" ]]; then
    for svc in ${SERVICES}; do
        log_info "  Starting ${svc}..."
        kubectl scale deployment/${svc} --replicas=2 -n "${NAMESPACE}" 2>/dev/null || true
    done
    
    log_info "Waiting for services to be ready..."
    for svc in ${SERVICES}; do
        kubectl wait --for=condition=available deployment/${svc} \
            -n "${NAMESPACE}" --timeout=300s 2>/dev/null || log_warn "${svc} not ready"
    done
fi

# =============================================================================
# STEP 7: Validation
# =============================================================================
if [[ "${SKIP_VALIDATION}" != "true" ]]; then
    log_step "Step 7: Validating restore..."
    
    if [[ "${DRY_RUN}" != "true" ]]; then
        VALIDATION_PASSED=true
        
        # Check service health
        for svc in ${SERVICES}; do
            log_info "  Checking ${svc} health..."
            HEALTH=$(kubectl exec -n "${NAMESPACE}" -l app=${svc} -- \
                curl -s http://localhost:8080/actuator/health 2>/dev/null | head -1 || echo "")
            if echo "${HEALTH}" | grep -q '"status":"UP"'; then
                log_info "    ${svc}: HEALTHY"
            else
                log_warn "    ${svc}: NOT HEALTHY"
                VALIDATION_PASSED=false
            fi
        done
        
        if [[ "${VALIDATION_PASSED}" == "true" ]]; then
            log_info "All validations passed!"
        else
            log_warn "Some validations failed - manual verification recommended"
        fi
    fi
fi

# =============================================================================
# Summary
# =============================================================================
log_info "============================================="
log_info "Restore complete!"
log_info "============================================="
log_info "Namespace: ${NAMESPACE}"
log_info "Components: ${RESTORE_COMPONENTS}"
log_info "Backup timestamp: ${BACKUP_TIMESTAMP}"
log_info ""
log_info "Next steps:"
log_info "  1. Verify data integrity"
log_info "  2. Check application logs for errors"
log_info "  3. Run smoke tests"
log_info "  4. Monitor for consumer lag"

