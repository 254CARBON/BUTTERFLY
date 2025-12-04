#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/butterfly-e2e/docker-compose.full.yml"
SCENARIO_FILE="$ROOT_DIR/butterfly-e2e/scenarios/governance-decision-loop.json"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

command -v jq >/dev/null 2>&1 || { log_error "jq is required to parse responses"; exit 1; }
command -v curl >/dev/null 2>&1 || { log_error "curl is required"; exit 1; }
command -v docker >/dev/null 2>&1 || { log_error "docker is required to start the dev stack"; exit 1; }

TENANT_ID="${TENANT_ID:-e2e-test}"
CORRELATION_ID="${CORRELATION_ID:-gov-loop-$(date +%s)}"
KEEP_STACK=0
SKIP_STACK=0
RUN_GOLDEN_PATH=0
ALLOW_PARTIAL=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-stack)
      KEEP_STACK=1
      shift
      ;;
    --skip-stack)
      SKIP_STACK=1
      shift
      ;;
    --golden-path)
      RUN_GOLDEN_PATH=1
      shift
      ;;
    --correlation-id)
      CORRELATION_ID="$2"
      shift 2
      ;;
    --allow-partial)
      ALLOW_PARTIAL=1
      shift
      ;;
    -h|--help)
      cat <<EOF
Usage: $0 [options]

Runs the governance decision loop scenario (PERCEPTION → CAPSULE → ODYSSEY → NEXUS → PLATO → SYNAPSE)
after starting the minimal docker-compose stack defined in butterfly-e2e/docker-compose.full.yml.

Options:
  --keep-stack          Do not tear down docker-compose services after the run
  --skip-stack          Assume services are already running (no docker-compose up/down)
  --golden-path         Run butterfly-e2e/run-golden-path.sh --strategic before the governance loop
  --allow-partial       Do not fail when a service is unavailable (steps for that service are skipped)
  --correlation-id ID   Override the default correlation id
  -h, --help            Show this help message
EOF
      exit 0
      ;;
    *)
      log_error "Unknown option: $1"
      exit 1
      ;;
  esac
done

PERCEPTION_URL="${PERCEPTION_URL:-http://localhost:8081}"
CAPSULE_URL="${CAPSULE_URL:-http://localhost:8082}"
ODYSSEY_URL="${ODYSSEY_URL:-http://localhost:8083}"
PLATO_URL="${PLATO_URL:-http://localhost:8084}"
NEXUS_URL="${NEXUS_URL:-http://localhost:8085}"
SYNAPSE_URL="${SYNAPSE_URL:-http://localhost:8086}"

STACK_STARTED=0
PLAN_ID=""
SPEC_ID=""
POLICY_ID=""
declare -A SERVICE_READY=()

cleanup() {
  set +e
  if [[ -n "$PLAN_ID" && "${SERVICE_READY[plato]:-1}" != "0" ]]; then
    log_info "Cleaning up plan $PLAN_ID"
    curl -s -o /dev/null -w "" -X DELETE -H "X-Tenant-ID: $TENANT_ID" "$PLATO_URL/api/v1/plans/$PLAN_ID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$POLICY_ID" && "${SERVICE_READY[plato]:-1}" != "0" ]]; then
    curl -s -o /dev/null -w "" -X DELETE -H "X-Tenant-ID: $TENANT_ID" "$PLATO_URL/api/v1/policies/$POLICY_ID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$SPEC_ID" && "${SERVICE_READY[plato]:-1}" != "0" ]]; then
    curl -s -o /dev/null -w "" -X DELETE -H "X-Tenant-ID: $TENANT_ID" "$PLATO_URL/api/v1/specs/$SPEC_ID" >/dev/null 2>&1 || true
  fi
  if [[ $STACK_STARTED -eq 1 && $KEEP_STACK -eq 0 ]]; then
    log_info "Tearing down docker-compose stack"
    docker compose -f "$COMPOSE_FILE" down -v >/dev/null 2>&1 || true
  elif [[ $STACK_STARTED -eq 1 ]]; then
    log_warn "Keeping docker-compose stack running per --keep-stack"
  fi
}
trap cleanup EXIT

start_stack() {
  if [[ $SKIP_STACK -eq 1 ]]; then
    log_info "Skipping docker-compose stack startup (per --skip-stack)"
    return
  fi
  log_step "Starting minimal governance stack (Kafka + Redis + Nexus/Odyssey mocks)"
  docker compose -f "$COMPOSE_FILE" --profile odyssey --profile nexus --profile mocks up -d kafka redis odyssey-app nexus-app wiremock capsule-mock >/dev/null
  STACK_STARTED=1
  log_info "Waiting for Kafka bootstrap..."
  local retries=20
  until docker compose -f "$COMPOSE_FILE" exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; do
    retries=$((retries - 1))
    if [[ $retries -le 0 ]]; then
      log_error "Kafka did not become healthy in time"
      exit 1
    fi
    sleep 2
  done
}

call_service() {
  local service="$1"
  local method="$2"
  local endpoint="$3"
  local body="${4:-}"
  local expected="${5:-200}"
  local optional="${6:-0}"

  local base_url
  case "$service" in
    perception) base_url="$PERCEPTION_URL" ;;
    capsule) base_url="$CAPSULE_URL" ;;
    odyssey) base_url="$ODYSSEY_URL" ;;
    nexus) base_url="$NEXUS_URL" ;;
    plato) base_url="$PLATO_URL" ;;
    synapse) base_url="$SYNAPSE_URL" ;;
    *) base_url="http://localhost:8080" ;;
  esac

  local url="${base_url}${endpoint}"
  if [[ "${SERVICE_READY[$service]:-1}" == "0" && $ALLOW_PARTIAL -eq 1 ]]; then
    log_warn "    Skipping $service call (${endpoint}) because service is unavailable (partial mode)"
    return 0
  fi
  local tmp
  tmp=$(mktemp)

  local headers=(-H "X-Tenant-ID: $TENANT_ID" -H "X-Correlation-ID: $CORRELATION_ID")
  if [[ "$method" != "GET" && "$method" != "DELETE" ]]; then
    headers+=(-H "Content-Type: application/json")
  elif [[ -n "$body" ]]; then
    headers+=(-H "Content-Type: application/json")
  fi

  local curl_args=(-s -S -o "$tmp" -w "%{http_code}" -X "$method")
  curl_args+=("${headers[@]}")
  if [[ -n "$body" ]]; then
    curl_args+=(-d "$body")
  fi
  curl_args+=("$url")

  local status
  status=$(curl "${curl_args[@]}" 2>/dev/null || echo "000")

  local ok=1
  IFS=',' read -ra allowed <<< "$expected"
  local match=0
  for code in "${allowed[@]}"; do
    if [[ "$status" == "$code" ]]; then
      match=1
      break
    fi
  done
  if [[ $match -eq 0 && "$expected" == "any" ]]; then
    match=1
  fi

  if [[ $match -eq 1 ]]; then
    log_info "    ✓ $method $endpoint -> $status"
    cat "$tmp"
  else
    local msg="    ✗ $method $endpoint -> $status (expected $expected)"
    if [[ "$optional" == "1" ]]; then
      log_warn "$msg (optional)"
      cat "$tmp"
    else
      log_error "$msg"
      cat "$tmp" >&2
      rm -f "$tmp"
      return 1
    fi
  fi
  rm -f "$tmp"
}

health_check() {
  for svc in perception capsule odyssey nexus plato synapse; do
    log_step "Checking $svc health"
    if call_service "$svc" GET "/actuator/health" "" "200" 0 >/dev/null; then
      SERVICE_READY[$svc]=1
    else
      SERVICE_READY[$svc]=0
      if [[ $ALLOW_PARTIAL -eq 1 ]]; then
        log_warn "$svc is unavailable; related steps will be skipped"
      else
        log_error "$svc health endpoint is unavailable. Start the service or re-run with --allow-partial."
        exit 1
      fi
    fi
  done
}

service_available() {
  local svc="$1"
  [[ "${SERVICE_READY[$svc]:-0}" == "1" ]]
}

skip_step() {
  local label="$1"
  local svc="$2"
  log_warn "Skipping ${label} because ${svc^^} is unavailable (see --allow-partial docs)."
}

run_golden_path() {
  log_step "Running strategic golden path harness"
  pushd "$ROOT_DIR/butterfly-e2e" >/dev/null
  ./run-golden-path.sh --strategic --skip-build
  popd >/dev/null
}

run_governance_loop() {
  log_step "Running governance decision loop scenario"
  log_info "Scenario definition: $SCENARIO_FILE"

  local iso_ts
  iso_ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  local rim_node="rim:gov:loop:${CORRELATION_ID}"

  if service_available perception; then
    log_step "Perception ingest"
    local perception_payload
    perception_payload=$(cat <<EOF
{
  "signalType": "GOVERNANCE_EVIDENCE",
  "source": "governance-loop-cli",
  "rimNodeId": "$rim_node",
  "payload": {
    "evidenceId": "${CORRELATION_ID}",
    "metric": "policy-compliance",
    "value": 0.87,
    "confidence": 0.92,
    "timestamp": "$iso_ts"
  },
  "metadata": {
    "tags": ["e2e","governance"],
    "priority": "HIGH"
  }
}
EOF
)
    local perception_response
    perception_response="$(call_service perception POST "/api/v1/signals" "$perception_payload" "201,202")"
    local rim_node_id
    rim_node_id="$(echo "$perception_response" | jq -r '.rimNodeId // empty')"
    if [[ -z "$rim_node_id" ]]; then
      log_warn "Perception response did not include rimNodeId, falling back to $rim_node"
      rim_node_id="$rim_node"
    fi
  else
    skip_step "perception ingest" "perception"
    rim_node_id="$rim_node"
  fi

  if service_available perception; then
    log_step "Verifying PERCEPTION state"
    call_service perception GET "/api/v1/state/${rim_node_id}" "" "200"
  else
    skip_step "perception state verification" "perception"
  fi

  if service_available capsule; then
    log_step "Verifying CAPSULE persisted records"
    call_service capsule GET "/api/v1/capsules?scopeId=${rim_node_id}&limit=1" "" "200"
  else
    skip_step "capsule verification" "capsule"
  fi

  if service_available odyssey; then
    log_step "Fetching ODYSSEY context"
    call_service odyssey GET "/api/v1/entities?entityId=${rim_node_id}" "" "200,404" 1 >/dev/null
  else
    skip_step "odyssey context" "odyssey"
  fi

  if service_available nexus; then
    log_step "Requesting NEXUS temporal slice"
    local nexus_payload
    nexus_payload=$(cat <<EOF
{
  "entityId": "$rim_node_id",
  "timeframe": { "lookback": "PT45M", "lookahead": "PT15M" },
  "includeSources": ["perception", "capsule", "odyssey"],
  "requireGovernanceView": true
}
EOF
)
    call_service nexus POST "/api/v1/temporal/slice" "$nexus_payload" "200"
  else
    skip_step "nexus temporal slice" "nexus"
  fi

  if service_available plato; then
    log_step "Creating PLATO governance policy"
    local policy_payload
    policy_payload=$(cat <<EOF
{
  "namespace": "e2e",
  "localId": "gov-loop-policy-${CORRELATION_ID}",
  "policyType": "GOVERNANCE_LOOP",
  "name": "Governance Loop Policy",
  "description": "Ensures evidence is fresh/confident",
  "rules": [
    { "ruleId": "confidence", "ruleType": "THRESHOLD", "condition": "confidence >= 0.75", "action": "ALLOW" },
    { "ruleId": "freshness", "ruleType": "THRESHOLD", "condition": "age_seconds <= 1800", "action": "WARN" }
  ],
  "scope": "entity"
}
EOF
)
    local policy_resp
    policy_resp="$(call_service plato POST "/api/v1/policies" "$policy_payload" "201")"
    POLICY_ID="$(echo "$policy_resp" | jq -r '.id // empty')"
  else
    skip_step "plato policy creation" "plato"
  fi

  if service_available plato; then
    log_step "Registering PLATO spec"
    local spec_payload
    spec_payload=$(cat <<EOF
{
  "namespace": "e2e",
  "localId": "gov-loop-spec-${CORRELATION_ID}",
  "specType": "DATA_CONTRACT",
  "content": {
    "schema": {
      "type": "object",
      "properties": {
        "evidenceId": { "type": "string" },
        "metric": { "type": "string" },
        "value": { "type": "number" },
        "confidence": { "type": "number" }
      },
      "required": ["evidenceId","metric","value","confidence"]
    }
  },
  "description": "Governance loop payload contract"
}
EOF
)
    local spec_resp
    spec_resp="$(call_service plato POST "/api/v1/specs" "$spec_payload" "201")"
    SPEC_ID="$(echo "$spec_resp" | jq -r '.id // empty')"
  else
    skip_step "plato spec registration" "plato"
  fi

  if service_available plato; then
    log_step "Creating PLATO plan"
    local plan_payload
    plan_payload=$(cat <<EOF
{
  "namespace": "e2e",
  "localId": "gov-loop-plan-${CORRELATION_ID}",
  "planType": "GOVERNANCE_LOOP",
  "policyId": "$POLICY_ID",
  "steps": [
    {
      "name": "validate-evidence",
      "action": {
        "type": "VALIDATE",
        "parameters": {
          "specId": "$SPEC_ID",
          "rimNodeId": "$rim_node_id",
          "mode": "DRY_RUN"
        }
      }
    },
    {
      "name": "execute-synapse",
      "dependsOn": ["validate-evidence"],
      "action": {
        "type": "EXECUTE",
        "synapseAction": {
          "toolId": "synapse-governance-tester",
          "actionType": "QUERY",
          "parameters": {
            "targetId": "$rim_node_id",
            "operation": "stabilize"
          }
        }
      }
    }
  ],
  "description": "Governance decision loop validation plan"
}
EOF
)
    local plan_resp
    plan_resp="$(call_service plato POST "/api/v1/plans" "$plan_payload" "201")"
    PLAN_ID="$(echo "$plan_resp" | jq -r '.id // empty')"
  else
    skip_step "plato plan creation" "plato"
  fi

  if service_available plato && [[ -n "$PLAN_ID" ]]; then
    log_step "Executing plan via PLATO (invokes SYNAPSE)"
    call_service plato POST "/api/v1/plans/${PLAN_ID}/execute" '{"mode":"DRY_RUN"}' "202"
  else
    skip_step "plato plan execution" "plato"
  fi

  if service_available synapse && [[ -n "$PLAN_ID" ]]; then
    log_info "Waiting for SYNAPSE processing..."
    sleep 6
  fi

  if service_available plato && [[ -n "$PLAN_ID" ]]; then
    log_step "Checking plan status"
    call_service plato GET "/api/v1/plans/${PLAN_ID}" "" "200"
  fi

  if service_available nexus && [[ -n "$PLAN_ID" ]]; then
    log_step "Sending learning signal back to NEXUS"
    local learning_payload
    learning_payload=$(cat <<EOF
{
  "signalType": "GOVERNANCE_LOOP",
  "entityId": "$rim_node_id",
  "planId": "$PLAN_ID",
  "outcome": "SUCCESS",
  "metrics": {
    "executionLatencyMs": 400
  }
}
EOF
)
    call_service nexus POST "/api/v1/learning/signal" "$learning_payload" "200,202" 1 >/dev/null
  else
    skip_step "nexus learning signal" "nexus"
  fi

  if service_available synapse && [[ -n "$PLAN_ID" ]]; then
    log_step "Verifying SYNAPSE action (optional)"
    call_service synapse GET "/api/v1/actions?planId=${PLAN_ID}&limit=1" "" "200,404" 1 >/dev/null
  else
    skip_step "synapse action verification" "synapse"
  fi

  log_info "Governance loop completed successfully for $rim_node_id (partial mode: $ALLOW_PARTIAL)"
}

main() {
  log_info "Correlation ID: $CORRELATION_ID"
  start_stack
  if [[ $RUN_GOLDEN_PATH -eq 1 ]]; then
    run_golden_path
  fi
  health_check
  run_governance_loop
  log_info "Run complete. Inspect services with correlation id $CORRELATION_ID for traceability."
}

main "$@"
