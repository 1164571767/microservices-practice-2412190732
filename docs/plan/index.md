# PetCare 任务拆分与验收标准

> 依据：`README.md`、`CLAUDE.md`、`.claude/rules/`、`docs/tech-stack.md`、`docs/api/error-codes.md`、`docs/adr/`。
> 目标：把系统拆成**一个闭环小功能**粒度的任务，每个任务独立可交付、可验证。

## 1. 拆分原则

- **一个任务 = 一个可运行的业务闭环**：能独立编译、测试、验收，不跨多个模块同时开工。
- **垂直切片**：每个任务同时包含 migration → entity → service → controller → 测试，而不是先做完整张数据库表再统一写接口。
- **边界不破**：跨模块只走 Module API 或领域事件（见 ADR-0003），禁止跨模块访问其他模块的 Repository / Entity 或内部子包。
- **正确性优先**：涉及事务、并发、幂等、权限的任务，必须带对应测试（见 ADR-0002）。

## 2. 完成定义（DoD）

一个任务只有全部满足才算完成：

- [ ] `mvn -B verify` 通过（含 Modulith `verify()`、ArchUnit、Spotless、JaCoCo、Enforcer）。
- [ ] 核心 API 测试覆盖：成功、参数校验失败、越权、资源不存在、业务错误（按适用）。
- [ ] 涉及并发/幂等/非法状态者，有对应测试（禁止用 `sleep` 掩盖并发）。
- [ ] 数据库变更通过 Flyway migration；关键字段有 `NOT NULL / UNIQUE / FK / CHECK` 约束。
- [ ] 新增/变更 API 已同步 OpenAPI；新增业务错误码已更新 `docs/api/error-codes.md`。
- [ ] 未执行的检查明确标注 `NOT VERIFIED`。

## 3. 里程碑与任务总表

阶段顺序即依赖顺序；同一里程碑内任务通常可并行。

| 编号 | 任务 | 模块 | 关键验收标准 |
|---|---|---|---|
| **M0 工程基础** |
| T-01 | 工程骨架与运行状态接口 | system | ✅ 见 §4.1 |
| T-02 | 统一错误模型与全局异常处理 | shared | ✅ 见 §4.2 |
| T-03 | 数据库接入（MySQL + Flyway + JPA） | shared | ✅ 见 §4.3 |
| T-04 | 可观测性基线（traceId + metrics + 日志） | shared | ✅ 见 §4.4 |
| **M1 认证与权限**（identity） |
| T-05 | 用户注册 | identity | 手机号/用户名唯一；密码 BCrypt；重复注册返回冲突 code |
| T-06 | 登录与 JWT 签发/刷新 | identity | 正确凭据得 access+refresh；错误凭据返回 `AUTH_INVALID_CREDENTIALS` |
| T-07 | 受保护接口与 RBAC 鉴权 | identity | 无 token→401；角色不符→`AUTH_FORBIDDEN` |
| T-08 | 资源 ownership/scope 校验 | identity | 越权访问他人资源返回 `AUTH_FORBIDDEN`（有测试） |
| T-09 | 身份 Module API | identity | 提供当前用户/角色查询，供其他模块经根包调用 |
| **M2 主人与宠物档案**（owner） |
| T-10 | 主人档案管理 | owner | 本人可读写自己档案；仅员工可读他人 |
| T-11 | 宠物档案 CRUD | owner | 品种/年龄/体重校验；归属主人校验 |
| T-12 | 疫苗与过敏史 | owner | 记录可查询；与宠物归属一致 |
| T-13 | 宠物病历索引查询 | owner | 经 medical Module API 只读聚合，不直连其表 |
| **M3 排班与预约**（appointment） |
| T-14 | 医生排班维护（管理员） | appointment | 排班时段不重叠（DB 约束）；仅管理员可改 |
| T-15 | 可预约时间段查询 | appointment | 返回按排班切分的可选时段，已约满不返回 |
| T-16 | 创建预约 + 冲突检测 | appointment | DB 唯一键兜底；并发重复预约只有一个成功；幂等键去重 |
| T-17 | 预约取消/改期 | appointment | 状态机约束；非法状态返回 `APPOINTMENT_INVALID_STATE` |
| T-18 | 签到与候诊队列 | appointment | 仅到院可签到；发布叫号事件；非法流转被拒 |
| **M4 门诊病历与处方**（medical） |
| T-19 | 接诊记录与病历 | medical | 仅接诊医生可写；症状/诊断/医嘱结构化存储 |
| T-20 | 处方开具 | medical | 关联病历与药品；仅接诊医生可开 |
| T-21 | 历史病历查询 | medical | 主人仅看自己宠物；分页；无 N+1 |
| **M5 药品与库存**（pharmacy） |
| T-22 | 药品目录维护 | pharmacy | 名称/规格唯一；仅药房/管理员可改 |
| T-23 | 库存查询与调整 | pharmacy | `CHECK(quantity>=0)`；调整留痕可追溯 |
| T-24 | 处方发药扣库存 | pharmacy | 并发扣减不超卖；幂等；不足返回 `INSUFFICIENT_STOCK` |
| T-25 | 低库存预警 | pharmacy | 低于阈值发布事件并生成通知 |
| **M6 收费与账单**（billing） |
| T-26 | 收费项目维护 | billing | 金额 `NUMERIC(19,2)` 且 `>=0`；仅管理员可改 |
| T-27 | 生成账单 | billing | 账单金额一致性；关联病历/处方 |
| T-28 | 模拟支付 | billing | 幂等；重复支付返回 `PAYMENT_ALREADY_PAID`；状态机 |
| T-29 | 退款/作废与记录查询 | billing | 非法状态被拒；操作可审计 |
| **M7 通知**（notification） |
| T-30 | 通知模块与事件订阅 | notification | 订阅预约成功/收费成功事件生成站内通知；消费幂等 |
| T-31 | 候诊叫号通知 | notification | 消费叫号事件并落通知 |
| T-32 | 复诊提醒 | notification | 定时生成提醒；重复执行幂等 |
| **M8 统计报表**（reporting） |
| T-33 | 门诊量与医生工作量 | reporting | 只读投影，不写业务模块 |
| T-34 | 收入统计 | reporting | 与账单口径一致 |
| T-35 | 药品消耗统计 | reporting | 与出入库流水一致 |
| **M9 工程收尾** |
| T-36 | OpenAPI 与接口文档同步 | 全部 | `/v3/api-docs` 覆盖全部接口且与实现一致 |
| T-37 | compose 全链路与运行手册 | 全部 | 一条命令起 PG 并跑通核心流程 |
| T-38 | 安全与并发复查 | 全部 | 跑 `/security-review`、`/concurrency-review` 并修复问题 |
| T-39 | 服务提取评估（非承诺） | 候选模块 | 输出评估结论，不改代码 |

## 4. 任务详情

### 4.1 T-01 工程骨架与运行状态接口 ✅ 已完成

- **目标**：搭出可运行、可测试、带质量闸门的工程骨架，并提供验证运行状态的 GET 接口。
- **模块**：`system`（技术模块）。
- **依赖**：无。
- **交付物**：
  - `monolith/` Maven 工程，Java 25 + Spring Boot 4.0.8，group/package = `com.zjgsu.sfy`。
  - `GET /api/system/status` 返回 `{application,status,version}`。
  - 启动测试、接口测试、Modulith 结构校验、ArchUnit 规则。
  - Actuator `health`（含 liveness/readiness 分组）、`docker-compose.yml`（当前无服务，MySQL 本机原生）、`.env.example`。
- **验收标准**（已达成）：
  - [x] `mvn -B verify` 通过：6 个测试全绿（含 Modulith `verify()` 与 ArchUnit）。
  - [x] 应用可启动，`GET /api/system/status` → 200 `{"application":"monolith","status":"UP","version":"0.0.1-SNAPSHOT"}`。
  - [x] `GET /actuator/health` → 200 `UP`；`/actuator/health/readiness` → 200 `UP`。
- **验证命令**：
  ```bash
  cd monolith
  mvn -B verify
  java -jar target/monolith-0.0.1-SNAPSHOT.jar   # 另开终端 curl
  curl -s http://localhost:8080/api/system/status
  ```
- **备注**：本地 Maven Central 不可达，本地用系统 `mvn`（aliyun 镜像）；`./mvnw` 仅 CI 用。

### 4.2 T-02 统一错误模型与全局异常处理

- **目标**：统一错误响应结构，映射 `docs/api/error-codes.md` 的稳定 code。
- **模块**：`common`。
- **验收标准**（已完成）：
  - [x] 错误响应含 `code`（稳定机器码）与 `message`，客户端只需依赖 `code`。
  - [x] `@RestControllerAdvice` 处理：参数校验 → `VALIDATION_FAILED`；找不到 → `RESOURCE_NOT_FOUND`；未捕获 → `INTERNAL_ERROR`（不外泄堆栈/SQL/路径）。
  - [x] 至少一个测试接口可复现上述各 code。
- **实现**：`common.error` 包 `ErrorCode` / `BusinessException` / `ApiErrorResponse`，`common.api.GlobalExceptionHandler`；测试用 test 源中的 `ErrorProbeController`。
- **验证**：`mvn -B verify` 绿；`GlobalExceptionHandlerTest` 4 个用例断言 `$.code`。

### 4.3 T-03 数据库接入（MySQL + Flyway + JPA）

- **目标**：接入真实数据库与迁移，建立集成测试基线。
- **模块**：`common`。
- **状态**：✅ 已完成并验证（连接本机 MySQL 8.0，`localhost:3306`）。
- **方案调整（已定）**：集成测试与应用连接**本机真实 MySQL**（不用 Testcontainers，见 ADR-0004 修订）。
- **连接方式**：本机 MySQL 8，凭据经 `.env` 注入 `DB_NAME` / `DB_USER` / `DB_PASSWORD`；`DB_HOST` / `DB_PORT` 默认 `localhost:3306`。
- **已实现**：
  - 依赖：`spring-boot-starter-data-jpa`、`spring-boot-starter-flyway`、`flyway-mysql`、`mysql-connector-j`。
  - 配置：`ddl-auto=validate`、`open-in-view=false`；`spring.config.import` 读取 `monolith/.env`（gitignore）。
  - `V1__init.sql` 建 `idempotency_record`（`UNIQUE(idempotency_key)` + `CHECK(length>0)`）；实体用 UUIDv7（`@UuidGenerator(Style.VERSION_7)`，Hibernate 7.2 已确认可用）+ `IdempotencyRecordRepository`。
  - 集成测试 `IdempotencyRecordPersistenceTest`（真实 PG：写入 + 唯一键冲突）。
- **验收标准**：
  - [x] 引入 JPA / Flyway / MySQL 依赖。
  - [x] `ddl-auto=validate`、`open-in-view=false`；`V1__init.sql` 建表并带约束。
  - [x] 集成测试连接真实 PG 通过（Flyway 应用 V1；唯一键冲突按预期抛出）。
  - [x] 本机 MySQL 启动后应用可连库启动（Flyway 报告 schema 已最新，应用正常启动）。
- **验证**：`mvn -B verify` 全绿。

### 4.4 T-04 可观测性基线

- **模块**：`common`（不依赖数据库，已先行完成）。
- **验收标准**（已完成）：
  - [x] 引入 Micrometer Tracing（**Brave bridge** + `spring-boot-micrometer-tracing-brave`），日志行包含 `traceId`（形如 `[<traceId>-<spanId>]`）。
  - [x] 状态接口用结构化字段记录业务日志并暴露 metric `petcare.system.status.requests`。
  - [x] 非本地 profile（`prod`）输出 JSON（ECS）结构化日志，含 `traceId`/`spanId`。
  - [~] 敏感字段脱敏：当前无敏感字段进日志，规则见 `.claude/rules/backend/security.md`，留待 `identity` 落地时验证。
- **验证**：默认 profile 日志实测含 traceId；`/actuator/metrics/petcare.system.status.requests` 返回 COUNT；`--spring.profiles.active=prod` 输出 JSON。
- **备注**：OTel bridge 不写 SLF4J MDC，故改用 Brave，详见 `docs/tech-stack.md` §9。

### 4.5 T-05 ~ T-09 认证与权限（identity）✅ 已完成

| 编号 | 目标 | 关键验收标准 |
|---|---|---|
| T-05 | 用户注册 | 唯一约束兜底重复注册；密码 BCrypt；返回创建成功、无明文密码入库 |
| T-06 | 登录与 JWT 签发/刷新 | access+refresh 签发；错误凭据 `AUTH_INVALID_CREDENTIALS`；refresh 可轮换 |
| T-07 | 受保护接口与 RBAC | 无 token 401；角色不符 `AUTH_FORBIDDEN`；权限在服务端校验 |
| T-08 | ownership/scope 校验 | 越权访问他人资源被拒并有测试 |
| T-09 | 身份 Module API | 根包暴露当前用户/角色只读查询 |

**已实现**：

- 根包 Module API：`IdentityApi` / `UserProfile` / `CurrentUser`；`Role` / `UserStatus` 收于 `identity/common`，其他模块改用 `isClinicStaff()` 等语义谓词；内部 `controller/dto/service/repository/entity/config`（service 细分 `command/` 与 `result/`）。
- 注册固定为 `OWNER`（公开注册不授予员工/管理员权限）；密码 `DelegatingPasswordEncoder`（BCrypt）；用户名大小写不敏感唯一。
- access token 为 HS256 JWT（仅身份声明，`typ=access`）；refresh token 为高熵不透明串，**仅存 SHA-256 哈希**，轮换用条件 UPDATE 原子撤销（并发只成功一次）。
- 受保护接口默认拒绝匿名；角色用 `@PreAuthorize`，资源 ownership 在应用层二次校验。
- ADMIN 可创建任意角色账号；全新环境可用环境变量引导首个管理员（幂等，凭据不入日志）。
- 文档：`docs/api/identity.md`、错误码 `docs/api/error-codes.md`、决策 `docs/adr/0005-authn-authz-jwt-rbac.md`。

- **验证**：`mvn -B verify`；`AuthFlowIntegrationTest`、`RbacAndOwnershipIntegrationTest`、`RefreshRotationConcurrencyTest` 覆盖成功/校验失败/401/越权/业务错误/并发轮换；`UserTest`/`TokenServiceTest`/`JwtPropertiesTest` 为纯单元测试。

### 4.6 T-10 ~ T-13 主人与宠物档案（owner）

依赖 T-05/T-09。验收要点：档案归属校验、字段校验（品种/年龄/体重）、仅员工可读他人档案；病历索引经 `medical` Module API 只读获取，不直连其表。

| 编号 | 目标 | 关键验收标准 |
|---|---|---|
| T-10 | 主人档案管理 | 本人 `PUT/GET /api/owner/me`（upsert）；越权读他人档案 `AUTH_FORBIDDEN`；员工可读 |
| T-11 | 宠物档案 CRUD | 出生日期/体重/品种校验；`owner_id` 归属校验；员工只读 |
| T-12 | 疫苗与过敏史 | 记录可查询；仅归属主人可写；`severity` 枚举校验 |
| T-13 | 宠物病历索引查询 | 经 `MedicalApi` 只读聚合，不直连 medical 表 |

**已实现**：

- 模块结构：根包 `OwnerApi` / `PetSummary`；内部 `controller/dto/service/repository/entity`（service 细分 `command/`）。
- `owner.user_id` 唯一且**不设对 `users` 的外键**（数据所有权独立，便于后续拆分）；宠物/疫苗/过敏外键为模块内 `FK ... ON DELETE CASCADE`。
- 越权统一 `AUTH_FORBIDDEN`：主人仅能读写自己档案与宠物；`STAFF/DOCTOR/ADMIN` 只读他人档案/宠物。
- 列表接口分页（`PageResponse`），排序服务端固定，不接受客户端排序字段。
- **T-13 方案**：先在 `medical` 根包固化只读 `MedicalApi` 契约，`MedicalApiImpl` 为占位实现（返回空），M4 落地真实病历表；owner 不直连 medical 表。
- 文档：`docs/api/owner.md`；无新增错误码（复用 `AUTH_FORBIDDEN` / `RESOURCE_NOT_FOUND` / `VALIDATION_FAILED`）。

- **验证**：`ArchitectureTest`、`ModularityTests`（Modulith `verify()`）、`OwnerTest`、`PetTest` 通过；`owner.api.*` 集成测试覆盖成功/校验失败/404/越权/员工只读（见 §5 说明）。

### 4.7 T-14 ~ T-18 排班与预约（appointment）

依赖 T-05/T-09/T-10（宠物）。核心正确性任务：

| 编号 | 目标 | 关键验收标准 |
|---|---|---|
| T-14 | 医生排班维护（管理员） | 同医生时段不重叠（DB 排除约束）；仅 ADMIN 可改；非医生 `OPERATION_NOT_ALLOWED` |
| T-15 | 可预约时间段查询 | 按 `slotMinutes` 切分排班时段；已约满不返回 |
| T-16 | 创建预约 + 冲突检测 | DB 唯一键 + 排班悲观锁兜底；并发仅一个成功；幂等键去重且不同请求 `IDEMPOTENCY_CONFLICT` |
| T-17 | 预约取消/改期 | 状态机；非法状态 `APPOINTMENT_INVALID_STATE`；取消后释放时段 |
| T-18 | 签到与候诊队列 | 仅 SCHEDULED 可签到；叫号发布 `QueueNumberCalled` 事件；非法流转被拒 |

**已实现**：

- 模块结构：根包 `QueueNumberCalled`（事件）；内部 `controller/dto/service/repository/entity`（service 细分 `command/`、`result/`）。
- 排班与预约在 MySQL 下用生成列 `active_start_at` + 唯一键保证同医生时段不双订；取消时生成列为 NULL，时段可重新预约。任意区间重叠由 Service 悲观锁 + 重叠查询兜底。
- 幂等：创建预约支持 `Idempotency-Key`；`common.idempotency_record` 增加 `result_ref`，同键同请求回放同一预约，同键异请求 `IDEMPOTENCY_CONFLICT`。
- 归属：主人仅能操作自己宠物的预约；员工可代操作；签到/队列/叫号限 `STAFF/DOCTOR/ADMIN`。
- 依赖方向：appointment → owner（`OwnerApi`）、identity（`IdentityApi`），无跨模块表访问。
- 文档：`docs/api/appointment.md`；错误码 `APPOINTMENT_CONFLICT/NOT_AVAILABLE/INVALID_STATE`、`IDEMPOTENCY_CONFLICT`、`OPERATION_NOT_ALLOWED`（`error-codes.md` 已列，本次落到 `ErrorCode`）。

- **验证**：`AppointmentConcurrencyTest`（并发同时段仅 1 成功）、`AppointmentBookingIntegrationTest`（成功/越权/`APPOINTMENT_CONFLICT`/`APPOINTMENT_NOT_AVAILABLE`/幂等回放/`IDEMPOTENCY_CONFLICT`）、`AppointmentLifecycleIntegrationTest`（取消/改期/签到 + 非法状态 + 取消释放时段）、`QueueIntegrationTest`（队列/叫号事件/空队列）、`DoctorScheduleIntegrationTest`（排班重叠、非医生、可约时段）；`AppointmentTest`/`DoctorScheduleTest` 为状态机与不变式单测。

### 4.8 T-19 ~ T-21 门诊病历与处方（medical）✅ 已完成

依赖 T-16/T-18（预约）。验收要点：仅接诊医生可写病历/处方；处方关联病历与药品；历史病历分页且 ownership 校验。

| 编号 | 目标 | 关键验收标准 |
|---|---|---|
| T-19 | 接诊记录与病历 | 仅接诊医生可写；症状/诊断/医嘱独立字段存储；一次预约至多一份病历 |
| T-20 | 处方开具 | 关联病历；药品信息内嵌（M5 未落地）；仅接诊医生可开；一份病历至多一张处方 |
| T-21 | 历史病历查询 | 主人仅看自己宠物（经 owner 端点）；临床历史分页；无 N+1（有测试断言） |

**已实现**：

- 模块结构：根包 `MedicalApi` / `MedicalRecordSummary` / `AppointmentLookup`（端口）；内部 `controller/dto/service/repository/entity`（service 细分 `command/`、`result/`、`policy/`）。
- 表：`medical_record`（`UNIQUE(appointment_id)`）、`prescription`（`UNIQUE(medical_record_id)`）、`prescription_item`；`V5__medical.sql`。`appointment_id/pet_id/doctor_id` 不建跨模块外键。
- 写侧：角色须 `DOCTOR` 且为接诊医生；`petId/doctorId` 取自预约。基线校验由 `MedicalWritePolicy` 保证，`MedicalWriteGuard` 作为可插拔追加校验。
- 依赖无环：`medical.AppointmentLookup` 由 `appointment` 实现（方向 `appointment → medical`），避免 `medical→appointment→owner→medical` 环，详见 ADR-0006。
- T-21 无 N+1：某页病历取回后，处方与明细各一次批量加载；`MedicalHistoryIntegrationTest` 用 Hibernate 统计断言查询条数不随记录数增长。
- 文档：`docs/api/medical.md`；决策 `docs/adr/0006-medical-appointment-port.md`；无新增错误码（复用 `AUTH_FORBIDDEN` / `RESOURCE_NOT_FOUND` / `OPERATION_NOT_ALLOWED` / `VALIDATION_FAILED`）。

- **验证**：`MedicalRecordIntegrationTest`（成功/非接诊医生 403/非医生 403/预约不存在 404/校验失败/重复病历冲突）、`PrescriptionIntegrationTest`（成功/越权/重复处方冲突/病历不存在/校验失败）、`MedicalHistoryIntegrationTest`（倒序含处方/分页/临床角色校验/无 N+1）、`MedicalRecordTest`/`PrescriptionItemTest`（领域不变式）。

### 4.9 T-22 ~ T-25 药品与库存（pharmacy）

核心正确性任务 T-24：处方发药扣库存需**并发不超卖 + 幂等 + `INSUFFICIENT_STOCK`**，并有并发与幂等测试；T-25 低库存阈值触发事件。

### 4.10 T-26 ~ T-29 收费与账单（billing）

核心正确性任务 T-28：模拟支付幂等、`PAYMENT_ALREADY_PAID`、状态机（`PAYMENT_INVALID_STATE`）；金额 `NUMERIC(19,2)` 且 `CHECK(amount>=0)`。

### 4.11 T-30 ~ T-32 通知（notification）

依赖各业务模块事件。验收要点：仅通过事件订阅（无跨模块直连）；消费幂等；复诊提醒定时任务重复执行不产生重复通知。

### 4.12 T-33 ~ T-35 统计报表（reporting）

只读投影，不反向写业务模块；统计口径与账单/流水一致。

### 4.13 T-36 ~ T-39 工程收尾

- T-36 OpenAPI 全量同步；T-37 compose 全链路 + 运行手册；T-38 跑安全/并发复查并修复；T-39 服务提取评估（非本期承诺，只出结论）。

## 5. 当前进展

- ✅ **T-01**：骨架可构建、可启动，状态接口与健康检查均返回 200。
- ✅ **T-02**：统一错误模型（`ErrorCode`/`BusinessException`/`ApiErrorResponse`/`GlobalExceptionHandler`）；`VALIDATION_FAILED`/`RESOURCE_NOT_FOUND`/`INTERNAL_ERROR` 均有 MockMvc 用例覆盖。
- ✅ **T-03**：MySQL + Flyway + JPA 接入；`V1__init.sql` 建 `idempotency_record`（唯一键 + CHECK）；集成测试连真实 MySQL 通过（UUIDv7 落库 + 唯一键冲突按预期抛错）。
- ✅ **T-04**：日志含 traceId（`[<traceId>-<spanId>]`）；metric `petcare.system.status.requests` 经 `/actuator/metrics` 可见；`prod` profile 输出 ECS JSON 日志。
- ✅ **T-05 ~ T-09**：identity 模块落地注册 / 登录 / refresh 轮换 / RBAC / ownership / Module API；详见 §4.5。
- ✅ **T-10 ~ T-12**：owner 模块落地主人档案（upsert）、宠物档案 CRUD、疫苗/过敏史；归属校验 + 员工只读 + 分页；`V3__owner.sql`。详见 §4.6。
- ✅ **T-13**：`medical` 主数据已在 M4 落地；owner 经 `MedicalApi` 只读聚合拿到真实病历索引，越权校验与联调测试通过（`PetMedicalIndexIntegrationTest`）。
- ✅ **T-14 ~ T-18**：appointment 模块落地排班、可约时段、创建预约（幂等 + 冲突兜底）、取消/改期、签到/候诊队列与叫号事件；`V4__appointment.sql`。详见 §4.7。
- ✅ **T-19 ~ T-21**：medical 模块落地接诊病历、处方开具、历史病历查询；写侧可插拔接诊医生校验、历史查询无 N+1；`V5__medical.sql`。详见 §4.8。
- **M0 / M1 / M2 / M3 / M4 完成**。
- 认证需 `JWT_SECRET`（>= 32 字符）环境变量；`/actuator/metrics` 因安全基线收紧，现需认证后访问。
- **本次验证状态**：`mvn -B verify` 全绿（**137 测试**）：ArchUnit、Modulith `verify()`（含 medical 端口反转后仍无环）、Spotless、JaCoCo、Enforcer 全部通过；`V1`~`V5` 已在 MySQL 库 `petcare` 应用；medical 覆盖病历/处方写侧鉴权（非接诊医生 403）、重复预约病历/重复处方冲突、校验失败、历史病历倒序分页与**无 N+1**（Hibernate 统计断言），以及 owner 侧 T-13 联调（真实病历索引）。
- **环境提示**：本机 MySQL 8 的项目库为 `petcare`，`.env` 需设 `DB_NAME=petcare`、`DB_PORT=3306`、`DB_USER` / `DB_PASSWORD`。MySQL 无 `btree_gist`，`V4` 使用生成列唯一键。
- 下一步：**M5 药品与库存（T-22 ~ T-25，pharmacy 模块）**。
