"""Initialize Nacos once or inspect real registered instances without printing credentials."""
import argparse
import json
import os
from pathlib import Path
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
NAMES = [name + "-service" for name in ("identity", "patient", "clinic", "pharmacy", "billing", "notification", "reporting")]


def settings():
    result = {}
    if (ROOT / ".env").exists():
        for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                result[key.strip()] = value.strip().strip("\"'")
    return result | dict(os.environ)


def api(base, path, values, post=False, token=None):
    encoded = urlencode(values)
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    if token:
        headers["accessToken"] = token
    request = Request(base + path + ("?" + encoded if not post else ""),
                      data=encoded.encode() if post else None, headers=headers)
    try:
        with urlopen(request, timeout=10) as response:
            result = json.load(response)
    except (HTTPError, URLError, TimeoutError) as error:
        raise RuntimeError("Nacos API request failed: " + path) from error
    if isinstance(result, dict) and result.get("code", 0) != 0:
        raise RuntimeError("Nacos API rejected request: " + path)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--initialize", action="store_true")
    parser.add_argument("--service", choices=NAMES)
    parser.add_argument("--expect-count", type=int)
    parser.add_argument("--wait", type=int, default=0)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    config = settings()
    base = config.get("NACOS_API_URL", "http://127.0.0.1:8848").rstrip("/")
    password = config.get("NACOS_PASSWORD", "")
    if not password or password.startswith("replace-"):
        raise RuntimeError("Set NACOS_PASSWORD in local .env before initialization/login")
    if args.initialize:
        api(base, "/nacos/v3/auth/user/admin", {"password": password}, post=True)
        print("PASS initialized Nacos administrator; password retained only in local .env")
        return
    login = api(base, "/nacos/v3/auth/user/login", {"username": config.get("NACOS_USERNAME", "nacos"), "password": password}, post=True)
    token = login.get("accessToken") or login.get("data", {}).get("accessToken")
    if not token:
        raise RuntimeError("Nacos login returned no access token")
    group = config.get("NACOS_GROUP", "PETCARE_SFY_GROUP")
    namespace = config.get("NACOS_NAMESPACE", "")
    deadline = time.monotonic() + args.wait
    while True:
        records = []
        valid = True
        for service in [args.service] if args.service else NAMES:
            result = api(base, "/nacos/v3/client/ns/instance/list", {
                "serviceName": service, "groupName": group, "namespaceId": namespace, "healthyOnly": "true",
            }, token=token)
            data = result.get("data", result)
            hosts = data if isinstance(data, list) else data.get("hosts", [])
            hosts = [host for host in hosts if host.get("healthy", True) and host.get("enabled", True)]
            records.append({"service": service, "group": group, "namespace": namespace or "public", "instances": [
                {"ip": host["ip"], "port": host["port"], "healthy": host.get("healthy", True)} for host in hosts]})
            valid &= len(hosts) == args.expect_count if args.expect_count is not None else len(hosts) >= 1
        if valid or time.monotonic() >= deadline:
            break
        time.sleep(2)
    report = {"passed": bool(valid), "services": records}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not valid:
        raise RuntimeError("Nacos registered instance count did not meet expectation")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError) as error:
        print("FAIL " + str(error), file=sys.stderr)
        sys.exit(1)
