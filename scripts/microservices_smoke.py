"""Week 06 real HTTP smoke checks; standard library only, no database access.

Run from the repository root: bash scripts/smoke-test.sh
Creates unique demonstration users/patients/appointments and leaves them for inspection.
Never prints passwords or tokens. Runtime context is stored only in ignored .artifacts/.
"""
import argparse
import json
import os
from pathlib import Path
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
NAMES = ["identity", "patient", "clinic", "pharmacy", "billing", "notification", "reporting"]


def local_settings():
    settings = {}
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                settings[key.strip()] = value.strip().strip("\"'")
    settings.update(os.environ)
    return settings


SETTINGS = local_settings()
URLS = {name: SETTINGS.get(name.upper() + "_SERVICE_URL", f"http://127.0.0.1:{8081+i}").rstrip("/")
        for i, name in enumerate(NAMES)}
GATEWAY = SETTINGS.get("GATEWAY_URL", "").rstrip("/")
RESULTS = []


def request(service, method, path, body=None, token=None, expected=200, key=None):
    base = GATEWAY if GATEWAY and path.startswith("/api/") else URLS[service]
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    data = None if body is None else json.dumps(body).encode("utf-8")
    try:
        with urlopen(Request(base + path, data=data, method=method, headers=headers), timeout=35) as response:
            status, raw = response.status, response.read()
    except HTTPError as error:
        status, raw = error.code, error.read()
    except (URLError, TimeoutError) as error:
        raise AssertionError(f"{service} {method} {path}: connection failed: {error}") from error
    parsed = json.loads(raw) if raw else None
    entry = {"service": service, "method": method, "path": path, "status": status}
    if isinstance(parsed, dict) and "code" in parsed:
        entry["code"] = parsed["code"]
    RESULTS.append(entry)
    print(f"{status} {service:12} {method:4} {path}" + (f" [{entry['code']}]" if "code" in entry else ""), flush=True)
    assert expected is None or status == expected, f"Expected {expected}, got {status}: {entry.get('code', 'unexpected response')}"
    return parsed


def login(username, password):
    return request("identity", "POST", "/api/auth/login", {"username": username, "password": password})["accessToken"]


def appointments(context, token):
    return request("clinic", "GET", f"/api/appointment/pets/{context['petId']}/appointments", token=token)


def check_nacos_console():
    base = SETTINGS.get("NACOS_CONSOLE_URL", "http://127.0.0.1:" + SETTINGS.get("NACOS_CONSOLE_PORT", "18080")).rstrip("/")
    with urlopen(base + "/", timeout=10) as response:
        assert response.status == 200, "Nacos console did not return HTTP 200"
        assert b"<html" in response.read().lower(), "Nacos console returned no HTML page"
    print("200 nacos-console " + base + "/", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--health-only", action="store_true")
    parser.add_argument("--booking-only", action="store_true", help="Skip health checks for the minimal booking stack")
    parser.add_argument("--exercise-replicas", action="store_true", help="Book two more slots, reserving the second slot for a failure check")
    parser.add_argument("--dependency-failure", action="store_true", help="Run after stopping patient-service; requires a successful smoke context")
    parser.add_argument("--verify-restored", action="store_true", help="Check appointment count after restoring patient-service")
    parser.add_argument("--context", type=Path, default=ROOT / ".artifacts/week06-smoke-context.json")
    args = parser.parse_args()
    if args.dependency_failure or args.verify_restored:
        context = json.loads(args.context.read_text(encoding="utf-8"))
        token = login(context["username"], context["password"])
        if args.dependency_failure:
            # A different unused slot prevents a replay/conflict from masking failed admission.
            booking = dict(context["booking"])
            for field in ("startAt", "endAt"):
                value = datetime.fromisoformat(booking[field].replace("Z", "+00:00")) + timedelta(minutes=15)
                booking[field] = value.isoformat().replace("+00:00", "Z")
            context["failedBooking"] = booking
            args.context.write_text(json.dumps(context, ensure_ascii=False, indent=2), encoding="utf-8")
            response = request("clinic", "POST", "/api/appointment/appointments", booking, token, 503, "down-" + uuid.uuid4().hex)
            assert response["code"] == "DEPENDENCY_UNAVAILABLE"
        else:
            count = len(appointments(context, token))
            assert count == context["appointmentCount"], f"Unexpected data change: {count}"
            print(f"PASS dependency failure left appointment count unchanged: {count}")
            request("clinic", "POST", "/api/appointment/appointments", context["failedBooking"], token, 201, "restored-" + uuid.uuid4().hex)
            assert len(appointments(context, token)) == count + 1
            print(f"PASS restored booking succeeds: appointments {count} -> {count + 1}")
        return

    if not args.booking_only or args.health_only:
        check_nacos_console()
        for name in NAMES:
            health = request(name, "GET", "/actuator/health")
            assert health["status"] == "UP", f"{name} is not UP"
    if args.health_only:
        print("PASS Nacos console accessible; seven services UP")
        return

    username = SETTINGS.get("BOOTSTRAP_ADMIN_USERNAME")
    password = SETTINGS.get("BOOTSTRAP_ADMIN_PASSWORD")
    assert username and password, "Set BOOTSTRAP_ADMIN_USERNAME/BOOTSTRAP_ADMIN_PASSWORD (environment or root .env)"
    admin = login(username, password)
    stamp = str(time.time_ns())[-15:]
    owner_name, owner_password = "w06.owner." + stamp, "Week06Owner2026!"
    request("identity", "POST", "/api/auth/register", {"username": owner_name, "phone": "1" + stamp,
            "password": owner_password, "displayName": "第六周演示主人"}, expected=201)
    owner_token = login(owner_name, owner_password)
    request("patient", "PUT", "/api/owner/me", {"name": "第六周演示主人", "phone": "1" + stamp, "address": "杭州"}, owner_token)
    pet = request("patient", "POST", "/api/owner/pets", {"name": "第六周演示宠物", "species": "犬",
                  "birthDate": "2022-05-01", "weightKg": 5.5}, owner_token, 201)
    doctor = request("identity", "POST", "/api/identity/users", {"username": "w06.doctor." + stamp,
                     "phone": "2" + stamp, "password": "Week06Doctor2026!", "displayName": "第六周演示医生", "roles": ["DOCTOR"]}, admin, 201)
    start = (datetime.now(timezone.utc) + timedelta(minutes=15)).replace(second=0, microsecond=0)
    end = start + timedelta(minutes=15)
    instant = lambda value: value.isoformat().replace("+00:00", "Z")
    request("clinic", "POST", "/api/appointment/schedules", {"doctorId": doctor["id"], "startAt": instant(start),
            "endAt": instant(start + timedelta(hours=1)), "slotMinutes": 15}, admin, 201)
    context = {"username": owner_name, "password": owner_password, "petId": pet["id"], "doctorId": doctor["id"],
               "doctorUsername": "w06.doctor." + stamp, "doctorPassword": "Week06Doctor2026!"}
    booking = {"petId": pet["id"], "doctorId": doctor["id"], "startAt": instant(start), "endAt": instant(end)}
    context["booking"] = booking
    before = len(appointments(context, owner_token))
    key = "week06-" + stamp
    appointment = request("clinic", "POST", "/api/appointment/appointments", booking, owner_token, 201, key)
    replay = request("clinic", "POST", "/api/appointment/appointments", booking, owner_token, 201, key)
    assert replay["id"] == appointment["id"]
    context["appointmentId"] = appointment["id"]
    after = len(appointments(context, owner_token))
    assert after == before + 1
    print(f"PASS real HTTP booking: appointments {before} -> {after}; idempotent replay adds 0")
    missing = dict(booking, petId=str(uuid.uuid4()))
    response = request("clinic", "POST", "/api/appointment/appointments", missing, owner_token, 404, "missing-" + stamp)
    assert response["code"] == "RESOURCE_NOT_FOUND"
    assert len(appointments(context, owner_token)) == after
    print(f"PASS missing pet: 404; appointment count stays {after}")
    if args.exercise_replicas:
        for offset in (30, 45):
            extra = dict(booking, startAt=instant(start + timedelta(minutes=offset)),
                         endAt=instant(end + timedelta(minutes=offset)))
            response = request("clinic", "POST", "/api/appointment/appointments", extra,
                               owner_token, 201, "week08-" + uuid.uuid4().hex)
            assert response["id"] != appointment["id"]
        assert len(appointments(context, owner_token)) == after + 2
        after += 2
        print(f"PASS two additional bookings; appointments={after}")
    context["appointmentCount"] = after
    args.context.parent.mkdir(parents=True, exist_ok=True)
    args.context.write_text(json.dumps(context, ensure_ascii=False, indent=2), encoding="utf-8")
    print("PASS smoke checks; demonstration data retained for inspection")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, ValueError, KeyError) as error:
        print("FAIL " + str(error), file=sys.stderr)
        sys.exit(1)
