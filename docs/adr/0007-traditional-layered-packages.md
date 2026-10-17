# ADR-0007: 模块内包结构采用传统分层（controller/service/repository）

## Status
Accepted

## Context
课程要求模块内部采用传统分层：模块下直接是 `controller` / `service` / `repository`。而此前实现沿用 DDD / 六边形分层 `api / application / domain / infrastructure`（ADR-0001、ADR-0003）。

现状盘点：
- 包结构变更会波及约 170 个源文件（main + test），是纯搬包 + 改 import，**不改业务行为**。
- `identity.Role` 被 `appointment`、`owner` 的 service 跨模块 import；若把它藏进 identity 的非根子包，Modulith `verify()` 会失败。
- 迁移工具（Flyway）与依赖方向无关，不受影响。

目标：满足教学要求的同时，**不放弃** ADR-0001/0003 已确立的模块边界强制与正确性优先设计。

## Decision
- 业务模块内部改为传统分层：
  `controller / dto/{req,resp} / service/{command,result,policy} / repository / entity / common / util / config`，仅在实际存在职责时创建目录。
- `shared` 重命名为全局 `common`，保持 OPEN 模块，只承载跨模块错误契约（`error/`、`api/`）与幂等（`idempotency/`），不放业务模型。
- 模块私有枚举 / 常量入**模块内** `common/`（如 `appointment/common/AppointmentStatus`、`identity/common/UserStatus`）；跨模块共享的错误码等入**全局** `common/`。
- **Module API 类型仍留在模块根包**（`IdentityApi`/`CurrentUser`/`UserProfile`、`OwnerApi`/`PetSummary`、`MedicalApi`/`MedicalRecordSummary`/`AppointmentLookup`、`QueueNumberCalled`），跨模块只允许访问根包类型或订阅事件。
- `Role` 收进 `identity/common`，**不再被其他模块 import**。`CurrentUser` / `UserProfile` 暴露语义谓词（`isClinicStaff()`、`isDoctor()`），下游用法由 `requester.hasAnyRole(STAFF, DOCTOR, ADMIN)` 改为 `requester.isClinicStaff()`。
- 保留 Spring Modulith `verify()`；ArchUnit 规则改写到新包名，并新增一条护栏：`identity` 之外的类不得依赖 `..identity.common..`。
- 跨模块端口适配器（`AppointmentLookupAdapter`，见 ADR-0006）落在 `service/`；此前 `infrastructure/` 中的配置类归入模块 `config/`。

## Consequences
优点：
- 满足教学要求的传统分层，package tree 直观：控制器 / 服务 / 仓库一目了然。
- 模块边界未削弱：Modulith `verify()` 与 ArchUnit 依旧在 `verify` 阶段阻断越界；跨模块仍只走根包 Module API 或事件。
- `Role` 不再跨模块外泄，角色词汇由 identity 独占，`identity.common` 由 ArchUnit 机械守护。
- 业务行为零改动：重构前后 `mvn -B verify` 全绿（测试总数由 **137 → 138**，差异仅来自新增的一条 ArchUnit 规则；原有用例与断言未增删）。

代价：
- `common` / `util` 层在旧规则中是**禁止**的垃圾桶包。本 ADR 放开后，其职责必须靠 review 约束：`common` 只放常量 / 枚举 / 公共信息，`util` 只放无状态工具。
- `medical/util/UniqueViolations` 因跨包使用需由 package-private 提升为 `public`。
- 其他模块的**测试**仍需以 `Set.of(Role.X)` seed 用户，继续 import `identity/common.Role`；测试不受 Modulith / ArchUnit 约束，属可接受残留。
- `dto` 由 `req` / `resp` 两子包承载，`service` 另有 `command` / `result` 子包，包数量较之前增加。

## Alternatives considered
- **保留 DDD `api/application/domain/infrastructure`**：不满足课程要求。否决。
- **顺带放宽模块边界（去掉 Modulith / ArchUnit，允许模块间直接注入 Service）**：与 ADR-0001、ADR-0003 及 `CLAUDE.md`「正确性优先、边界优先」直接冲突，且会丢失可执行的边界强制。否决。
- **`Role` 放入全局 `common` 作共享枚举**：改动最小，但 identity 不再独占角色词汇，全局 `common` 渗入业务语义。否决。
- **`Role` 收进 identity 并经 `IdentityApi` 暴露**（`identityApi.isClinicStaff(user)`）：封装更强，但 `OwnerService` / `PetService` 当前未注入 `IdentityApi`，需额外注入；而 `CurrentUser` / `UserProfile` 本就是 identity 的 Module API 类型且已携带角色，谓词挂在这里改动更小。否决，改用模块 API 类型上的语义谓词。
- **模块内不建 `common` / `util`**：会使 `Role` / `UniqueViolations` 无处安放，或被迫塞进 `entity` / `service`。否决。
