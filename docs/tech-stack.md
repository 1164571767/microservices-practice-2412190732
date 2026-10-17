# 技术选型（第一阶段：模块化单体）

> 本文件是第一阶段（模块化单体、单库、单院区）技术栈的**唯一事实源**。
> 原则依据：`CLAUDE.md`、`.claude/rules/`、`docs/adr/ADR-0001`、`docs/adr/ADR-0002`。
> 「后续演进方向」中的 Redis / RocketMQ / SkyWalking / 对象存储 / 服务拆分**均为非本期承诺**，不引入依赖、不搭基础设施。

## 1. 约束与原则

- **固定前提**：Java 25（LTS）、Spring Boot 4.0.x。
- 业务模块边界优先，禁止跨模块直接访问其他模块的 Repository / Entity 或内部子包（见 ADR-0001、ADR-0007）。
- 数据库是关键一致性的**最终守卫**，应用层校验只做前置（见 ADR-0002）。
- Schema 变更只走 migration，禁止 `create / create-drop`。
- 不为展示技术而引入无实际价值的基础设施。

## 2. 版本基线

| 组件 | 版本 | 说明 |
|---|---|---|
| Java | 25 (LTS) | CI 已固定 `java-version: '25'` |
| Spring Boot | 4.0.x（当前最新 4.0.8） | 基于 Spring Framework 7、Jakarta EE 11 |
| Spring Framework | 7.0.x | 随 Boot BOM |
| Spring Security | 7.0.x | 随 Boot BOM |
| Spring Modulith | 2.0.x | 与 Boot 4.0.x 同轨发布 |
| Hibernate ORM | 7.x | 随 Boot BOM；支持 UUIDv7 |
| Jackson | 3.x（`tools.jackson.*`） | Boot 4 默认；不再用 `com.fasterxml.jackson.*` |
| JUnit | Jupiter 6（Platform 6） | Boot 4 测试栈；无 `junit-vintage-engine` |
| MySQL | 8.0 | 业务库（utf8mb4） |
| Flyway | 随 Boot BOM（`spring-boot-starter-flyway`） | 迁移工具 |
| springdoc-openapi | 3.x（3.0.x 对应 Boot 4.0.x） | OpenAPI 生成，非 Boot BOM 管理，需显式锁版本 |

**Boot 4 依赖命名注意**：`spring-boot-starter-web` → **`spring-boot-starter-webmvc`**；`spring-boot-starter-json` → `spring-boot-starter-jackson`；测试切片已拆分（如 `spring-boot-starter-webmvc-test`、`spring-boot-starter-security-test`），`@MockBean`/`@SpyBean` 改为 `@MockitoBean`/`@MockitoSpyBean`。

## 3. 决策总览

| 领域 | 决策 | 理由（要点） | 被否方案 |
|---|---|---|---|
| 构建工具 | **Maven + Wrapper** | 本机已装 3.9.11、CI 已配 `cache: maven`、生态成熟 | Gradle |
| 模块边界强制 | **Spring Modulith 2.0.x + ArchUnit**（ADR-0003） | 自带验证测试 / 事件外部化，仪式最少 | 多模块 Maven、纯 ArchUnit |
| 数据库 | **MySQL 8.0**（ADR-0008） | 课程要求；PostgreSQL 的 `EXCLUDE` 改用生成列唯一键 + 悲观锁 | PostgreSQL、H2 |
| 持久化 | **Spring Data JPA / Hibernate 7** | 聚合 + 状态转换贴合 domain 规则，`@Version` 乐观锁 | jOOQ、MyBatis、Spring Data JDBC |
| 迁移 | **Flyway** | 纯 SQL、可审、原生支持高级约束 | Liquibase |
| 集成测试库 | **本机 MySQL 8（真实库）** | 与生产语义一致，无需 Docker | H2、Testcontainers |
| 认证 | **OAuth2 Resource Server + Nimbus JOSE，Access+Refresh** | 零额外依赖、服务端校验权限、未来服务间透传 | jjwt、服务端 Session |
| 密码 | **BCrypt**（`DelegatingPasswordEncoder`） | 安全哈希、生态默认 | 明文 / 自制哈希 |
| API 契约 | **REST + springdoc 代码优先** | 与「新增 API 同步更新 OpenAPI」最自然 | 契约优先、手写 OpenAPI |
| 主键 | **UUIDv7**（MySQL `BINARY(16)`，`@UuidGenerator(Style.VERSION_7)`） | 时间有序、不可枚举、拆分无冲突 | `BIGSERIAL`、UUIDv4 |
| 跨模块事件 | **Spring Modulith 事件 + 持久化发布登记表** | 至少一次投递 + 重试，近事务性发件箱 | 原生 `ApplicationEvent`、本期上 RocketMQ |
| 可观测性 | **Actuator + Micrometer + Micrometer Tracing(Brave)** | 零 agent、traceId 贯穿日志；Brave bridge 会写 MDC | 本期 SkyWalking Agent、仅 Actuator |
| 虚拟线程 | **本期不启用** | 行为最可预测；待压测需要再开 | 启用（记为可切换项） |
| 代码约定 | **record + 手写映射，不用 Lombok/MapStruct** | 少注解处理器、编译可预测 | Lombok / MapStruct |
| 质量闸门 | **ArchUnit + Spotless + JaCoCo + Maven Enforcer** | 覆盖边界/格式/覆盖率/JDK 四类关键风险 | 严格档（Checkstyle/SpotBugs…）、最小档 |
| 本地环境 | **本机 MySQL 8**，应用本地跑 | 最小、可重现、凭据不入库 | 全套中间件、VM 内 Docker |

## 4. 架构与模块

### 4.1 包结构约定

模块 = 主包（`com.zjgsu.sfy`）的**直接子包**；其内部采用传统分层 `controller / dto / service / repository / entity / common / util / config`（见 `.claude/rules/package-structure.md`），均为**不对外子包**。跨模块只能访问模块**根包**中的 Module API 类型，或订阅领域事件；违反由 Modulith `verify()` + ArchUnit 在 `verify` 阶段阻断。

```
com.zjgsu.sfy
├── MonolithApplication.java       // 主包根，Modulith 以此定位模块
├── common/                        // 共享内核：开放模块（@ApplicationModule(OPEN)），仅技术能力，禁止业务模型
│   ├── error/                     //   统一错误契约
│   ├── api/                       //   HTTP 异常映射
│   └── idempotency/               //   幂等能力
├── identity/
│   ├── <Module API 类型（对外可见，放根包）IdentityApi / CurrentUser / UserProfile>
│   ├── controller/                //   Controller
│   ├── dto/{req,resp}/            //   请求 / 响应模型
│   ├── service/                   //   用例 / 应用服务（细分 command / result）
│   ├── repository/                //   Spring Data Repository
│   ├── entity/                    //   持久化实体与不变式
│   ├── common/                    //   模块私有常量 / 枚举（Role、UserStatus）
│   └── config/                    //   @Configuration / @ConfigurationProperties / 安全装配
├── owner/ …（同上结构，按需创建子包；仅在实际存在职责时创建）
```

工程根目录：`monolith/`；groupId 与 package 均为 `com.zjgsu.sfy`。

### 4.2 模块清单与数据所有权

| 模块 | 包名 | 拥有的数据 | 主要对外方式 |
|---|---|---|---|
| 认证与权限 | `identity` | 用户、角色、权限、凭据 | Module API（当前用户/角色查询）、JWT |
| 主人与宠物档案 | `owner` | 主人、宠物、疫苗、过敏史 | Module API |
| 预约与排班 | `appointment` | 医生排班、预约、候诊队列 | Module API、事件 |
| 门诊病历 | `medical` | 接诊记录、病历、处方 | Module API、事件 |
| 药品与库存 | `pharmacy` | 药品目录、库存、出入库流水 | Module API、事件 |
| 收费与账单 | `billing` | 收费项目、账单、支付记录 | Module API、事件 |
| 通知 | `notification` | 站内通知、提醒任务 | **仅事件订阅**（消费型） |
| 统计报表 | `reporting` | 只读汇总（不拥有业务主数据） | 读模型 / 事件投影 |
| 共享内核 | `common` | 无业务数据 | 技术工具、错误码、基类型 |

规则：一个模块的 Repository 只能操作本模块拥有的数据；`reporting` 通过事件投影或只读查询构建，不反向写业务模块。

## 5. 数据、约束与一致性

- **迁移**：Flyway `src/main/resources/db/migration/V_n__*.sql`；`spring.jpa.hibernate.ddl-auto=validate`（正式环境绝不用 `create/update`）。Flyway 10+ 连接 MySQL 需额外引入 `flyway-mysql`。
- **约束优先落到数据库**：
  - 预约冲突：`UNIQUE(doctor_id, active_start_at)`（生成列，取消时为 NULL）+ Service 层 `SELECT ... FOR UPDATE` 与重叠查询；MySQL 无 `EXCLUDE`。
  - 库存/金额：`CHECK (quantity >= 0)`、`CHECK (amount >= 0)`；关键字段 `NOT NULL / UNIQUE / FK`。
- **并发**：聚合根用 `@Version` 乐观锁；热点计数（库存）如需强一致用行级锁或原子 UPDATE，不依赖 Redis。
- **幂等**：写接口（支付、发药、签到）以客户端幂等键 + 唯一约束去重，冲突映射为 `IDEMPOTENCY_CONFLICT`。
- **主键**：UUIDv7（`@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`，Hibernate 标记 `@Incubating`，需实现时验证）。
- **分页**：列表接口默认分页；注意 N+1 与事务边界（`spring.jpa.open-in-view=false`）。

## 6. 安全

- Spring Security 7 + OAuth2 Resource Server + Nimbus JOSE 签发/校验 JWT。
- 短命 Access Token + 轮换 Refresh Token；JWT 只承载身份声明，**权限在服务端校验**。
- RBAC：`authorities` 做角色判断，业务层再校验资源 ownership / scope（如「主人只能看自己的宠物」）。
- 密码 BCrypt；secret / 私钥走环境变量，**不进 Git、不进日志**（仓库已有 `.env`/`*.pem`/`*.key` 读取拦截）。
- 对外输入默认不可信；错误响应遵循 `docs/api/error-codes.md`，不泄露 stack trace / SQL / 路径。

## 7. API 契约

- REST + DTO（Java `record`）+ Bean Validation；禁止直接暴露 Persistence Entity。
- Controller 只做 HTTP 层职责，业务规则进 service / entity。
- 错误模型映射 `docs/api/error-codes.md` 的稳定 code。
- springdoc-openapi 3.x 生成 `/v3/api-docs` 与 Swagger UI；新增 API 同步更新 OpenAPI。

## 8. 事件与异步

- 跨模块通信优先 **Domain Event**，用 Spring Modulith `@ApplicationModuleListener` 异步、事务后发布。
- 开启 **Event Publication Registry（JDBC 持久化）**：未完成投递可重试、可审计，达成至少一次投递（近事务性发件箱）。
- 消费者必须**幂等**（以 eventId / 业务幂等键去重）。
- 本期**不引入外部消息中间件**；RocketMQ 留待服务拆分阶段，事件模型保持可外部化。

## 9. 可观测性

- Actuator：区分 **liveness / readiness** 探针组。
- Micrometer 采集关键业务 metrics（预约数、扣库存、支付成功等）。
- Micrometer Tracing（**Brave bridge**）提供 **traceId 贯穿**：Brave 的 MDC 装饰器把 `traceId`/`spanId` 写入 SLF4J MDC，Boot 的日志关联模式（默认 `traceId(32),spanId(16)`）自动渲染，实测日志形如 `[<traceId>-<spanId>]`。
  - 必须同时引入 `spring-boot-micrometer-tracing-brave`（Boot 4 的 bridge 自动配置模块）与 `micrometer-tracing-bridge-brave`；只加后者不会启用 tracing。
  - **不用 OTel bridge**：`micrometer-tracing` 与 OTel bridge 均不写 SLF4J MDC（jar 字节码已确认），导致 traceId 无法关联；未来需要 OTLP 导出时再单独评估。
- 日志：本地用可读文本；非本地 profile 输出**结构化字段**（JSON），敏感数据脱敏。

## 10. 工程与质量闸门

- Maven Wrapper 必须提交（CI 依赖 `./mvnw -B verify`）。
- `verify` 阶段内联以下闸门：
  - **Spring Modulith `verify()` + ArchUnit**：强制模块边界与依赖方向。
  - **Spotless**（google-java-format 1.28.0）：统一格式，消除 Windows/VSCode 换行与格式漂移（对应历史提交痛点）。
  - **JaCoCo 0.8.13**：覆盖率报告（domain / 关键应用服务后续设阈值）。
  - **Maven Enforcer 3.5.0**：要求 Java 25（`[25,26)`）与 Maven ≥ 3.9。
- 已锁版本：spring-modulith 2.0.8、archunit 1.4.1、spotless 2.44.3、jacoco 0.8.13、enforcer 3.5.0、google-java-format 1.28.0。
- 测试栈：JUnit Jupiter 6、AssertJ、Mockito、`spring-boot-starter-webmvc-test`、`spring-boot-starter-security-test`。
- **本地构建**：`monolith/` 下可直接使用 `./mvnw`；网络受限时可用系统 `mvn`（已配置镜像与本地仓库）。

## 11. 本地开发环境

- 本机安装 **MySQL 8.0**（默认 `localhost:3306`），应用在主机/IDE 本地运行。
- 凭据经 `.env`（gitignore）注入：`application.yml` 用 `spring.config.import: optional:file:./.env[.properties]` 读取，需提供 `DB_NAME` / `DB_USER` / `DB_PASSWORD`；`DB_HOST` / `DB_PORT` 可选（默认 `localhost:3306`）。
- 数据库集成测试连**本机真实 MySQL**（不使用 Testcontainers，见 ADR-0008）。
- 远程 VMware 虚拟机内的 Docker 保留给后续 Redis / RustFS 等扩展，不再承载 MySQL。
- 配置：当前为单个 `application.yml`（`prod` profile 只覆盖日志格式）；需要时再拆分，不使用配置中心。

## 12. 领域技术约定

- 金额：`DECIMAL(19,2)` + 币种字段（`CNY`），Java 侧 `BigDecimal`，运算显式指定 `RoundingMode`。
- 时间：统一 `Instant` / `OffsetDateTime` 存 **UTC**，展示按 `Asia/Shanghai`；`timestamptz` 存区间。
- 标识：对外一律 UUIDv7，不暴露自增。
- 业务状态：只能经领域方法 / 状态转换变更，禁止裸 `setStatus(...)`。

## 13. 不在本期（非承诺演进项）

以下均**不引入依赖、不改代码**，仅作为方向记录：Redis（缓存/预约锁）、RocketMQ/Kafka（跨服务消息）、SkyWalking（链路追踪后端）、MinIO/RustFS（对象存储，病历附件）、服务拆分（6 个候选服务）、OAuth2 统一认证中心、启用虚拟线程。

## 14. 实现时需验证的开放点

1. **springdoc-openapi 具体版本**：3.0.x 线对应 Boot 4.0.x，需按官方兼容表锁定（非 Boot BOM 管理）。
2. **Hibernate `UuidGenerator.Style.VERSION_7`**：当前 `@Incubating`，落库前验证生成与 PG `uuid` 映射。
3. **Modulith 事件登记表**：确认 JDBC schema 初始化方式与 Flyway 迁移的先后顺序。
4. **本地 Maven 镜像**：本地依赖走 aliyun（用 `mvn`），CI 走 Central（用 `./mvnw`）；若希望本地也用 `./mvnw`，需把 wrapper 的 distributionUrl 指向可达镜像。
5. **Boot 4 版本号**：Initializr 元数据里版本 id 带 `.RELEASE` 后缀（如 `4.0.8.RELEASE`），实际 Maven 坐标是 `4.0.8`，写 pom 时不要带后缀。

## 15. 参考

- Spring Boot 4.0.0 与 Spring Framework 7 发布说明（spring.io）
- Spring Boot 4.0 Migration Guide（starter 重命名、`@MockitoBean`、Jackson 3）
- Spring Modulith 2.0.x 发布说明（与 Boot 4.0.x 同轨）
- Hibernate 7 `@UuidGenerator` / `UuidGenerator.Style.VERSION_7`
- springdoc-openapi v3（Spring Boot 4 支持）
