#!/usr/bin/env bash
set -euo pipefail

PROMETHEUS_URL=${PROMETHEUS_URL:-"http://localhost:9090"}
PROMQL_TIMEOUT=${PROMQL_TIMEOUT:-"30s"}
REQUIRED_SERVICES=${REQUIRED_SERVICES:-"perception,capsule,odyssey,plato,nexus,synapse"}
SCENARIO_COMMAND=${SCENARIO_COMMAND:-"./butterfly-e2e/run-golden-path.sh --strategic --skip-build"}
RUN_SCENARIOS=${RUN_SCENARIOS:-1}
FAST_BURN_THRESHOLD=${FAST_BURN_THRESHOLD:-14.4}
WARN_BURN_THRESHOLD=${WARN_BURN_THRESHOLD:-3}
BUDGET_REMAINING_MIN=${BUDGET_REMAINING_MIN:-0.1}

print_usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --prometheus-url <url>     Override Prometheus base URL (default: $PROMETHEUS_URL)
  --promql-timeout <dur>     Set Prometheus query timeout (default: $PROMQL_TIMEOUT)
  --services <csv>           Comma-separated list of services to validate
  --scenario-command <cmd>   Command that runs the key E2E scenarios
  --skip-scenarios           Skip executing scenarios (only validate metrics)
  -h, --help                 Show this help message
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prometheus-url)
      PROMETHEUS_URL=$2
      shift 2
      ;;
    --promql-timeout)
      PROMQL_TIMEOUT=$2
      shift 2
      ;;
    --services)
      REQUIRED_SERVICES=$2
      shift 2
      ;;
    --scenario-command)
      SCENARIO_COMMAND=$2
      shift 2
      ;;
    --skip-scenarios)
      RUN_SCENARIOS=0
      shift
      ;;
    -h|--help)
      print_usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      print_usage
      exit 1
      ;;
  esac
done

info() { echo "[SLO] $*"; }
error() { echo "[SLO][ERROR] $*" >&2; }

run_prom_query() {
  local expr=$1
  curl -fsS --get \
    --data-urlencode "query=${expr}" \
    --data-urlencode "timeout=${PROMQL_TIMEOUT}" \
    "$PROMETHEUS_URL/api/v1/query"
}

value_for_service() {
  local expr=$1
  local service=$2
  local raw
  if ! raw=$(run_prom_query "$expr"); then
    error "Failed to query Prometheus for expression: $expr"
    return 1
  fi
  echo "$raw" | jq -er --arg svc "$service" '
    if .status != "success" then
      error(.error // "query failed")
    else
      (first(.data.result[] | select(.metric.service == $svc)) // error("service " + $svc + " missing"))
      | .value[1]
    end
  '
}

lt() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a<b)}'; }
le() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a<=b)}'; }
gt() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a>b)}'; }

as_percent() { awk -v v="$1" 'BEGIN{printf "%.3f", v * 100}'; }
as_rate() { awk -v v="$1" 'BEGIN{printf "%.2f", v}'; }
as_ms() { awk -v v="$1" 'BEGIN{printf "%.2f", v * 1000}'; }

if [[ $RUN_SCENARIOS -eq 1 ]]; then
  info "Executing scenario command: $SCENARIO_COMMAND"
  bash -c "$SCENARIO_COMMAND"
fi

IFS=',' read -r -a SERVICE_LIST <<< "$REQUIRED_SERVICES"

header=$(printf "%-12s %-10s %-10s %-10s %-10s %-12s %-12s\n" "Service" "Avail%" "Target%" "BurnRate" "Budget%" "p95(ms)" "Target(ms)")
rows=()
failures=()

for svc in "${SERVICE_LIST[@]}"; do
  svc_trim=$(echo "$svc" | xargs)
  [[ -z $svc_trim ]] && continue

  avail=$(value_for_service 'butterfly:slo_availability:ratio_rate5m' "$svc_trim") || exit 1
  target=$(value_for_service 'butterfly:slo_target:availability_ratio' "$svc_trim") || exit 1
  burn=$(value_for_service 'butterfly:slo_error_budget_burn_rate:ratio' "$svc_trim") || exit 1
  budget=$(value_for_service 'butterfly:slo_error_budget_remaining:ratio' "$svc_trim") || exit 1
  latency=$(value_for_service 'butterfly:slo_latency:p95_seconds' "$svc_trim") || exit 1
  latency_target=$(value_for_service 'butterfly:slo_latency_target:p95_seconds' "$svc_trim") || exit 1

  avail_pct=$(as_percent "$avail")
  target_pct=$(as_percent "$target")
  burn_fmt=$(as_rate "$burn")
  budget_pct=$(as_percent "$budget")
  latency_ms=$(as_ms "$latency")
  latency_target_ms=$(as_ms "$latency_target")

  rows+=("$(printf "%-12s %-10s %-10s %-10s %-10s %-12s %-12s" "$svc_trim" "$avail_pct" "$target_pct" "$burn_fmt" "$budget_pct" "$latency_ms" "$latency_target_ms")")

  if lt "$avail" "$target"; then
    failures+=("$svc_trim availability ${avail_pct}% < target ${target_pct}%")
  fi
  if gt "$burn" "$FAST_BURN_THRESHOLD"; then
    failures+=("$svc_trim burn rate $burn_fmt exceeds fast threshold $FAST_BURN_THRESHOLD")
  elif gt "$burn" "$WARN_BURN_THRESHOLD"; then
    failures+=("$svc_trim burn rate $burn_fmt exceeds warning threshold $WARN_BURN_THRESHOLD")
  fi
  if le "$budget" "$BUDGET_REMAINING_MIN"; then
    failures+=("$svc_trim error budget remaining ${budget_pct}% <= ${BUDGET_REMAINING_MIN} threshold")
  fi
  if gt "$latency" "$latency_target"; then
    failures+=("$svc_trim p95 latency ${latency_ms}ms > target ${latency_target_ms}ms")
  fi
done

info "SLO snapshot"
echo "$header"
for line in "${rows[@]}"; do
  echo "$line"
done

echo
if [[ ${#failures[@]} -gt 0 ]]; then
  error "SLO violations detected:"
  for msg in "${failures[@]}"; do
    error " - $msg"
  done
  exit 2
else
  info "All monitored services are within SLO thresholds."
fi
