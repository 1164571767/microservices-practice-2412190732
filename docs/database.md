# 数据库设计（第 04 周：预约与排班）

> 当前实现：**MySQL 8.0**（utf8mb4），Schema 由 Flyway 管理（`monolith/src/main/resources/db/migration/V1~V5`）。
> 迁移脚本：`V4__appointment.sql`（本模块）、`V1__init.sql`（幂等记录）。
> 数据库选型的演进见 ADR-0004（PostgreSQL）与 **ADR-0008（迁移到 MySQL，当前生效）**。

## 1. 范围与数据所有权

本周选择 **appointment（排班与预约）** 模块，核心是两张业务表：

| 表 | 所属模块 | 作用 |
|---|---|---|
| `doctor_schedule` | appointment | 医生的一段可用时间窗口 |
| `appointment` | appointment | 一次预约及其状态 |

关联的跨模块数据（不在本模块建表）：

| 数据 | 所属模块 | 引用方式 |
|---|---|---|
| 宠物 `pet` | owner | `appointment.pet_id`，**不建跨模块外键**，归属经 `OwnerApi` 校验 |
| 医生 `users` | identity | `doctor_id`，**不建跨模块外键**，角色经 `IdentityApi` 校验 |

这样保证每个模块只拥有自己的数据，为后续服务拆分保留边界。

## 2. 实体关系

```text
owner.pet (1) ──────────────┐
                            │ 引用（无外键）
                            ▼
identity.users (医生) ──┐   appointment (N)
                        │        ▲
                        │        │  时间区间必须被排班覆盖
                        ▼        │
                 doctor_schedule (1) ── 同医生时段不可重叠
```

- `doctor_schedule` 与 `appointment` 的**逻辑关系是 1 : N**：一条排班可被多条预约使用；每条预约的时间区间必须落在该医生的某条排班内。
- 两者之间**不建外键**：归属由 `(doctor_id, 时间区间)` 的覆盖关系表达，而不是行引用。
- 同一医生在同一时间区间最多一条**未取消**的预约。

## 3. 表：`doctor_schedule`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BINARY(16)` | **PK** | 主键（应用层 UUIDv7） |
| `doctor_id` | `BINARY(16)` | `NOT NULL` | 医生用户 ID（identity 模块） |
| `start_at` | `DATETIME(6)` | `NOT NULL` | 排班开始（UTC） |
| `end_at` | `DATETIME(6)` | `NOT NULL` | 排班结束（UTC） |
| `slot_minutes` | `INT` | `NOT NULL` | 时段切分粒度（分钟） |
| `created_at` | `DATETIME(6)` | `NOT NULL` | 创建时间 |
| `updated_at` | `DATETIME(6)` | `NOT NULL` | 更新时间 |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | JPA `@Version` 乐观锁 |

约束：

| 约束 | 定义 | 目的 |
|---|---|---|
| `ck_doctor_schedule_range` | `CHECK (end_at > start_at)` | 时间区间合法 |
| `ck_doctor_schedule_slot` | `CHECK (slot_minutes > 0 AND slot_minutes <= 480)` | 时段粒度合理 |
| `uq_doctor_schedule_start` | `UNIQUE (doctor_id, start_at)` | 同一医生同一开始时刻不可重复 |

索引：`idx_doctor_schedule_doctor_start (doctor_id, start_at)`。

> 任意区间的排班重叠（如 00:00–04:00 与 02:00–06:00）由 `DoctorScheduleService` 在事务内查询后拒绝，返回 `APPOINTMENT_CONFLICT`。

## 4. 表：`appointment`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BINARY(16)` | **PK** | 主键（应用层 UUIDv7） |
| `pet_id` | `BINARY(16)` | `NOT NULL` | 宠物 ID（owner 模块） |
| `doctor_id` | `BINARY(16)` | `NOT NULL` | 医生用户 ID（identity 模块） |
| `start_at` | `DATETIME(6)` | `NOT NULL` | 预约开始（UTC） |
| `end_at` | `DATETIME(6)` | `NOT NULL` | 预约结束（UTC） |
| `status` | `VARCHAR(20)` | `NOT NULL` + `CHECK` | `SCHEDULED` / `CHECKED_IN` / `CALLED` / `CANCELLED` |
| `checked_in_at` | `DATETIME(6)` | 可空 | 到院签到时间 |
| `called_at` | `DATETIME(6)` | 可空 | 叫号时间 |
| `active_start_at` | `DATETIME(6)` | `GENERATED ... STORED` | 未取消时为 `start_at`，取消时为 `NULL`，仅用于唯一约束 |
| `created_at` | `DATETIME(6)` | `NOT NULL` | 创建时间 |
| `updated_at` | `DATETIME(6)` | `NOT NULL` | 更新时间 |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | 乐观锁 |

约束：

| 约束 | 定义 | 目的 |
|---|---|---|
| `ck_appointment_range` | `CHECK (end_at > start_at)` | 时间区间合法 |
| `ck_appointment_status` | `CHECK (status IN (...))` | 状态枚举兜底 |
| `uq_appointment_active_slot` | `UNIQUE (doctor_id, active_start_at)` | **同一医生同一开始时刻不双订**；取消后 `NULL` 不占唯一键，时段可重约 |

索引：`idx_appointment_doctor_start (doctor_id, start_at)`、`idx_appointment_pet (pet_id)`。

### 并发控制策略

MySQL 不支持 PostgreSQL 的 `EXCLUDE USING gist`，改用两层守卫：

1. **数据库**：`uq_appointment_active_slot` 兜住「同一医生同一开始时刻」的并发插入；
2. **Service**：`AppointmentService.requireWithinSchedule()` 先用 `SELECT ... FOR UPDATE` 悲观锁定该医生的全部排班，再查询是否存在重叠的未取消预约，覆盖任意时间区间。

已取消预约的 `active_start_at` 为 `NULL`，InnoDB 唯一索引允许多个 `NULL`，因此取消后时段可重新预约。

## 5. 支撑表：`idempotency_record`

创建预约支持 `Idempotency-Key`，去重依赖：

| 字段 | 类型 | 约束 |
|---|---|---|
| `id` | `BINARY(16)` | **PK** |
| `idempotency_key` | `VARCHAR(255)` | **UNIQUE**、`CHECK (CHAR_LENGTH > 0)` |
| `request_hash` | `VARCHAR(64)` | `NOT NULL` |
| `result_ref` | `BINARY(16)` | 可空，指向首次创建的预约 |

同键同请求 → 回放同一预约；同键异请求 → `IDEMPOTENCY_CONFLICT`。

## 6. 测试数据准备

- 集成测试直接连接**本机 MySQL 8**，测试基类通过代码插入夹具（用户、医生、主人、宠物、排班、预约），不依赖手工 SQL。
- 每个用例使用随机用户名 / UUID，可**重复执行**。
- 相关测试：`AppointmentBookingIntegrationTest`、`AppointmentLifecycleIntegrationTest`、`DoctorScheduleIntegrationTest`、`QueueIntegrationTest`、`AppointmentConcurrencyTest`、`AppointmentTest`、`DoctorScheduleTest`。