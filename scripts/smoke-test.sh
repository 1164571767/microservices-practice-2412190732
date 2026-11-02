#!/usr/bin/env bash
# Checks the Nacos 3 console and all seven application health endpoints before business checks.
# Python failures are propagated unchanged through exec; credentials are never echoed.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${PYTHON:-}" ]]; then
  PYTHON_BIN="$PYTHON"
elif command -v python3 >/dev/null 2>&1 && python3 -c 'import sys; assert sys.version_info >= (3, 10)' >/dev/null 2>&1; then
  PYTHON_BIN=python3
else
  PYTHON_BIN=python
fi
export PYTHONUTF8=1
exec "$PYTHON_BIN" "$ROOT/scripts/microservices_smoke.py" "$@"
