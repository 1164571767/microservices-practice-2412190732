# 宠医通（PetCare）宠物医院管理系统 — 项目立项说明

> 课程：微服务开发与实践　学号：2412190732　姓名：宋方壹
> 工程：`monolith/`（模块化单体，Java 25 + Spring Boot 4.0.x）

## 1. 要解决的实际问题

城市养宠家庭越来越多，宠物医院通常面临：

- 预约靠电话 / 微信，容易冲突、漏约；
- 宠物病历分散，医生难以连续跟踪；
- 药品库存和处方脱节；
- 收费、诊疗、复诊提醒流程不连贯；
- 多角色协作效率低。

本项目目标是构建一个覆盖「宠物主人预约 — 到院接诊 — 病历处方 — 药品扣减 — 收费 — 复诊提醒」的宠物医院管理系统。

## 2. 目标用户与需求

| 角色 | 主要需求 |
|---|---|
| 宠物主人 | 注册登录、管理宠物档案、预约医生、查看病历、缴费、接收提醒 |
| 医生 / 兽医 | 查看排班、接诊、填写病历、开处方、查看历史病历 |
| 前台 / 护士 | 预约管理、到院签到、候诊叫号、收费操作 |
| 药房 / 库管 | 药品目录、库存管理、处方发药、库存预警 |
| 管理员 | 用户与角色管理、医生排班、收费项目、统计报表 |

## 3. 选择该题目的原因

- **多角色**：主人、医生、前台、药房、管理员，适合做认证与权限。
- **业务模块清晰**：用户、宠物、预约、病历、药品、收费、通知，天然适合服务拆分。
- **有事务场景**：预约冲突、处方扣库存、收费与账单一致性。
- **有消息场景**：预约提醒、候诊叫号、收费成功、复诊提醒。
- **有监控与部署场景**：多个服务需要健康检查、链路追踪、日志聚合。
- **复杂度可控**：支付可以模拟，不依赖真实物流和真实短信，适合课程逐步演进。

## 4. 功能模块划分

| 模块 | 职责 |
|---|---|
| 认证与权限 | 注册、登录、JWT 认证、RBAC（主人 / 医生 / 前台 / 药房 / 管理员） |
| 宠物与主人档案 | 主人信息、宠物信息（品种、年龄、体重、疫苗、过敏史）、宠物病历索引 |
| 预约与排班 | 医生排班、时间段预约、取消 / 改期、冲突检测、到院签到、候诊队列 |
| 门诊病历 | 接诊记录、症状 / 诊断 / 医嘱、处方开具、历史病历查询 |
| 药品与库存 | 药品目录、库存数量、处方扣减库存、低库存预警 |
| 收费与账单 | 收费项目、生成账单、模拟支付、退款 / 作废、收费记录查询 |
| 通知 | 预约成功提醒、候诊叫号、收费成功通知、复诊提醒 |
| 统计报表 | 门诊量、医生工作量、收入统计、药品消耗统计 |

## 5. 一条完整核心业务流程

1. 宠物主人注册并登录。
2. 添加宠物档案。
3. 选择医生和可预约时间段。
4. 系统检查排班与预约冲突，预约成功。
5. 系统发送预约成功提醒。
6. 主人到院，前台签到，进入候诊队列。
7. 医生接诊，填写病历并开具处方。
8. 药房根据处方发药，扣减库存。
9. 前台生成账单，主人模拟支付。
10. 系统记录收费成功，并发送复诊提醒。
11. 主人可查看本次病历、处方和账单。

---

## 6. 第 04 周实现范围：预约与排班（appointment 模块）

本周从上述完整流程中截取**第 3–5 步**，在 `appointment` 模块中作为一条可运行、可测试的业务闭环实现。

### 6.1 本周核心业务流程：创建一次预约

```text
管理员创建医生排班
        │
        ▼
主人查询某医生某日可预约时段
        │
        ▼
主人为自己的宠物创建预约
        │
        ├─ Service 校验：宠物归属、医生角色、时间是否落在排班内、时段是否被占
        │
        ▼
数据库排除约束兜底并发双订 → 预约成功（SCHEDULED）
        │
        ▼
主人查询预约详情 / 宠物预约列表
        │
        ▼
到院签到（CHECKED_IN）→ 候诊队列 → 叫号（CALLED）
```

对应接口（均为 `/api/...` 前缀）：

| 步骤 | 方法 | 路径 |
|---|---|---|
| 创建排班 | POST | `/api/appointment/schedules` |
| 查询排班 | GET | `/api/appointment/schedules?doctorId=` |
| 查询可约时段 | GET | `/api/appointment/availability?doctorId=&date=` |
| **创建预约** | POST | `/api/appointment/appointments` |
| **查询预约详情** | GET | `/api/appointment/appointments/{id}` |
| 查询宠物预约列表 | GET | `/api/appointment/pets/{petId}/appointments` |
| 取消 / 改期 / 签到 | POST | `/api/appointment/appointments/{id}/cancel`、`/reschedule`、`/check-in` |

### 6.2 参与该流程的核心业务实体及关系

| 实体 | 归属模块 | 主要字段 | 说明 |
|---|---|---|---|
| `DoctorSchedule` | appointment | `id`、`doctorId`、`startAt`、`endAt`、`slotMinutes`、`version` | 医生的一段可用时间窗口，按 `slotMinutes` 切分为可预约时段 |
| `Appointment` | appointment | `id`、`petId`、`doctorId`、`startAt`、`endAt`、`status`、`checkedInAt`、`calledAt`、`version` | 一次预约；状态只能经领域方法流转 |

**实体关系**：

```text
DoctorSchedule (1) ──── (N) Appointment
        通过 (doctor_id + 时间区间) 判定归属：
        每条预约的时间区间必须被某条该医生的排班区间覆盖；
        同一医生在同一时间区间最多存在一条未取消的预约。
```

说明：两者之间**不建数据库外键**，因为「归属」由时间区间覆盖关系表达；双订约束由 MySQL 生成列唯一键 + Service 层悲观锁保证（见 §6.3）。`Appointment.petId` / `doctorId` 指向 owner / identity 模块的数据，跨模块不建外键，归属经 Module API 校验。

### 6.3 需要在 Service 中检查的业务规则

**核心规则（本周）**：

> **预约必须落在该医生排班覆盖的时间窗口内，且同一医生在同一时间段最多存在一条未取消的预约。**

落地位置：

| 层次 | 实现 | 位置 |
|---|---|---|
| Service 前置校验 | 查询该医生的排班，要求至少一条 `schedule.covers(startAt, endAt)`；不满足抛 `APPOINTMENT_NOT_AVAILABLE` | `AppointmentService.requireWithinSchedule()` |
| Service 冲突映射 | 保存时捕获 `DataIntegrityViolationException`，映射为 `APPOINTMENT_CONFLICT` | `AppointmentService.book()` |
| 数据库最终守卫 | `UNIQUE (doctor_id, active_start_at)`（生成列，取消时为 `NULL`）；任意区间重叠由 Service 悲观锁兜底 | `V4__appointment.sql`、`AppointmentService` |

配套规则：

- **医生角色校验**：排班的 `doctorId` 必须是 `DOCTOR` 角色，否则 `OPERATION_NOT_ALLOWED`（`DoctorScheduleService.requireDoctor()`）。
- **状态机**：`SCHEDULED → CHECKED_IN → CALLED`；`SCHEDULED / CHECKED_IN → CANCELLED`。非法流转返回 `APPOINTMENT_INVALID_STATE`（`Appointment` 实体方法）。
- **归属校验**：主人只能操作自己宠物的预约，`STAFF / DOCTOR / ADMIN` 可代操作（`AppointmentService.requireAccessiblePet()`）。
- **幂等**：创建预约支持 `Idempotency-Key`，同键同请求回放，同键异请求 `IDEMPOTENCY_CONFLICT`。

## 7. 当前项目范围

**本期实现**：单院区宠物医院、用户与角色权限、宠物档案、预约排班、门诊病历、处方与药品库存、模拟收费、站内通知 / 模拟消息、基础统计报表。

**暂不实现**：真实支付对接、保险理赔、多院区连锁管理、真实短信 / 微信推送、医学影像 AI 诊断、复杂财务审计、第三方物流或药品配送。

## 8. 后续演进方向

- **数据库持久化**：当前以 MySQL 8.0 落地（见 ADR-0008）。
- **缓存**：Redis 做缓存、会话、预约锁。
- **对象存储**：MinIO / RustFS 存病历附件、检查图片。
- **服务拆分**：用户与宠物服务、预约排班服务、病历服务、药品库存服务、收费账单服务、通知服务。
- **认证**：Spring Security + JWT、OAuth2 / RBAC、服务间鉴权。
- **消息**：RocketMQ 承载预约提醒、收费成功、库存扣减、复诊提醒，异步解耦通知服务。
- **事务**：本地事务（收费、账单）；分布式事务（预约 + 通知、处方 + 库存 + 收费）与最终一致性。
- **监控**：Spring Boot Actuator、SkyWalking 链路追踪。
- **部署**：Docker Compose 本地部署、CI/CD 自动化构建与发布。
