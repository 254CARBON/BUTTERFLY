#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLI_JAR="$PROJECT_ROOT/butterfly-e2e/target/butterfly-e2e-cli.jar"

echo ">> Ensuring butterfly-common is installed locally"
mvn -q -f "$PROJECT_ROOT/butterfly-common/pom.xml" install -DskipTests

build_cli() {
  echo ">> Building fastpath CLI"
  mvn -q -f "$PROJECT_ROOT/butterfly-e2e/pom.xml" -DskipTests package
}

if [[ ! -f "$CLI_JAR" ]]; then
  build_cli
else
  # Rebuild if sources are newer than the jar
  LAST_BUILD=$(stat -c %Y "$CLI_JAR")
  LATEST_SRC=$(find "$PROJECT_ROOT/butterfly-e2e/src" -type f -printf '%T@\n' | sort -nr | head -n1 | cut -d. -f1)
  if [[ -n "$LATEST_SRC" && "$LATEST_SRC" -gt "$LAST_BUILD" ]]; then
    build_cli
  fi
fi

exec java ${JAVA_OPTS:-} -jar "$CLI_JAR" "$@"
