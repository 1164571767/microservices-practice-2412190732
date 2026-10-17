# ADR-0001: Modular Monolith First

## Status
Accepted

## Context
PetCare 首期是单院区宠物医院系统，模块天然按用户/宠物、预约、病历、库存、收费、通知等业务能力划分；后续计划逐步拆分微服务。

## Decision
第一阶段采用 Modular Monolith：
- 单进程、单数据库；
- 模块拥有独立 controller/service/repository/entity/dto 分层；
- 禁止跨模块直接访问 Repository/Entity；
- 跨模块通过 Module API 或 Domain Event；
- 以真实业务边界作为未来服务拆分候选。

## Consequences
优点：降低课程项目运维复杂度，同时保留可拆分边界。
代价：需要主动约束模块依赖，并避免共享模型与数据库耦合。
