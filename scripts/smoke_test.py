#!/usr/bin/env python3
"""PetCare 全链路冒烟测试（T-37 运行手册配套）。

按「注册主人 -> 建档建宠 -> 管理员建医生/排班 -> 预约 -> 签到叫号 -> 病历处方
-> 发药扣库存 -> 账单支付 -> 通知 -> 统计报表」跑通一条真实业务闭环，并对关键
业务错误码（冲突 / 幂等回放 / 重复支付 / 越权）做断言。

只依赖 Python 3 标准库。连接一个已启动的 PetCare 实例即可：

    python3 scripts/smoke_test.py
    BASE_URL=http://localhost:8080 ADMIN_PASSWORD='AdminPassw0rd!' python3 scripts/smoke_test.py

脚本会写入演示数据（用户名/手机号/药品名带随机后缀，可重复执行）；不会删除任何
数据，也不会读取或打印 token / 密码。
"""

from __future__ import annotations

import json
import os
import random
import string
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from typing import Any
from zoneinfo import ZoneInfo

# Windows 控制台默认代码页可能不是 UTF-8；统一输出编码，避免中文日志乱码。
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8", errors="replace")

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080").rstrip("/")
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "AdminPassw0rd!")
ZONE = ZoneInfo(os.getenv("SMOKE_ZONE", "Asia/Shanghai"))
HTTP_TIMEOUT = float(os.getenv("SMOKE_HTTP_TIMEOUT", "30"))
PROJECTION_TIMEOUT = float(os.getenv("SMOKE_PROJECTION_TIMEOUT", "10"))
DEMO_PASSWORD = "Str0ngPassw0rd!"


class SmokeFailure(AssertionError):
    """冒烟断言失败；消息中只放状态码与响应体，不放凭据。"""


def step(message: str) -> None:
    print(f"\n[step] {message}")


def ok(message: str) -> None:
    print(f"  OK   {message}")


def parse_body(raw: bytes) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return raw.decode("utf-8", errors="replace")


def api(
    method: str,
    path: str,
    *,
    token: str | None = None,
    body: Any = None,
    headers: dict[str, str] | None = None,
    timeout: float = HTTP_TIMEOUT,
) -> tuple[int, Any]:
    """发送 HTTP 请求；HTTP 错误也返回状态码与响应体，便于断言错误码。"""
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(BASE_URL + path, data=data, method=method)
    if body is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, parse_body(response.read())
    except urllib.error.HTTPError as error:
        return error.code, parse_body(error.read())
    except urllib.error.URLError as error:
        raise SmokeFailure(
            f"无法连接 {BASE_URL}{path}：{error.reason}（请先启动应用并确认 BASE_URL）"
        ) from error


def expect(
    method: str,
    path: str,
    *,
    status: int | tuple[int, ...],
    code: str | None = None,
    label: str,
    token: str | None = None,
    body: Any = None,
    headers: dict[str, str] | None = None,
) -> Any:
    expected_statuses = (status,) if isinstance(status, int) else status
    actual_status, payload = api(method, path, token=token, body=body, headers=headers)
    if actual_status not in expected_statuses:
        raise SmokeFailure(
            f"{label}：期望 HTTP {expected_statuses}，实际 {actual_status}，响应={payload!r}"
        )
    if code is not None:
        actual_code = payload.get("code") if isinstance(payload, dict) else None
        if actual_code != code:
            raise SmokeFailure(
                f"{label}：期望错误码 {code}，实际 {actual_code}，响应={payload!r}"
            )
    detail = f"（{code}）" if code else ""
    ok(f"{label} -> HTTP {actual_status}{detail}")
    return payload


def unique_run_id() -> str:
    explicit = os.getenv("SMOKE_RUN_ID")
    if explicit:
        return explicit
    stamp = datetime.now(ZONE).strftime("%Y%m%d%H%M%S")
    suffix = "".join(random.choices(string.ascii_lowercase, k=4))
    return f"{stamp}{suffix}"


def unique_phone() -> str:
    return "1" + "".join(random.choices(string.digits, k=10))


def login(username: str, password: str) -> str:
    payload = expect(
        "POST",
        "/api/auth/login",
        status=200,
        label=f"登录 {username}",
        body={"username": username, "password": password},
    )
    token = payload.get("accessToken") if isinstance(payload, dict) else None
    if not token:
        raise SmokeFailure(f"登录 {username} 未返回 accessToken")
    return token


def local_tomorrow() -> date:
    return datetime.now(ZONE).date() + timedelta(days=1)


def iso_utc(moment: datetime) -> str:
    return moment.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def query(path: str, **params: Any) -> str:
    filtered = {key: value for key, value in params.items() if value is not None}
    return path + "?" + urllib.parse.urlencode(filtered)


def wait_for(description: str, probe, expected: Any):
    """等待最终一致读模型（事件投影）追上；不是并发同步手段。"""
    deadline = time.monotonic() + PROJECTION_TIMEOUT
    last = None
    while time.monotonic() < deadline:
        last = probe()
        if last == expected:
            ok(f"{description}（已追平）")
            return last
        time.sleep(0.2)
    raise SmokeFailure(f"{description}：等待 {PROJECTION_TIMEOUT}s 后仍为 {last!r}，期望 {expected!r}")


def notification_types(token: str, status: str | None = None) -> set[str]:
    payload = expect(
        "GET",
        query("/api/notification/notifications", page=0, size=100, status=status),
        status=200,
        label="查询通知列表",
        token=token,
    )
    items = payload.get("items", []) if isinstance(payload, dict) else []
    return {item.get("type") for item in items}


def main() -> int:
    run = unique_run_id()
    today = datetime.now(ZONE).date()
    owner_username = f"smoke.owner.{run}"
    doctor_username = f"smoke.doctor.{run}"
    drug_name = f"Smoke阿莫西林-{run}"
    charge_name = f"诊查费-{run}"
    appointment_start = datetime.now(ZONE).replace(microsecond=0) + timedelta(minutes=2)
    if appointment_start.date() != today:
        raise SmokeFailure("临近上海午夜，请跨日后重跑当日签到冒烟")
    appointment_end = appointment_start + timedelta(minutes=30)
    book_body = {
        "petId": None,
        "doctorId": None,
        "startAt": iso_utc(appointment_start),
        "endAt": iso_utc(appointment_end),
    }
    book_headers = {"Idempotency-Key": f"smoke-book-{run}"}

    print(f"PetCare 冒烟测试：BASE_URL={BASE_URL} RUN_ID={run}")

    step("1. 健康检查与 OpenAPI 文档")
    expect("GET", "/actuator/health/readiness", status=200, label="就绪探针")
    openapi = expect("GET", "/v3/api-docs", status=200, label="OpenAPI 文档")
    if not str(openapi.get("openapi", "")).startswith("3."):
        raise SmokeFailure(f"OpenAPI 版本异常：{openapi.get('openapi')!r}")
    if "bearerAuth" not in json.dumps(openapi.get("components", {}), ensure_ascii=False):
        raise SmokeFailure("OpenAPI 未声明 bearerAuth 安全方案")

    step("2. 未认证 / 越权基线")
    expect(
        "GET",
        "/api/identity/me",
        status=401,
        code="AUTH_UNAUTHENTICATED",
        label="匿名访问受保护接口",
    )

    step("3. 主人注册、登录、建档、建宠")
    owner_phone = unique_phone()
    expect(
        "POST",
        "/api/auth/register",
        status=201,
        label="注册主人",
        body={
            "username": owner_username,
            "phone": owner_phone,
            "password": DEMO_PASSWORD,
            "displayName": f"冒烟主人 {run}",
        },
    )
    owner_token = login(owner_username, DEMO_PASSWORD)
    expect(
        "PUT",
        "/api/owner/me",
        status=200,
        label="创建主人档案",
        token=owner_token,
        body={"name": f"冒烟主人 {run}", "phone": owner_phone, "address": "杭州"},
    )
    pet = expect(
        "POST",
        "/api/owner/pets",
        status=201,
        label="创建宠物",
        token=owner_token,
        body={
            "name": f"豆豆-{run}",
            "species": "金毛",
            "birthDate": "2022-05-01",
            "weightKg": 28.5,
        },
    )
    pet_id = pet["id"]
    expect("GET", f"/api/owner/pets/{pet_id}", status=200, label="查询宠物", token=owner_token)

    step("4. 管理员登录，创建医生与排班")
    admin_token = login(ADMIN_USERNAME, ADMIN_PASSWORD)
    doctor = expect(
        "POST",
        "/api/identity/users",
        status=201,
        label="创建医生账号",
        token=admin_token,
        body={
            "username": doctor_username,
            "phone": unique_phone(),
            "password": DEMO_PASSWORD,
            "displayName": f"冒烟医生 {run}",
            "roles": ["DOCTOR"],
        },
    )
    doctor_id = doctor["id"]
    expect(
        "POST",
        "/api/appointment/schedules",
        status=201,
        label="创建当日尚未开始的排班",
        token=admin_token,
        body={
            "doctorId": doctor_id,
            "startAt": iso_utc(appointment_start),
            "endAt": iso_utc(appointment_start + timedelta(hours=3)),
            "slotMinutes": 30,
        },
    )
    slots = expect(
        "GET",
        query("/api/appointment/availability", doctorId=doctor_id, date=today.isoformat()),
        status=200,
        label="查询可预约时段",
        token=owner_token,
    )
    if not isinstance(slots, list) or not slots:
        raise SmokeFailure(f"可预约时段为空：{slots!r}")
    first_slot = slots[0]
    book_body["petId"] = pet_id
    book_body["doctorId"] = doctor_id
    book_body["startAt"] = first_slot["startAt"]
    book_body["endAt"] = first_slot["endAt"]

    step("5. 预约：幂等回放 + 时段冲突")
    booked = expect(
        "POST",
        "/api/appointment/appointments",
        status=201,
        label="创建预约",
        token=owner_token,
        body=book_body,
        headers=book_headers,
    )
    appointment_id = booked["id"]
    if booked.get("status") != "SCHEDULED":
        raise SmokeFailure(f"预约初始状态异常：{booked.get('status')!r}")
    replay = expect(
        "POST",
        "/api/appointment/appointments",
        status=201,
        label="同幂等键重试",
        token=owner_token,
        body=book_body,
        headers=book_headers,
    )
    if replay.get("id") != appointment_id:
        raise SmokeFailure("同幂等键重试未回放同一预约")
    expect(
        "POST",
        "/api/appointment/appointments",
        status=409,
        code="APPOINTMENT_CONFLICT",
        label="同医生同时段重复预约",
        token=owner_token,
        body=book_body,
        headers={"Idempotency-Key": f"smoke-book-conflict-{run}"},
    )

    step("6. 签到、候诊队列与叫号")
    expect(
        "POST",
        f"/api/appointment/appointments/{appointment_id}/check-in",
        status=200,
        label="到院签到",
        token=admin_token,
    )
    expect(
        "GET",
        query("/api/appointment/queue", doctorId=doctor_id),
        status=200,
        label="查询候诊队列",
        token=admin_token,
    )
    called = expect(
        "POST",
        query("/api/appointment/queue/call", doctorId=doctor_id),
        status=200,
        label="叫下一号",
        token=admin_token,
    )
    if called.get("id") != appointment_id or called.get("status") != "CALLED":
        raise SmokeFailure(f"叫号结果异常：{called!r}")

    step("7. 服务端诊疗草稿、正式病历处方与完成（接诊医生）")
    doctor_token = login(doctor_username, DEMO_PASSWORD)
    consultation_url = f"/api/medical/consultations/{appointment_id}"
    draft = {
        "symptoms": "咳嗽、食欲下降", "diagnosis": "急性支气管炎",
        "advice": "多休息、清淡饮食", "prescriptionChoice": "PRESCRIPTION",
        "items": [{"drugName": drug_name, "specification": "0.5g", "dosage": "1片",
                   "frequency": "每日2次", "quantity": 6, "days": 5, "note": "饭后服用"}],
    }
    expect("PUT", consultation_url + "/draft", status=200, label="保存诊疗草稿",
           token=doctor_token, body={"version": 0, "draft": draft})
    restored = expect("GET", consultation_url, status=200, label="恢复服务端草稿", token=doctor_token)
    if restored.get("draft", {}).get("diagnosis") != draft["diagnosis"]:
        raise SmokeFailure("诊疗草稿未恢复")
    submitted = expect("POST", consultation_url + "/submit", status=200,
                       label="原子提交正式病历与处方", token=doctor_token, body={"version": 1})
    record = submitted["record"]
    record_id = record["id"]
    prescription_id = record["prescriptions"][0]["id"]
    completed = expect("POST", consultation_url + "/complete", status=200,
                       label="完成诊疗", token=doctor_token,
                       body={"version": 2, "noPrescription": False})
    if not completed.get("completedAt") or completed.get("canWrite"):
        raise SmokeFailure("诊疗完成状态异常")

    step("8. 药品目录、发药扣库存与低库存预警")
    drug = expect(
        "POST",
        "/api/pharmacy/drugs",
        status=201,
        label="新建药品并初始化库存",
        token=admin_token,
        body={
            "name": drug_name,
            "specification": "0.5g",
            "unit": "盒",
            "lowStockThreshold": 15,
            "initialQuantity": 20,
        },
    )
    drug_id = drug["id"]
    dispensed = expect(
        "POST",
        f"/api/pharmacy/prescriptions/{prescription_id}/dispense",
        status=201,
        label="处方发药",
        token=admin_token,
        headers={"Idempotency-Key": f"smoke-dispense-{run}"},
    )
    if not isinstance(dispensed.get("items"), list) or not dispensed["items"]:
        raise SmokeFailure(f"发药明细为空：{dispensed!r}")
    expect(
        "POST",
        f"/api/pharmacy/prescriptions/{prescription_id}/dispense",
        status=201,
        label="同一处方重复发药（回放）",
        token=admin_token,
    )
    inventory = expect(
        "GET",
        f"/api/pharmacy/inventory/{drug_id}",
        status=200,
        label="查询发药后库存",
        token=admin_token,
    )
    if inventory.get("quantity") != 14:
        raise SmokeFailure(f"发药后库存应为 14，实际 {inventory.get('quantity')!r}")
    low_stock = expect(
        "GET",
        query("/api/pharmacy/inventory/low-stock", page=0, size=100),
        status=200,
        label="低库存预警列表",
        token=admin_token,
    )
    low_stock_ids = {item.get("drugId") for item in low_stock.get("items", [])}
    if drug_id not in low_stock_ids:
        raise SmokeFailure("发药跌破阈值后未出现在低库存列表")

    step("9. 账单、支付幂等与重复支付")
    charge = expect(
        "POST",
        "/api/billing/charge-items",
        status=201,
        label="创建收费项目",
        token=admin_token,
        body={"name": charge_name, "amount": 50.0},
    )
    charge_id = charge["id"]
    bill_body = {
        "medicalRecordId": record_id,
        "prescriptionId": prescription_id,
        "items": [{"chargeItemId": charge_id, "quantity": 1}],
    }
    bill_headers = {"Idempotency-Key": f"smoke-bill-{run}"}
    bill = expect(
        "POST",
        "/api/billing/bills",
        status=201,
        label="生成账单",
        token=admin_token,
        body=bill_body,
        headers=bill_headers,
    )
    bill_id = bill["id"]
    if Decimal(str(bill.get("totalAmount"))) != Decimal("50.00"):
        raise SmokeFailure(f"账单金额异常：{bill.get('totalAmount')!r}")
    if bill.get("status") != "UNPAID":
        raise SmokeFailure(f"账单初始状态异常：{bill.get('status')!r}")
    replay_bill = expect(
        "POST",
        "/api/billing/bills",
        status=201,
        label="同幂等键重试生成账单",
        token=admin_token,
        body=bill_body,
        headers=bill_headers,
    )
    if replay_bill.get("id") != bill_id:
        raise SmokeFailure("同幂等键重试未回放同一账单")

    pay_headers = {"Idempotency-Key": f"smoke-pay-{run}"}
    paid = expect(
        "POST",
        f"/api/billing/bills/{bill_id}/pay",
        status=201,
        label="管理员模拟收款",
        token=admin_token,
        headers=pay_headers,
    )
    if paid.get("status") != "PAID":
        raise SmokeFailure(f"支付后账单状态异常：{paid.get('status')!r}")
    replayed_payment = expect(
        "POST",
        f"/api/billing/bills/{bill_id}/pay",
        status=201,
        label="同幂等键重复支付（回放）",
        token=admin_token,
        headers=pay_headers,
    )
    if replayed_payment.get("payment", {}).get("id") != paid.get("payment", {}).get("id"):
        raise SmokeFailure("同幂等键重复支付未回放同一支付记录")
    expect(
        "POST",
        f"/api/billing/bills/{bill_id}/pay",
        status=409,
        code="PAYMENT_ALREADY_PAID",
        label="无幂等键重复支付",
        token=admin_token,
    )

    handoff = expect("GET", consultation_url + "/handoff", status=200,
                     label="核实诊疗收费与发药交接", token=doctor_token)
    if handoff.get("bill", {}).get("id") != bill_id or handoff.get("bill", {}).get("status") != "PAID":
        raise SmokeFailure("诊疗工作台收费交接不匹配")
    if handoff.get("dispense", {}).get("id") != dispensed["id"]:
        raise SmokeFailure("诊疗工作台发药交接不匹配")

    step("10. 站内通知（预约 / 叫号 / 收费 / 低库存）")
    owner_types = notification_types(owner_token)
    for expected_type in ("APPOINTMENT_BOOKED", "QUEUE_CALLED", "BILL_PAID"):
        if expected_type not in owner_types:
            raise SmokeFailure(f"主人缺少通知类型 {expected_type}，实际 {sorted(owner_types)}")
    ok(f"主人通知类型齐全：{sorted(owner_types)}")
    admin_types = notification_types(admin_token)
    if "LOW_STOCK" not in admin_types:
        raise SmokeFailure(f"管理员缺少低库存通知，实际 {sorted(admin_types)}")
    ok("低库存通知已按角色投递给管理员")
    unread = expect(
        "GET",
        "/api/notification/notifications/unread-count",
        status=200,
        label="未读通知数",
        token=owner_token,
    )
    if not isinstance(unread, dict) or unread.get("unread", 0) < 1:
        raise SmokeFailure(f"主人未读通知数异常：{unread!r}")
    read_all = expect(
        "POST",
        "/api/notification/notifications/read-all",
        status=200,
        label="一键已读",
        token=owner_token,
    )
    if not isinstance(read_all, dict) or read_all.get("updated", 0) < 1:
        raise SmokeFailure(f"一键已读未更新任何通知：{read_all!r}")
    after_read = expect(
        "GET",
        "/api/notification/notifications/unread-count",
        status=200,
        label="已读后未读数",
        token=owner_token,
    )
    if after_read.get("unread") != 0:
        raise SmokeFailure(f"一键已读后未读数应为 0，实际 {after_read!r}")

    step("11. 越权与统计报表（只读投影）")
    expect(
        "GET",
        query("/api/reporting/revenue", **{"from": today.isoformat(), "to": today.isoformat()}),
        status=403,
        code="AUTH_FORBIDDEN",
        label="主人访问统计报表",
        token=owner_token,
    )
    visits = expect(
        "GET",
        query(
            "/api/reporting/visits",
            **{"from": today.isoformat(), "to": today.isoformat(), "doctorId": doctor_id},
        ),
        status=200,
        label="门诊量（按本次医生过滤）",
        token=admin_token,
    )
    if visits.get("totalVisits") != 1:
        raise SmokeFailure(f"本次医生门诊量应为 1，实际 {visits.get('totalVisits')!r}")
    doctor_rows = visits.get("doctors") or []
    if not any(row.get("doctorId") == doctor_id and row.get("visits") == 1 for row in doctor_rows):
        raise SmokeFailure(f"医生工作量投影异常：{doctor_rows!r}")

    revenue = expect(
        "GET",
        query("/api/reporting/revenue", **{"from": today.isoformat(), "to": today.isoformat()}),
        status=200,
        label="收入统计",
        token=admin_token,
    )
    if Decimal(str(revenue.get("paidAmount"))) < Decimal("50.00"):
        raise SmokeFailure(f"收入统计未包含本次支付：{revenue!r}")
    if Decimal(str(revenue.get("netAmount"))) != Decimal(str(revenue.get("paidAmount"))) - Decimal(
        str(revenue.get("refundedAmount"))
    ):
        raise SmokeFailure(f"净收入口径不一致：{revenue!r}")

    consumption = expect(
        "GET",
        query(
            "/api/reporting/drug-consumption",
            **{
                "from": today.isoformat(),
                "to": today.isoformat(),
                "drugId": drug_id,
                "page": 0,
                "size": 20,
            },
        ),
        status=200,
        label="药品消耗统计（按本次药品过滤）",
        token=admin_token,
    )
    if consumption.get("totalQuantity") != 6 or consumption.get("totalDispenses") != 1:
        raise SmokeFailure(f"药品消耗投影异常：{consumption!r}")

    step("12. 完成")
    print(
        "\nPASS：注册/宠物/排班/预约/签到叫号/病历处方/发药/账单支付/"
        "通知/统计全链路通过。"
    )
    print(f"演示数据：owner={owner_username} doctor={doctor_username} drug={drug_name}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SmokeFailure as failure:
        print(f"\nFAIL：{failure}", file=sys.stderr)
        sys.exit(1)
