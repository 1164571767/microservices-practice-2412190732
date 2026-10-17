# ADR-0002: Database Is the Final Correctness Guard

## Status
Accepted

## Context
预约、库存、支付存在并发与重复请求风险。

## Decision
应用层负责业务校验与并发控制策略；数据库约束负责最终正确性。Redis/锁只能作为性能或并发优化，不作为唯一一致性保障。

## Consequences
需要认真设计 UNIQUE / FK / CHECK / transaction / version 等约束，并编写真实集成测试。
