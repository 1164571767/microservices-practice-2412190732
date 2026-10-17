# ADR-0005: Authentication and Authorization (JWT access + rotating refresh, RBAC + ownership)

## Status
Accepted

## Context
M1 需要落地认证与权限：注册、登录、受保护接口与 RBAC、资源 ownership/scope 校验，并向其他模块暴露只读的身份 API（见 `docs/plan/index.md` 任务 T-05 ~ T-09）。
约束：
- `docs/tech-stack.md` §6 已定：Spring Security 7 + OAuth2 Resource Server + Nimbus JOSE，Access + Refresh，密码 BCrypt，权限必须在服务端校验。
- `CLAUDE.md`：敏感操作可审计、密码只存哈希、错误不泄露内部信息、不引入无实际价值的基础设施。
- 模块边界（ADR-0003）：identity 内部 `controller/dto/service/repository/entity/config` 不对外，跨模块只能经根包 Module API。

需要决定的关键点：refresh token 的形态与撤销/轮换方式；token 承载什么；RBAC 与 ownership 在哪一层校验。

## Decision
- **access token**：HS256 签名的 JWT，短期（默认 PT15M），只承载身份声明 `sub`(userId)/`username`/`roles`；额外 `typ=access`，decode 端校验 issuer 与 `typ`，防止用 refresh 通道的凭据冒充 access。
- **refresh token**：**不透明高熵随机串**（32 字节，Base64URL），**服务端仅存 SHA-256 哈希**；生成时落库，轮换时用条件 UPDATE（`revoked_at IS NULL`）原子撤销并获得新 token —— 并发消费同一 token 时只有一个成功，其余按重放拒绝。
- **不在 JWT 中放权限清单**：JWT 只声明角色；接口权限用 `@PreAuthorize` + 资源 ownership 在**服务端**二次校验（`UserService.getProfile` 等），越权返回 `AUTH_FORBIDDEN`。
- **角色**：`OWNER / DOCTOR / STAFF / ADMIN`（枚举，DB 有 `CHECK`）。公开注册**固定创建 OWNER**，员工/医生/管理员只能由 ADMIN 经 `POST /api/identity/users` 创建；全新环境的首个管理员由环境变量引导（幂等，凭据不入源码/日志）。
- **密码**：`DelegatingPasswordEncoder`（BCrypt，`{bcrypt}` 前缀），登录用等价的 dummy 哈希比较以降低用户名枚举的时间侧信道。
- **Module API**：identity 根包暴露 `IdentityApi` / `UserProfile` / `CurrentUser` / `Role`，其他模块经此只读查询；identity 的 Repository/Entity 不外泄。
- **无状态**：`SessionCreationPolicy.STATELESS`，CSRF 关闭（不依赖 Cookie）；公开登记/登录/刷新显式放行，其余一律要求认证。

## Consequences
优点：
- 撤销与轮换由服务端状态控制，refresh token 泄露可被撤销；哈希存储使其在库泄露时不可直接复用。
- 权限在服务端判定，JWT 不含敏感数据；模块边界经 Modulith `verify()` + ArchUnit 强制。
- 无需引入 Redis/会话存储等额外基础设施即可满足 M1。

代价：
- refresh token 需要一次数据库读写；轮换对并发敏感，必须以数据库原子操作为准（已用条件 UPDATE）。
- 需要运维提供 `JWT_SECRET`（>= 32 字符），缺失时应用快速失败。

## Alternatives considered
- **服务端 Session**：与无状态目标不符，跨服务透传困难。否决。
- **jjwt 自行签发**：多一个库，且与已选 OAuth2 Resource Server / Nimbus 重复。否决。
- **refresh token 也用 JWT（无服务端状态）**：无法单独撤销、难以检测重放，违背 T-06「可轮换」。否决。
- **把权限写进 JWT**：权限变更需等 token 过期，且越权校验仍需服务端做，收益有限。否决。

## 待办 / 边界
- OpenAPI（springdoc 3.x）与身份接口文档的全量同步在 T-36 处理。
- 审计流水（登录失败、权限拒绝的持久化审计）待后续里程碑按需引入。
