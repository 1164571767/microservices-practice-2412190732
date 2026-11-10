#!/usr/bin/env bash
# Run only against an isolated demonstration deployment: stops and restores patient-service.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/.artifacts/week07"
mkdir -p "$OUT"
PYTHON_BIN="${PYTHON:-python3}"
restore_needed=0
cleanup() {
  if [[ "$restore_needed" == 1 ]]; then
    docker compose start patient-service >/dev/null
  fi
}
trap cleanup EXIT
count_appointments() {
  docker compose exec -T micro-mysql sh -c \
    'MYSQL_PWD="$CLINIC_DB_PASSWORD" mysql -usvc_clinic -N petcare_clinic -e "select count(*) from appointment"' | tr -d '\r'
}

"$PYTHON_BIN" scripts/nacos-check.py --wait 120 --output "$OUT/registered.json" > "$OUT/registered.txt"
bash scripts/smoke-test.sh > "$OUT/smoke-normal.txt" 2>&1
before="$(count_appointments)"
echo "PASS normal booking; total local appointments=$before"
restore_needed=1
docker compose stop patient-service
"$PYTHON_BIN" scripts/nacos-check.py --service patient-service --expect-count 0 --wait 120 \
  --output "$OUT/unregistered.json" > "$OUT/unregistered.txt"
bash scripts/smoke-test.sh --dependency-failure > "$OUT/smoke-down.txt" 2>&1
after_failure="$(count_appointments)"
[[ "$after_failure" == "$before" ]] || { echo 'FAIL dependency failure added local appointments'; exit 1; }
curl -fsS http://127.0.0.1:8083/actuator/health > "$OUT/clinic-health-while-patient-down.json"
if bash scripts/smoke-test.sh --health-only > "$OUT/smoke-expected-failure.txt" 2>&1; then
  echo 'FAIL smoke script accepted an unavailable patient-service'
  exit 1
else
  expected_failure_exit=$?
fi
echo "PASS unavailable dependency: 503; local appointments $before -> $after_failure; smoke exit=$expected_failure_exit"

docker compose start patient-service
for attempt in $(seq 1 120); do
  if curl -fsS http://127.0.0.1:8082/actuator/health > "$OUT/patient-health-restored.json" 2>/dev/null; then break; fi
  sleep 2
done
"$PYTHON_BIN" scripts/nacos-check.py --service patient-service --expect-count 1 --wait 120 \
  --output "$OUT/restored.json" > "$OUT/restored.txt"
bash scripts/smoke-test.sh --verify-restored > "$OUT/smoke-restored.txt" 2>&1
after_restored="$(count_appointments)"
[[ "$after_restored" == "$((before + 1))" ]] || { echo 'FAIL restored booking did not add exactly one appointment'; exit 1; }
bash scripts/smoke-test.sh --health-only > "$OUT/smoke-health.txt" 2>&1
restore_needed=0
docker compose logs --no-color --tail=2000 patient-service | grep 'Booking admission accepted' > "$OUT/patient-admissions.txt"
export WEEK07_COUNT_BEFORE="$before" WEEK07_COUNT_FAILED="$after_failure" WEEK07_COUNT_RESTORED="$after_restored" WEEK07_FAILURE_EXIT="$expected_failure_exit"
"$PYTHON_BIN" - <<'PY'
import json, os
from pathlib import Path
from datetime import datetime
out = Path('.artifacts/week07')
context = json.loads(Path('.artifacts/week06-smoke-context.json').read_text())
registered = json.loads((out/'registered.json').read_text())
report = {
    'recordedAt': datetime.now().astimezone().isoformat(),
    'flow': 'clinic-service -> OpenFeign(patient-service) -> original patient admission API',
    'group': registered['services'][0]['group'], 'namespace': registered['services'][0]['namespace'],
    'appointments': {k: int(os.environ[v]) for k, v in {
        'beforeFailure': 'WEEK07_COUNT_BEFORE', 'afterFailure': 'WEEK07_COUNT_FAILED', 'afterRecovery': 'WEEK07_COUNT_RESTORED'}.items()},
    'expectedFailedHealthExit': int(os.environ['WEEK07_FAILURE_EXIT']),
    'businessRequest': context['booking'], 'failedAndRecoveredRequest': context['failedBooking'],
    'petId': context['petId'], 'appointmentId': context['appointmentId'],
    'normalStatus': 201, 'missingResourceStatus': 404, 'dependencyDownStatus': 503, 'recoveryStatus': 201,
    'registered': registered,
    'unregistered': json.loads((out/'unregistered.json').read_text()),
    'restored': json.loads((out/'restored.json').read_text()),
    'clinicHealthWhilePatientDown': json.loads((out/'clinic-health-while-patient-down.json').read_text()),
    'patientHealthRestored': json.loads((out/'patient-health-restored.json').read_text()),
}
(out/'verification-results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
PY
echo "PASS restored booking: local appointments $after_failure -> $after_restored; all health checks passed"
echo "Evidence: .artifacts/week07/verification-results.json and command output files (no credentials)"
