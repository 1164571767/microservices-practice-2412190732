# ADR-0004: PostgreSQL as the System of Record

## Status
Superseded by [ADR-0008](0008-mysql-migration.md) (2026-10-17)

> 本文仅保留历史决策记录。项目已按要求迁移到 MySQL 8，当前实现与替代约束方案见 ADR-0008。

## Context
ADR-0002 规定「数据库是关键一致性的最终约束来源」，并要求为预约、库存、支付认真设计 UNIQUE / FK / CHECK / transaction / version 等约束，并编写真实集成测试。核心场景之一是**同一医生同一时段不得重复预约**——这本质上是一个时间区间互斥问题。

## Decision
- 业务库采用 **PostgreSQL 17**。
- 预约冲突用 **`EXCLUDE USING gist (doctor_id WITH =, time_range WITH &&)`** 在数据库层直接禁止重叠，而非只靠应用层「先查后写」。
- 库存、金额使用 `CHECK (>= 0)`；关键字段 `NOT NULL / UNIQUE / FK`；写操作可追溯。
- Schema 变更只走 **Flyway** migration；Hibernate `ddl-auto=validate`。
- 集成测试使用**真实 PostgreSQL**（见 Amendment），**不使用 H2**。

## Consequences
优点：
- 预约、库存、支付获得数据库级最终保证，应用层校验仅作前置体验。
- `EXCLUDE` / `CHECK` / `RETURNING` / 强 `timestamptz` 语义，直接支撑 ADR-0002。

代价：
- 本地必须运行真实 PostgreSQL（本机安装 PostgreSQL 17，`localhost:5432`）。
- 放弃 H2 的「零依赖」便利，换取测试真实性。

## Alternatives considered
- **MySQL 8**：国内生态熟悉，但缺少 `EXCLUDE` 约束，预约冲突只能近似，作为「最终守卫」需更多隐晦约束，`CHECK` 支持亦较弱。
- **H2（PostgreSQL 模式）**：无需 Docker，但方言差异会掩盖真实约束/并发问题，直接侵蚀 ADR-0002 的保证。仅可用于纯逻辑单测，不可用于关键集成测试。
- **Zoned embedded-postgres**：无需 Docker，本地友好；但与真实 PostgreSQL 仍可能有方言差异，暂不采用。

## Amendment (2026-10-05)
开发主机为 Windows，未安装 Docker，Testcontainers 无法在本机运行。集成测试与本地开发改用**本机安装的 PostgreSQL 17**（`localhost:5432`）：
- 本地：本机原生安装的 PostgreSQL 17（非 Docker）。
- 远程 VMware 虚拟机内的 Docker 不再承载 PostgreSQL，仅保留给后续 Redis / RustFS 等扩展。
- CI：需以 `services:` 提供 `postgres:17` 并注入 `POSTGRES_*` 环境变量（尚未接入）。
仍坚持「用真实 PostgreSQL、不用 H2」的原则；仅测试库的供给方式由 Testcontainers 改为本机 PostgreSQL。
