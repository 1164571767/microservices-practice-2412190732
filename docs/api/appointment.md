# appointment API（M3 排班与预约）

- 统一错误体：`{"code": "<稳定机器码>", "message": "<可读信息>"}`；客户端依赖 `code`，见 `error-codes.md`。
- 认证方式：`Authorization: Bearer <accessToken>`；本模块接口均需认证，能力不足或越权返回 `AUTH_FORBIDDEN`。
- 时间统一用 **UTC ISO-8601**（如 `2026-11-10T01:00:00Z`）；`availability` 的 `date` 按 `Asia/Shanghai` 解释。
- 归属校验在服务端：主人只能操作自己宠物的预约；`STAFF/DOCTOR/ADMIN` 可读/代操作。

## 排班（仅 ADMIN 可改）

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| POST | `/api/appointment/schedules` | ADMIN | 新建排班（同医生时段不可重叠） | 201 `ScheduleResponse` | `AUTH_FORBIDDEN`；`APPOINTMENT_CONFLICT`（重叠）；`OPERATION_NOT_ALLOWED`（非医生）；`VALIDATION_FAILED` |
| GET | `/api/appointment/schedules?doctorId=` | 任意已认证 | 列出某医生排班 | 200 `ScheduleResponse[]` | `VALIDATION_FAILED` |
| DELETE | `/api/appointment/schedules/{scheduleId}` | ADMIN | 删除排班 | 204 | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |

```jsonc
// POST /api/appointment/schedules
{ "doctorId": "<uuid>", "startAt": "2026-11-10T00:00:00Z", "endAt": "2026-11-10T04:00:00Z", "slotMinutes": 30 }
```

## 可预约时段（T-15）

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| GET | `/api/appointment/availability?doctorId=&date=` | 任意已认证 | 按排班切分为 `slotMinutes` 时段，剔除已约满 | 200 `TimeSlotResponse[]` | `VALIDATION_FAILED` |

```jsonc
// GET /api/appointment/availability?doctorId=<uuid>&date=2026-11-10
[ { "startAt": "2026-11-10T01:00:00Z", "endAt": "2026-11-10T01:30:00Z" }, ... ]
```

## 预约

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| POST | `/api/appointment/appointments` | 宠物主人或员工 | 创建预约；支持 `Idempotency-Key` 头 | 201 `AppointmentResponse` | `APPOINTMENT_CONFLICT`（时段已被占）；`APPOINTMENT_NOT_AVAILABLE`（不在排班内）；`IDEMPOTENCY_CONFLICT`；`AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND`（宠物/医生不存在）；`VALIDATION_FAILED` |
| GET | `/api/appointment/appointments/{id}` | 宠物主人或员工 | 预约详情 | 200 | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |
| GET | `/api/appointment/pets/{petId}/appointments` | 宠物主人或员工 | 某宠物预约列表 | 200 `AppointmentResponse[]` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |
| POST | `/api/appointment/appointments/{id}/cancel` | 宠物主人或员工 | 取消（SCHEDULED/CHECKED_IN） | 200 `AppointmentResponse` | `APPOINTMENT_INVALID_STATE`；`AUTH_FORBIDDEN` |
| POST | `/api/appointment/appointments/{id}/reschedule` | 宠物主人或员工 | 改期（仅 SCHEDULED） | 200 | `APPOINTMENT_INVALID_STATE`；`APPOINTMENT_NOT_AVAILABLE`；`APPOINTMENT_CONFLICT` |
| POST | `/api/appointment/appointments/{id}/check-in` | `STAFF/DOCTOR/ADMIN` | 到院签到（仅 SCHEDULED） | 200 | `AUTH_FORBIDDEN`；`APPOINTMENT_INVALID_STATE` |

```jsonc
// POST /api/appointment/appointments   + Header: Idempotency-Key: <client-key>
{ "petId": "<uuid>", "doctorId": "<uuid>", "startAt": "2026-11-10T01:00:00Z", "endAt": "2026-11-10T01:30:00Z" }
// 201
{ "id": "<uuid>", "petId": "<uuid>", "doctorId": "<uuid>", "startAt": "2026-11-10T01:00:00Z",
  "endAt": "2026-11-10T01:30:00Z", "status": "SCHEDULED", "checkedInAt": null, "calledAt": null }
```

**幂等语义**：同一 `Idempotency-Key` + 相同请求 → 返回首次创建的同一预约（重试回放）；同一 key + 不同请求 → `IDEMPOTENCY_CONFLICT`。

## 候诊队列（T-18）

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| GET | `/api/appointment/queue?doctorId=` | `STAFF/DOCTOR/ADMIN` | 候诊列表（按签到时间升序，带序号） | 200 `QueueEntryResponse[]` | `AUTH_FORBIDDEN` |
| POST | `/api/appointment/queue/call?doctorId=` | `STAFF/DOCTOR/ADMIN` | 叫下一号，发布叫号事件 | 200 `AppointmentResponse` | `AUTH_FORBIDDEN`；`OPERATION_NOT_ALLOWED`（队列为空） |

**状态机**：`SCHEDULED` →(check-in) `CHECKED_IN` →(叫号) `CALLED`；`SCHEDULED`/`CHECKED_IN` →(取消) `CANCELLED`。非法流转返回 `APPOINTMENT_INVALID_STATE`。

**事件**：叫号时发布 `appointment.QueueNumberCalled{appointmentId, petId, doctorId, calledAt}`，供 `notification` 模块（M7）以 `@ApplicationModuleListener` 订阅。

## 校验规则（Bean Validation）

| 字段 | 规则 |
|---|---|
| schedule.doctorId / startAt / endAt | 非空；`endAt > startAt` |
| schedule.slotMinutes | 1–480 |
| appointment.petId / doctorId / startAt / endAt | 非空；`endAt > startAt` |

## 数据约束（DB 最终守卫）

- `doctor_schedule`：`UNIQUE (doctor_id, start_at)`（同一医生同一开始时刻不可重复）；`CHECK (end_at > start_at)`、`CHECK (slot_minutes > 0 AND <= 480)`。任意区间重叠由 `DoctorScheduleService` 在事务内检查。
- `appointment`：`active_start_at` 生成列（未取消时为 `start_at`，取消时为 `NULL`）+ `UNIQUE (doctor_id, active_start_at)` —— 保证同一医生时段不双订，同时已取消的预约不占用时段。
- 任意区间的重叠由 `AppointmentService` 先 `SELECT ... FOR UPDATE` 锁定该医生的排班，再查询重叠预约来保证（MySQL 无 `EXCLUDE` 约束）。
- UUID 以 `BINARY(16)` 存储，时间以 `DATETIME(6)` 存 UTC；依赖 MySQL 8（utf8mb4）。
- `idempotency_record` 新增 `result_ref`，指向幂等键对应的预约，用于重试回放。

> 说明：预约与宠物/用户之间不建跨模块外键，归属经 `owner.OwnerApi` / `identity.IdentityApi` 校验，保持模块数据所有权独立。
