#!/usr/bin/env bash
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
