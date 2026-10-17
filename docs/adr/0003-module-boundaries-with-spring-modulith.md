# ADR-0003: Enforce Module Boundaries with Spring Modulith

## Status
Accepted

## Context
ADR-0001 确定第一阶段采用 Modular Monolith，并要求：模块拥有独立 controller/service/repository/entity/dto 分层，禁止跨模块直接访问 Repository/Entity 或其他内部子包，跨模块只能走 Module API 或 Domain Event。这些约束若只靠约定，会随迭代腐化，最终退化为「分布式单体」式的紧耦合，未来难以拆分。需要一种**可执行**的边界强制机制。

## Decision
- 采用 **Spring Modulith 2.0.x**（与 Spring Boot 4.0.x 同轨），保持**单一 Maven 模块、单一部署单元**。
- 业务模块 = 主包 `com.zjgsu.sfy` 的直接子包；其内部 `controller/dto/service/repository/entity/common/util/config` 为不对外子包。
- 跨模块只能访问模块**根包**中的 Module API 类型，或订阅领域事件。
- 边界由 Modulith `verify()` + ArchUnit 规则在 `./mvnw verify` 阶段强制，违反即构建失败。

## Consequences
优点：
- 仪式最少，无多模块 pom 负担；自带边界验证测试、模块文档生成。
- 领域事件可用 `@ApplicationModuleListener` + 持久化发布登记表实现至少一次投递。
- 事件支持外部化，未来拆服务时通信模型不变。

代价：
- 团队需遵循 Modulith 的包约定；对外 API 类型必须放在模块根包。
- 部分特性仍标记 `@Incubating`，需随版本演进验证。

## Alternatives considered
- **多模块 Maven 工程**：编译期强制最彻底，但 N 个 pom、跨模块调用繁琐、配置维护重，对课程项目收益不抵成本。
- **纯 ArchUnit 规则**：最轻，但需自维护规则矩阵，且无事件、文档、外部化支持。
- **仅靠 Code Review 约定**：不可执行，必然腐化。已否决。
