#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FAILURES=0

require_pattern() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if ! rg -q -U "$pattern" "$file"; then
    echo "ERROR: $message ($file)"
    FAILURES=$((FAILURES + 1))
  fi
}

forbid_pattern() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if rg -n "$pattern" "$file" >/tmp/secure-check.$$ 2>/dev/null; then
    echo "ERROR: $message ($file)"
    cat /tmp/secure-check.$$
    rm -f /tmp/secure-check.$$
    FAILURES=$((FAILURES + 1))
  else
    rm -f /tmp/secure-check.$$ 2>/dev/null || true
  fi
}

require_pattern "butterfly:[\\s\\S]*security:[\\s\\S]*secrets:" "$ROOT_DIR/PLATO/src/main/resources/application.yml" \
  "PLATO configuration must declare butterfly.security.secrets block"
require_pattern "butterfly\\.security\\.secrets" "$ROOT_DIR/ODYSSEY/odyssey-core/src/main/resources/application.properties" \
  "ODYSSEY configuration must declare butterfly.security.secrets block"

forbid_pattern "devpassword" "$ROOT_DIR/k8s/helm/butterfly-platform/values-prod.yaml" \
  "Production Helm values contain development password placeholder"
forbid_pattern "http://" "$ROOT_DIR/k8s/helm/butterfly-platform/values-prod.yaml" \
  "Production Helm values should not use http:// URLs"

if [[ "$FAILURES" -gt 0 ]]; then
  echo "Security configuration check failed ($FAILURES issue[s])."
  exit 1
fi

echo "Security configuration check passed."
