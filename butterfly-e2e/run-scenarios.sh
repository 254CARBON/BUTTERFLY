#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/butterfly-e2e/docker-compose.yml"
FULL_COMPOSE_FILE="$PROJECT_ROOT/butterfly-e2e/docker-compose.full.yml"
SCENARIO_DIR="$PROJECT_ROOT/butterfly-e2e/scenarios"
CATALOG_FILE="$SCENARIO_DIR/catalog.json"
CLI="$PROJECT_ROOT/scripts/fastpath-cli.sh"

command -v jq >/dev/null 2>&1 || { echo "jq is required to parse the scenario catalog."; exit 1; }

FASTPATH_BOOTSTRAP="${FASTPATH_BOOTSTRAP:-localhost:9092}"
FASTPATH_TOPIC="${FASTPATH_TOPIC:-rim.fast-path}"
SCENARIO_CATEGORY="${SCENARIO_CATEGORY:-}"  # Filter by category (e.g., perception-capsule)
SCENARIO_FILTER='.[] | select(((.expect // "publish") == "publish") or ((.expect // "publish") == "fail"))'
if [[ -n "$SCENARIO_CATEGORY" ]]; then
  SCENARIOS="${FASTPATH_SCENARIOS:-$(jq -r --arg cat "$SCENARIO_CATEGORY" "$SCENARIO_FILTER | select(.category == \$cat) | .name" "$CATALOG_FILE")}"
else
  SCENARIOS="${FASTPATH_SCENARIOS:-$(jq -r "${SCENARIO_FILTER} | .name" "$CATALOG_FILE")}"
fi
RUN_REFLEX_ASSERTIONS="${RUN_REFLEX_ASSERTIONS:-1}"
START_STACK="${START_DEV_STACK:-1}"

WAIT_FOR_KAFKA_RETRIES=20
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
  while [[ $attempts -lt $WAIT_FOR_KAFKA_RETRIES ]]; do
    if compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 2
  done
  echo "Kafka did not become ready in time" >&2
  return 1
}

cleanup() {
  if [[ "$START_STACK" == "1" ]]; then
    "$PROJECT_ROOT/scripts/dev-down.sh" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if [[ "$START_STACK" == "1" ]]; then
  FASTPATH_SKIP_STUB=1 "$PROJECT_ROOT/scripts/dev-up.sh"
  wait_for_kafka
fi

status=0

for scenario in $SCENARIOS; do
  entry=$(jq -c --arg name "$scenario" '.[] | select(.name == $name)' "$CATALOG_FILE")
  if [[ -z "$entry" ]]; then
    echo "!! Scenario '$scenario' not found in catalog"
    status=1
    continue
  fi

  file=$(echo "$entry" | jq -r '.file')
  raw=$(echo "$entry" | jq -r '.raw // false')
  fault=$(echo "$entry" | jq -r '.fault // ""')
  expect=$(echo "$entry" | jq -r '.expect // "publish"')

  payload_path="$SCENARIO_DIR/$file"
  if [[ ! -f "$payload_path" ]]; then
    echo "!! Payload file missing for scenario $scenario: $payload_path"
    status=1
    continue
  fi

  if [[ "$fault" == "kafka-stop" ]]; then
    if [[ "$START_STACK" != "1" ]]; then
      echo "!! Skipping kafka-stop fault injection for '$scenario' because START_DEV_STACK=0"
      continue
    fi
    echo ">> Injecting Kafka hiccup for scenario $scenario"
    compose stop kafka >/dev/null
    sleep 3
  fi

  echo ">> Running scenario '$scenario' ($file)"
  raw_flag=()
  if [[ "$raw" == "true" ]]; then
    raw_flag=(--raw)
  fi
  set +e
  "$CLI" publish \
    --scenarios-root "$SCENARIO_DIR" \
    --file "$payload_path" \
    "${raw_flag[@]}" \
    --bootstrap "$FASTPATH_BOOTSTRAP" \
    --topic "$FASTPATH_TOPIC"
  exit_code=$?
  set -e

  if [[ "$fault" == "kafka-stop" ]]; then
    echo ">> Restarting Kafka after fault injection"
    compose up -d kafka >/dev/null
    wait_for_kafka
  fi

  if [[ "$expect" == "fail" && $exit_code -eq 0 ]]; then
    echo "!! Expected scenario '$scenario' to fail but it succeeded."
    status=1
  elif [[ "$expect" != "fail" && $exit_code -ne 0 ]]; then
    echo "!! Scenario '$scenario' failed (exit $exit_code)."
    status=1
  fi
done

if [[ "$RUN_REFLEX_ASSERTIONS" == "1" ]]; then
  echo ">> Running ODYSSEY reflex assertions (golden + negative)"
  mvn -f "$PROJECT_ROOT/ODYSSEY/pom.xml" -Dtest=HftGoldenPathIntegrationTest,HftNegativePathIntegrationTest test
fi

if [[ $status -ne 0 ]]; then
  echo "❌ One or more scenarios failed. See logs above."
else
  echo "✅ Scenarios executed."
fi

exit $status
