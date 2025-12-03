#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/butterfly-e2e/docker-compose.yml"
FULL_COMPOSE_FILE="$PROJECT_ROOT/butterfly-e2e/docker-compose.full.yml"

COMPOSE_FILES=("$COMPOSE_FILE")
if [[ "${FASTPATH_FULL_STACK:-0}" == "1" ]]; then
  COMPOSE_FILES+=("$FULL_COMPOSE_FILE")
fi

compose() {
  local args=()
  for file in "${COMPOSE_FILES[@]}"; do
    args+=(-f "$file")
  done
  docker compose "${args[@]}" "$@"
}

wait_for_kafka() {
  local attempts=0
  while [[ $attempts -lt 20 ]]; do
    if compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 2
  done
  echo "Kafka did not become ready in time" >&2
  return 1
}

SERVICES_ARGS=("$@")
if [[ "${FASTPATH_SKIP_STUB:-0}" == "1" ]]; then
  if [[ "${FASTPATH_FULL_STACK:-0}" == "1" ]]; then
    SERVICES_ARGS=("zookeeper" "kafka" "odyssey-app" "${SERVICES_ARGS[@]}")
  else
    SERVICES_ARGS=("zookeeper" "kafka" "${SERVICES_ARGS[@]}")
  fi
fi

echo ">> Starting butterfly e2e dev stack (Kafka + rim.fast-path stub)"
compose up -d "${SERVICES_ARGS[@]}"

echo "⚡️ Kafka is coming up. Use ./scripts/dev-down.sh to stop."

if [[ -n "${FASTPATH_SCENARIOS:-}" ]]; then
  echo ">> Waiting for Kafka before seeding scenarios via fastpath-cli"
  wait_for_kafka
  for scenario in $FASTPATH_SCENARIOS; do
    echo ">> Seeding scenario: $scenario"
    "$PROJECT_ROOT/scripts/fastpath-cli.sh" publish \
      --scenario "$scenario" \
      --scenarios-root "$PROJECT_ROOT/butterfly-e2e/scenarios" \
      --bootstrap "${FASTPATH_BOOTSTRAP:-localhost:9092}" \
      --topic "${FASTPATH_TOPIC:-rim.fast-path}"
  done
fi
