# 第 04 周作业

> 本周题目：业务建模 / 单体应用实现 / 数据库持久化 / 测试与验证。
> 按作业要求，四个部分统一选取 **appointment（排班与预约）** 模块作为示例。
> 选取理由：该模块有 2 个核心实体（`DoctorSchedule`、`Appointment`）、明确的业务流程（创建预约）、可验证的业务规则（排班覆盖 + 防双订 + 状态机），REST 接口覆盖查询与创建，且已有集成 / 并发测试。

| 部分 | 产出 |
|---|---|
| 业务建模 | `docs/project-proposal.md` §6 |
| 单体应用实现 | 代码位于 `monolith/src/main/java/com/zjgsu/sfy/appointment/`；`README.md` 有启动命令与可直接运行的 curl |
| 数据库持久化 | `docs/database.md`；JPA Repository 位于 `appointment/repository/` |
| 测试与验证 | `monolith/src/test/java/com/zjgsu/sfy/appointment/` |

---

## 1. 业务建模

### 实现思路

本周实现的业务闭环是 **「管理员排班 → 主人查可约时段 → 主人为宠物创建预约 → 查询预约」**。从完整诊疗流程中截取「选择医生和可预约时间段 → 系统检查冲突 → 预约成功」三步。

参与流程的两个核心实体：

| 实体 | 主要字段 | 说明 |
|---|---|---|
| `DoctorSchedule` | `id`、`doctorId`、`startAt`、`endAt`、`slotMinutes` | 医生的可用时间窗口，按 `slotMinutes` 切分为时段 |
| `Appointment` | `id`、`petId`、`doctorId`、`startAt`、`endAt`、`status` | 一次预约，状态由实体方法流转 |

关系：`DoctorSchedule (1) —— (N) Appointment`。每条预约的时间区间必须被该医生的某条排班覆盖；同一医生同一时段最多一条未取消预约。两者不建数据库外键，归属由时间区间覆盖关系表达。

需要在 Service 中检查的业务规则：

> **预约必须落在该医生排班覆盖的时间窗口内，且同一医生在同一时间段最多存在一条未取消的预约。**

实现位置：

- `AppointmentService.requireWithinSchedule()`：查询排班并校验覆盖，不满足抛 `APPOINTMENT_NOT_AVAILABLE`；
- `AppointmentService.book()`：捕获 `DataIntegrityViolationException` 并映射为 `APPOINTMENT_CONFLICT`；
- 数据库排除约束作为并发最终守卫（见第 3 部分）。

### 运行命令

文档无需运行；查看：

```bash
sed -n '/## 6. 第 04 周实现范围/,/## 7./p' docs/project-proposal.md
```

### 结果

`docs/project-proposal.md` 已补充：本周核心流程、两个实体及关系、Service 业务规则（含落地代码位置），并保留原有项目背景、用户角色与模块划分。

---

## 2. 单体应用实现

### 实现思路

在现有 `monolith/` 工程内实现，未新建工程。分层为 `controller / service / repository / entity / dto`，Controller 只负责 HTTP 输入输出，业务规则在 Service 中：

| 类 | 路径 | 职责 |
|---|---|---|
| `DoctorScheduleController` | `appointment/controller/` | 排班创建 / 查询 / 删除、可约时段查询 |
| `AppointmentController` | `appointment/controller/` | 预约创建 / 查询 / 取消 / 改期 / 签到 |
| `QueueController` | `appointment/controller/` | 候诊队列与叫号 |
| `DoctorScheduleService` | `appointment/service/` | 排班创建、时段切分 |
| `AppointmentService` | `appointment/service/` | 预约用例、归属校验、冲突映射、状态机 |
| `Appointment` / `DoctorSchedule` | `appointment/entity/` | 实体与领域规则 |
| `AppointmentRepository` / `DoctorScheduleRepository` | `appointment/repository/` | Spring Data JPA |

接口（均以 `/api/...` 为前缀，覆盖查询与创建）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/appointment/schedules` | 创建排班（ADMIN） |
| GET | `/api/appointment/schedules?doctorId=` | 查询排班 |
| GET | `/api/appointment/availability?doctorId=&date=` | 查询可预约时段 |
| POST | `/api/appointment/appointments` | 创建预约 |
| GET | `/api/appointment/appointments/{id}` | 查询预约详情 |
| GET | `/api/appointment/pets/{petId}/appointments` | 查询某宠物预约列表 |
| POST | `/api/appointment/appointments/{id}/cancel` | 取消预约 |

### 运行命令

```bash
cd monolith
JWT_SECRET='local-dev-secret-at-least-32-characters!!' \
BOOTSTRAP_ADMIN_USERNAME=admin \
BOOTSTRAP_ADMIN_PASSWORD='AdminPassw0rd!' \
BOOTSTRAP_ADMIN_PHONE=13800000000 \
mvn spring-boot:run
```

完整可复制的 curl 链路见 `README.md`「预约与排班」一节（注册 → 建档 → 建宠物 → 管理员建医生 / 排班 → 查时段 → 创建预约 → 查询）。

### 结果（本地实测）

```text
# 创建预约 → 201
{"id":"01a14799-eada-...","petId":"01a14799-e6fb-...","doctorId":"01a14799-e908-...",
 "startAt":"2026-11-10T01:00:00Z","endAt":"2026-11-10T01:30:00Z",
 "status":"SCHEDULED","checkedInAt":null,"calledAt":null}

# 同医生同时段再次预约 → 409 业务规则拦截
{"code":"APPOINTMENT_CONFLICT","message":"该时段已被预约"} [HTTP 409]

# 时间不在排班内 → 409 业务规则拦截
{"code":"APPOINTMENT_NOT_AVAILABLE","message":"该时段不在可预约范围内"} [HTTP 409]

# 查询宠物预约列表 → 200
[{"id":"01a14799-eada-...","status":"SCHEDULED", ...}]
```

---

## 3. 数据库持久化

### 实现思路

- 数据访问统一放在 `repository` 包：`AppointmentRepository`、`DoctorScheduleRepository`（Spring Data JPA）。
- API 输入使用 DTO：`dto/req/BookAppointmentRequest`、`CreateScheduleRequest`、`RescheduleRequest`；Controller 不直接操作 Entity 之外的持久化细节。
- 关键约束落到数据库（详见 `docs/database.md`）：
  - `doctor_schedule`：`CHECK (end_at > start_at)`、`CHECK (slot_minutes > 0 AND <= 480)`、`UNIQUE (doctor_id, start_at)`；任意区间重叠由 Service 检查。
  - `appointment`：`CHECK (end_at > start_at)`、`CHECK (status IN (...))`、生成列 `active_start_at` + `UNIQUE (doctor_id, active_start_at)`（取消时为 `NULL`，释放时段）。
  - 任意区间重叠：`AppointmentService` 先 `SELECT ... FOR UPDATE` 锁定排班，再查询重叠预约（MySQL 无 `EXCLUDE`）。
- 可重复的测试数据准备：集成测试基类用代码插入夹具（用户、主人、宠物、排班、预约），每个用例使用随机用户名 / UUID，重复执行互不冲突。
- 当前数据库为 **MySQL 8.0**（Flyway 管理，`V4__appointment.sql`）。PostgreSQL 的排除约束改用「生成列唯一键 + Service 悲观锁」，详见 `docs/database.md` 与 ADR-0008。

### 运行命令

```bash
# 查看表结构与约束
cd monolith
set -a; . ./.env; set +a
mysql -h localhost -P 3306 -u "$DB_USER" -p "$DB_NAME" -e 'SHOW CREATE TABLE doctor_schedule\G'
mysql -h localhost -P 3306 -u "$DB_USER" -p "$DB_NAME" -e 'SHOW CREATE TABLE appointment\G'

# 查看已写入的数据
mysql -h localhost -P 3306 -u "$DB_USER" -p "$DB_NAME" \
  -e "select id, pet_id, doctor_id, start_at, end_at, status from appointment order by created_at desc limit 3;"
```

### 结果

`SHOW CREATE TABLE appointment` 实际输出（关键部分）：

```text
CREATE TABLE `appointment` (
  `id` binary(16) NOT NULL,
  `pet_id` binary(16) NOT NULL,
  `doctor_id` binary(16) NOT NULL,
  `start_at` datetime(6) NOT NULL,
  `end_at` datetime(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  `active_start_at` datetime(6) GENERATED ALWAYS AS (
      (case when (`status` <> _utf8mb4'CANCELLED') then `start_at` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_appointment_active_slot` (`doctor_id`,`active_start_at`),
  CONSTRAINT `ck_appointment_range` CHECK ((`end_at` > `start_at`)),
  CONSTRAINT `ck_appointment_status`
      CHECK ((`status` in (_utf8mb4'SCHEDULED',_utf8mb4'CHECKED_IN',_utf8mb4'CALLED',_utf8mb4'CANCELLED')))
)
```

查询结果示例：

```text
                  id                  |              pet_id              | status
--------------------------------------+----------------------------------+-----------
 01a14799-eada-7cd6-8fd2-38134b43f3dd | 01a14799-e6fb-703e-9ab1-5b332aa17c97 | SCHEDULED
```

---

## 4. 测试与验证

### 实现思路

测试覆盖三层：

| 层次 | 测试类 | 验证内容 |
|---|---|---|
| Repository / 持久化 | `AppointmentBookingIntegrationTest` 等（真实 MySQL） | 写入、唯一约束、查询 |
| Service 业务规则 | `AppointmentConcurrencyTest`、`AppointmentTest`、`DoctorScheduleTest` | 并发双订仅 1 成功、状态机、排班覆盖 |
| API | `AppointmentBookingIntegrationTest`、`AppointmentLifecycleIntegrationTest`、`DoctorScheduleIntegrationTest`、`QueueIntegrationTest` | 成功 / 参数错误 / 越权 / 业务错误 / 状态码 |

测试数据由测试基类在运行时准备，随机用户名 / UUID，可重复执行。

### 运行命令

```bash
cd monolith
./mvnw test
```

### 结果

```text
[INFO] Tests run: 138, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  02:19 min
```

appointment 模块分项：

```text
AppointmentBookingIntegrationTest    6
AppointmentLifecycleIntegrationTest  7
DoctorScheduleIntegrationTest        6
QueueIntegrationTest                 4
AppointmentTest                      8
DoctorScheduleTest                   4
AppointmentConcurrencyTest           1
----------------------------------------
合计                                36
```

> 说明：首次执行 `./mvnw` 会下载依赖，耗时较长；后续执行明显更快。测试连接本机 MySQL 8，重复执行通过。

---

