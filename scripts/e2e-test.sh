#!/usr/bin/env bash
# Runs against an isolated demo project. Stops and restarts both patient containers.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ "${1:-}" != "--isolated" || "$#" != 1 ]]; then
  echo 'Usage: bash scripts/e2e-test.sh --isolated (stops both patient instances)' >&2
  exit 2
fi
unset PYTHONOPTIMIZE
export PYTHON="${PYTHON:-python3}"
umask 077
mkdir -p .artifacts
work="$(mktemp -d .artifacts/week08.XXXXXXXX)"
context="$work/booking.json"
compose=(docker compose --profile week08)
patients=(patient-service patient-replica)
restore_needed=0
cleanup() {
  if (( restore_needed )); then
    "${compose[@]}" start --wait --wait-timeout 180 "${patients[@]}" >&2
  fi
}
trap cleanup EXIT

for service in "${patients[@]}"; do
  if [[ -z "$("${compose[@]}" ps --status running -q "$service")" ]]; then
    echo "FAIL $service is not running; start Compose with --profile week08" >&2
    exit 1
  fi
done
"$PYTHON" scripts/nacos-check.py --service patient-service --expect-count 2 --wait 120
bash scripts/smoke-test.sh --booking-only --exercise-replicas --context "$context"
pet_id="$("$PYTHON" -c 'import json, sys; print(json.load(open(sys.argv[1]))["petId"])' "$context")"
echo 'Verifying successful admissions for the pet created by this run:'
for service in "${patients[@]}"; do
  "${compose[@]}" logs --no-color "$service" | grep -F "Booking admission accepted petId=$pet_id"
done
echo 'PASS booking admission reached both patient instances'

restore_needed=1
"${compose[@]}" stop "${patients[@]}"
"$PYTHON" scripts/nacos-check.py --service patient-service --expect-count 0 --wait 120
bash scripts/smoke-test.sh --dependency-failure --context "$context"
"${compose[@]}" start --wait --wait-timeout 180 "${patients[@]}"
restore_needed=0
"$PYTHON" scripts/nacos-check.py --service patient-service --expect-count 2 --wait 120
bash scripts/smoke-test.sh --verify-restored --context "$context"
echo 'PASS end-to-end booking, two-instance load balancing, dependency failure and recovery'
