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

echo ">> Stopping butterfly e2e dev stack"
compose down "$@"
