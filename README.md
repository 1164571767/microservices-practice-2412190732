# microservices-practice-2412190732
微服务开发与实践 宋方壹 2412190732微服务课程仓库
项目名称
宠医通（PetCare）宠物医院管理系统

## 环境要求与快速开始

工程位于 `monolith/`：模块化单体，Java 25 + Spring Boot 4.0.x。

### 环境要求

| 项 | 要求 | 校验命令 |
|---|---|---|
| JDK | 25（Enforcer 强制 `[25,26)`） | `java -version` |
| Maven | 3.9 及以上 | `mvn -version` |
| MySQL | 8.0+（本机运行，默认 `localhost:3306`） | `mysql --version` |

> 构建命令：`monolith/` 下可直接使用 `./mvnw`（Maven Wrapper）。若网络受限，也可使用系统 `mvn`（已配置镜像与本地仓库）。

### 启动

先确保本机 MySQL 8 已启动，并创建好数据库；再按模板复制出 `monolith/.env`（已被 gitignore），至少提供数据库凭据与认证密钥：

```bash
cd monolith
cp .env.example .env   # 按需修改；务必设置 >= 32 字符的 JWT_SECRET
mvn spring-boot:run
```

> `JWT_SECRET` 为必填（>= 32 字符），缺失时应用快速失败；`JWT_ISSUER` 必须是合法 URL。
> 数据库连接读取 `DB_NAME` / `DB_USER` / `DB_PASSWORD` 与可选 `DB_HOST` / `DB_PORT`（默认 `localhost:3306`）。

先打包再运行：

```bash
cd monolith
mvn -B verify
java -jar target/monolith-0.0.1-SNAPSHOT.jar
```

### 测试

```bash
cd monolith
./mvnw test      # 只跑测试（第 04 周作业要求）
./mvnw verify    # 测试 + 架构/格式/覆盖率/Enforcer 全量校验
```

`verify` 依次执行：单元/集成测试、Spring Modulith 模块结构校验、ArchUnit 架构规则、Spotless 格式检查、JaCoCo 覆盖率报告、Maven Enforcer（强制 JDK 25）。

### 访问地址

应用默认监听 `8080` 端口。

| 接口 | 地址 | 预期响应 |
|---|---|---|
| 运行状态（问候接口） | `GET http://localhost:8080/api/system/status` | `{"application":"monolith","status":"UP","version":"0.0.1-SNAPSHOT"}` |
| 健康检查 | `GET http://localhost:8080/actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` |
| 就绪探针 | `GET http://localhost:8080/actuator/health/readiness` | `{"status":"UP"}` |
| 存活探针 | `GET http://localhost:8080/actuator/health/liveness` | `{"status":"UP"}` |
| 自定义指标 | `GET http://localhost:8080/actuator/metrics/petcare.system.status.requests` | `{"name":"petcare.system.status.requests","measurements":[{"statistic":"COUNT","value":1.0}]}` |

> 运行日志默认带 traceId（形如 `[<traceId>-<spanId>]`）；以 `--spring.profiles.active=prod` 启动时输出 ECS JSON 结构化日志。
> `/actuator/metrics/**` 现要求认证（安全基线收紧），`health`/`info` 仍公开。

### 认证与权限（M1，identity 模块）

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/api/auth/register` | POST | 公开 | 注册，固定创建 OWNER |
| `/api/auth/login` | POST | 公开 | 返回 access + refresh token |
| `/api/auth/refresh` | POST | 公开 | 轮换 refresh token |
| `/api/auth/logout` | POST | 已认证 | 撤销 refresh token |
| `/api/identity/me` | GET | 已认证 | 当前用户档案 |
| `/api/identity/users/{id}` | GET | 本人或 STAFF/DOCTOR/ADMIN | 越权返回 `AUTH_FORBIDDEN` |
| `/api/identity/users` | POST | ADMIN | 创建任意角色账号 |

```bash
# 注册 → 登录 → 访问受保护接口
curl -s -XPOST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice.01","phone":"13800000000","password":"Str0ngPassw0rd!","displayName":"Alice"}'
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice.01","password":"Str0ngPassw0rd!"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
curl -s localhost:8080/api/identity/me -H "Authorization: Bearer $TOKEN"
```

> 全新环境可用环境变量引导首个管理员（幂等）：`BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` / `BOOTSTRAP_ADMIN_PHONE`。详见 `docs/api/identity.md` 与 `docs/adr/0005-authn-authz-jwt-rbac.md`。

### 预约与排班（第 04 周，appointment 模块）

本周选择 `appointment` 模块作为「业务建模 / 单体应用实现 / 数据库持久化 / 测试与验证」的示例。核心表与约束见 [`docs/database.md`](docs/database.md)，接口细节见 [`docs/api/appointment.md`](docs/api/appointment.md)。

启动时需引导一个管理员（用于创建医生与排班），例如：

```bash
cd monolith
JWT_SECRET='local-dev-secret-at-least-32-characters!!' \
BOOTSTRAP_ADMIN_USERNAME=admin \
BOOTSTRAP_ADMIN_PASSWORD='AdminPassw0rd!' \
BOOTSTRAP_ADMIN_PHONE=13800000000 \
mvn spring-boot:run
```

以下示例可直接按顺序复制执行（默认应用监听 `8080`）。示例使用固定用户名 / 手机号，重复执行前请先改掉（或用新的用户名与手机号）：

```bash
BASE=http://localhost:8080

# 1) 注册主人（公开）
curl -s -XPOST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"owner.04","phone":"13800000404","password":"Str0ngPassw0rd!","displayName":"Owner 04"}'

# 2) 主人登录，取 access token
OWNER_TOKEN=$(curl -s -XPOST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"owner.04","password":"Str0ngPassw0rd!"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 3) 创建主人档案
curl -s -XPUT $BASE/api/owner/me -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"宋方壹","phone":"13800000404","address":"杭州"}'

# 4) 创建宠物，取 petId
PET_ID=$(curl -s -XPOST $BASE/api/owner/pets -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"豆豆","species":"金毛","birthDate":"2022-05-01","weightKg":28.5}' \
  | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# 5) 管理员登录，取 access token
ADMIN_TOKEN=$(curl -s -XPOST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"AdminPassw0rd!"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 6) 管理员创建医生，取 doctorId
DOCTOR_ID=$(curl -s -XPOST $BASE/api/identity/users -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"username":"doctor.04","phone":"13800000405","password":"Str0ngPassw0rd!","displayName":"李医生","roles":["DOCTOR"]}' \
  | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# 7) 管理员创建排班（2026-11-10 08:00–12:00 Asia/Shanghai，每 30 分钟一个时段）
curl -s -XPOST $BASE/api/appointment/schedules -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"doctorId\":\"$DOCTOR_ID\",\"startAt\":\"2026-11-10T00:00:00Z\",\"endAt\":\"2026-11-10T04:00:00Z\",\"slotMinutes\":30}"

# 8) 查询可预约时段（查询接口）
curl -s "$BASE/api/appointment/availability?doctorId=$DOCTOR_ID&date=2026-11-10" \
  -H "Authorization: Bearer $OWNER_TOKEN"

# 9) 创建预约（创建接口；Idempotency-Key 可省略）
curl -s -XPOST $BASE/api/appointment/appointments -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-key-04' \
  -d "{\"petId\":\"$PET_ID\",\"doctorId\":\"$DOCTOR_ID\",\"startAt\":\"2026-11-10T01:00:00Z\",\"endAt\":\"2026-11-10T01:30:00Z\"}"

# 10) 查询宠物预约列表（查询接口）
curl -s "$BASE/api/appointment/pets/$PET_ID/appointments" -H "Authorization: Bearer $OWNER_TOKEN"
```

**业务规则验证**：第 9 步占用 `01:00–01:30Z` 后，再用同样时段创建预约会返回
`{"code":"APPOINTMENT_CONFLICT", ...}`；若时间不落在排班内，返回 `{"code":"APPOINTMENT_NOT_AVAILABLE", ...}`。

### 数据库配置

当前实现使用本机 **MySQL 8.0**（见 `docs/adr/0008`）：

```bash
# 1) 安装并启动 MySQL 8 后，创建数据库（utf8mb4）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS petcare CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 2) 在 monolith/.env 中配置（该文件已被 gitignore，请勿提交）
DB_HOST=localhost
DB_PORT=3306
DB_NAME=petcare
DB_USER=<你的 MySQL 用户>
DB_PASSWORD=<你的 MySQL 密码>
```

Flyway 启动时自动建表（`V1`~`V5`）。MySQL 不支持 PostgreSQL 的 `EXCLUDE` 约束，双订由「生成列唯一键 + Service 层悲观锁 + 重叠查询」共同保证，详见 [`docs/database.md`](docs/database.md)。

要解决的实际问题
城市养宠家庭越来越多，宠物医院通常面临：

预约靠电话/微信，容易冲突、漏约；

宠物病历分散，医生难以连续跟踪；

药品库存和处方脱节；

收费、诊疗、复诊提醒流程不连贯；

多角色协作效率低。

本项目目标是构建一个覆盖“宠物主人预约—到院接诊—病历处方—药品扣减—收费—复诊提醒”的宠物医院管理系统。

主要目标用户及需求
角色	主要需求
宠物主人	注册登录、管理宠物档案、预约医生、查看病历、缴费、接收提醒
医生/兽医	查看排班、接诊、填写病历、开处方、查看历史病历
前台/护士	预约管理、到院签到、候诊叫号、收费操作
药房/库管	药品目录、库存管理、处方发药、库存预警
管理员	用户与角色管理、医生排班、收费项目、统计报表
2. 选择该题目的原因
多角色：主人、医生、前台、药房、管理员，适合做认证与权限。

业务模块清晰：用户、宠物、预约、病历、药品、收费、通知，天然适合服务拆分。

有事务场景：预约冲突、处方扣库存、收费与账单一致性。

有消息场景：预约提醒、候诊叫号、收费成功、复诊提醒。

有监控与部署场景：多个服务需要健康检查、链路追踪、日志聚合。

复杂度可控：支付可以模拟，不依赖真实物流和真实短信，适合课程逐步演进。

3. 功能模块划分
认证与权限模块

注册、登录、JWT 认证

角色：主人、医生、前台、药房、管理员

RBAC 权限控制

宠物与主人档案模块

主人信息

宠物信息：品种、年龄、体重、疫苗、过敏史

宠物病历索引

预约与排班模块

医生排班

时间段预约

预约、取消、改期

冲突检测

到院签到、候诊队列

门诊病历模块

接诊记录

症状、诊断、医嘱

处方开具

历史病历查询

药品与库存模块

药品目录

库存数量

处方扣减库存

低库存预警

收费与账单模块

收费项目

生成账单

模拟支付

退款/作废

收费记录查询

通知模块

预约成功提醒

候诊叫号

收费成功通知

复诊提醒

统计报表模块

门诊量

医生工作量

收入统计

药品消耗统计

4. 一条完整核心业务流程
宠物主人注册并登录。

添加宠物档案。

选择医生和可预约时间段。

系统检查排班与预约冲突，预约成功。

系统发送预约成功提醒。

主人到院，前台签到，进入候诊队列。

医生接诊，填写病历并开具处方。

药房根据处方发药，扣减库存。

前台生成账单，主人模拟支付。

系统记录收费成功，并发送复诊提醒。

主人可查看本次病历、处方和账单。

5. 当前项目范围
本期实现
单院区宠物医院

用户与角色权限

宠物档案

预约排班

门诊病历

处方与药品库存

模拟收费

站内通知/模拟消息

基础统计报表

暂不实现
真实支付对接

保险理赔

多院区连锁管理

真实短信/微信推送

医学影像 AI 诊断

复杂财务审计

第三方物流或药品配送

6. 后续演进方向
数据库持久化
MySQL/PostgreSQL 存核心业务数据

Redis 做缓存、会话、预约锁

MinIO或RustFS 存病历附件、检查图片

服务拆分
可拆为：

用户与宠物服务

预约排班服务

病历服务

药品库存服务

收费账单服务

通知服务

认证
Spring Security + JWT

OAuth2 / RBAC

服务间鉴权

消息
RocketMQ

预约提醒、收费成功、库存扣减、复诊提醒

异步解耦通知服务

事务
本地事务：收费、账单

分布式事务：预约 + 通知、处方 + 库存 + 收费

最终一致性

监控
Spring Boot Actuator

SkyWalking 链路追踪

部署
Docker Compose 本地部署

CI/CD 自动化构建与发布

7. 初步边界与适合演进的原因
初步边界：只围绕宠物医院核心诊疗闭环，不把支付、保险、物流、社交等外围能力纳入首期。

适合演进的原因：

模块天然按业务能力划分，不是按数据库表拆分；

每个模块可独立部署；

有明确的多角色认证需求；

有消息、事务、监控、部署的真实场景；

业务规模适中，适合一学期持续迭代。
