# ADR-0006: 以端口反转打破 medical 与 appointment 的循环依赖

## Status
Accepted

## Context
M4（门诊病历与处方，T-19 ~ T-21）要求「仅接诊医生可写」：写病历 / 开处方时需知道该预约的接诊医生与宠物归属。直觉做法是 medical 依赖 appointment 的 Module API。

但 ADR-0003 用 Modulith `verify()` 强制模块依赖构成**有向无环图**；现有依赖为 `owner → medical`（T-13 只读聚合）与 `appointment → owner`。于是：
- `medical → appointment` 形成 `medical → appointment → owner → medical` 环；
- `medical → owner` 形成 `owner → medical → owner` 环。

两种直接依赖都会让 `mvn verify` 失败。

## Decision
- medical 在自己的**根包**定义只读端口 `AppointmentLookup`（含 `AppointmentInfo` 子集视图：`id/petId/doctorId/status`）。
- 由 **appointment** 模块在 `service` 实现该端口（`AppointmentLookupAdapter`），复用其 `AppointmentRepository`。
- 依赖方向因此变为 `appointment → medical`；medical 不依赖任何业务模块（仅 `identity` 的当前用户）。
- 写侧基线校验「请求者 == 接诊医生」由 `MedicalWritePolicy` 直接保证（写死，无法通过移除某个 bean 绕过）；`MedicalWriteGuard` 作为**可插拔的追加**校验接口，注册 bean 即生效，用于后续扩展（如要求预约已到院叫号）而不改动用例。

## Consequences
优点：
- 既保留「接诊医生」语义与预约关联，又让模块图无环，`verify()` 通过。
- 端口由消费方（medical）定义、提供方（appointment）实现，符合依赖倒置；medical 用例不感知 appointment 的实体与仓储。
- 前置校验可插拔，扩展不触碰用例。

代价：
- 引入一个仅为跨模块读而存在的端口类型。Modulith 通常建议优先用事件解耦，但此处需要**同步**鉴权，事件不适用。
- 主人视角的历史查询仍需在 owner 侧完成归属校验（`owner → medical`）；medical 自有的历史查询面向临床角色（`STAFF/DOCTOR/ADMIN`）。

## Alternatives considered
- **medical 直接依赖 appointment / owner**：构成环，`verify()` 失败。否决。
- **事件解耦**：写病历是同步用户动作，需即时鉴权，事件（异步 / 查询登记表）不适用。
- **把 owner 的病历读端点迁入 medical**（使 `owner` 不再依赖 `medical`）：依赖方向最自然，但会改动 M2 既有端点、文档与测试（T-13），本期不做。
- **仅校验 DOCTOR 角色、不关联预约**：最省事，但丢失「接诊」语义与预约关联，不满足验收。否决。
