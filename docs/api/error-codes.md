# Error Codes

稳定的 machine-readable code；客户端依赖 code，不依赖 message。

| Code | Meaning |
|---|---|
| AUTH_UNAUTHENTICATED | 未携带有效凭据（无 token / token 无效或过期） |
| AUTH_INVALID_CREDENTIALS | 登录凭据无效 |
| AUTH_FORBIDDEN | 无权执行该操作（角色不符或越权访问他人资源） |
| USER_ALREADY_EXISTS | 注册时用户名或手机号已存在 |
| RESOURCE_NOT_FOUND | 资源不存在 |
| VALIDATION_FAILED | 请求参数校验失败 |
| APPOINTMENT_CONFLICT | 预约时间冲突 |
| APPOINTMENT_NOT_AVAILABLE | 当前时间段不可预约 |
| APPOINTMENT_INVALID_STATE | 预约状态不允许该操作 |
| INSUFFICIENT_STOCK | 库存不足 |
| INVENTORY_INVALID_STATE | 库存状态不允许该操作 |
| PAYMENT_ALREADY_PAID | 已支付，重复支付 |
| PAYMENT_INVALID_STATE | 支付状态不允许该操作 |
| IDEMPOTENCY_CONFLICT | 相同幂等键对应不同请求 |
| OPERATION_NOT_ALLOWED | 当前角色/业务状态不允许操作 |
| INTERNAL_ERROR | 未分类的内部错误 |

新增业务错误码必须：
1. 稳定、唯一、可检索；
2. 不包含实现细节；
3. 同步更新 API 文档与相关测试。
