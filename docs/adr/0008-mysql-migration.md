# ADR-0008: 从 PostgreSQL 迁移到 MySQL 8

## Status
Accepted

## Context
ADR-0004 选择 PostgreSQL 17 作为业务库，核心原因是它提供 `EXCLUDE USING gist` 排除约束，可把「同一医生同一时段不得重复预约」变成数据库级最终守卫。

课程第 04 周要求为应用接入 MySQL，且本机已安装 MySQL 8。迁移范围是**整个项目**（驱动、迁移脚本、约束、配置、CI、文档），不是新增一个可选的第二数据库。

需要保留的既有约束：
- ADR-0002：数据库是关键一致性的最终守卫；
- ADR-0003：模块边界与数据所有权不变；
- 测试必须可重复运行，且不能弱化并发与幂等断言。

## Decision
- 业务库切换为 **MySQL 8.0（utf8mb4）**；驱动 `mysql-connector-j`，Flyway 增加 `flyway-mysql`，移除 PostgreSQL 驱动与 `flyway-database-postgresql`。
- V1–V5 migration 重写为 MySQL 方言：
  - `UUID` → `BINARY(16)`（Hibernate 7 默认映射）；
  - `TIMESTAMPTZ` → `DATETIME(6)`，应用与 JDBC 统一 UTC（`hibernate.jdbc.time_zone=UTC`、`connectionTimeZone=UTC`）；
  - `NUMERIC` → `DECIMAL`；`length(trim(x))` → `CHAR_LENGTH(TRIM(x))`。
- **`EXCLUDE` 的替代**（预约双订）：
  1. 数据库：`appointment.active_start_at` 为生成列（未取消时为 `start_at`，取消时为 `NULL`），配合 `UNIQUE (doctor_id, active_start_at)`；InnoDB 唯一索引允许多个 `NULL`，因此取消后时段可重新预约。
  2. Service：`AppointmentService.requireWithinSchedule()` 先用 `SELECT ... FOR UPDATE` 锁定该医生的全部排班，再查询重叠的未取消预约；覆盖任意时间区间，并串行化同一医生的并发预约。
- **排班重叠**：`UNIQUE (doctor_id, start_at)` 兜底 + `DoctorScheduleService` 事务内重叠检查。
- `UniqueViolations` 由 PostgreSQL SQLState `23505` 改为 MySQL error code `1062`。
- 凭据**不硬编码**：本地通过 gitignored 的 `monolith/.env` 注入 `DB_NAME` / `DB_USER` / `DB_PASSWORD`（`DB_HOST` / `DB_PORT` 可选）；`.env.example` 只保留占位符；CI 使用一次性的 `mysql:8.0` service 与独立凭据。
- 集成测试连接**本机真实 MySQL**，沿用「唯一数据 + 可重复执行」策略。
- CI 的 `services` 由 `postgres:17` 改为 `mysql:8.0`。

## Consequences
优点：
- 满足课程要求，全项目单一数据库，无双方言维护成本。
- 并发双订仍有两层守卫：数据库唯一键 + Service 悲观锁；`AppointmentConcurrencyTest` 保持「6 并发仅 1 成功」。
- 取消后释放时段的行为得以保留。
- 凭据只存在于 gitignored 的 `.env`，不进版本库。

代价：
- 失去数据库原生的区间排除约束，任意区间的重叠依赖 Service 层锁；若未来新增绕过 Service 的写入口，需复用同一锁与查询。
- 排班重叠的并发创建没有数据库级区间保证（仅有 `start_at` 唯一键），当前仅管理员低频操作，可接受。
- `DATETIME(6)` 不含时区，必须依赖应用/JDBC 统一 UTC，配置错误会导致时间偏移。

## Alternatives considered
- **保留 PostgreSQL**：不满足本学期要求。否决。
- **双数据库兼容（两套 migration + 运行时 dialect）**：维护成本翻倍，测试矩阵扩大。否决。
- **MySQL 触发器模拟 `EXCLUDE`**：触发器难测试、难调试，错误信息不友好。否决。
- **`doctor_slot` 表 + `UNIQUE(doctor_id, slot_start)`**：约束最强，但需要新增表并改预约模型，影响面更大。否决。
- **只换驱动保留 PG DDL**：`tstzrange` / `EXCLUDE` 无法在 MySQL 执行。不可行。
