# identity API（M1 认证与权限）

- 统一错误体：`{"code": "<稳定机器码>", "message": "<可读信息>"}`；客户端依赖 `code`，见 `error-codes.md`。
- 认证方式：`Authorization: Bearer <accessToken>`（HS256 JWT）。
- 除下表标注为「公开」外，所有接口均需认证；角色不符或越权返回 `AUTH_FORBIDDEN`。

## 公开接口

| 方法 | 路径 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|
| POST | `/api/auth/register` | 注册；**固定创建 OWNER** | 201 `UserResponse` | `VALIDATION_FAILED`；`USER_ALREADY_EXISTS` |
| POST | `/api/auth/login` | 用户名/密码登录 | 200 `TokenResponse` | `AUTH_INVALID_CREDENTIALS`；`AUTH_FORBIDDEN`（账号停用） |
| POST | `/api/auth/refresh` | 用 refresh token 轮换 | 200 `TokenResponse` | `AUTH_INVALID_CREDENTIALS`（无效/过期/已被消费） |

请求/响应示例：

```jsonc
// POST /api/auth/register
{ "username": "alice.01", "phone": "13800000000", "password": "Str0ngPassw0rd!", "displayName": "Alice" }
// 201
{ "id": "<uuid>", "username": "alice.01", "phone": "13800000000", "displayName": "Alice", "roles": ["OWNER"], "status": "ACTIVE" }

// POST /api/auth/login
{ "username": "alice.01", "password": "Str0ngPassw0rd!" }
// 200
{ "accessToken": "<jwt>", "refreshToken": "<opaque>", "tokenType": "Bearer", "expiresIn": 900 }

// POST /api/auth/refresh
{ "refreshToken": "<opaque>" }
```

## 受保护接口

| 方法 | 路径 | 权限 | 说明 | 主要错误 |
|---|---|---|---|---|
| GET | `/api/identity/me` | 任意已认证 | 当前用户档案 | `AUTH_UNAUTHENTICATED` |
| GET | `/api/identity/users/{id}` | 本人或 STAFF/DOCTOR/ADMIN | 查询用户档案 | `AUTH_FORBIDDEN`（他人且非员工）；`RESOURCE_NOT_FOUND` |
| POST | `/api/identity/users` | ADMIN | 创建任意角色账号 | `AUTH_FORBIDDEN`；`VALIDATION_FAILED`；`USER_ALREADY_EXISTS` |
| POST | `/api/auth/logout` | 任意已认证 | 撤销给定 refresh token | `AUTH_UNAUTHENTICATED` |

```jsonc
// POST /api/identity/users (ADMIN)
{ "username": "dr.wang", "phone": "13900000000", "password": "Str0ngPassw0rd!", "displayName": "王医生", "roles": ["DOCTOR"] }
```

## 校验规则（Bean Validation）

| 字段 | 规则 |
|---|---|
| username | 3–50，`^[A-Za-z0-9_.-]+$`，大小写不敏感唯一 |
| phone | `^\+?[0-9]{6,20}$`，唯一 |
| password | 8–72 |
| displayName | 1–100 |
| roles | 创建账号时至少一个 |

## 数据约束（DB 最终守卫）

- `users.username` / `users.phone` `UNIQUE`；`status` `CHECK IN ('ACTIVE','DISABLED')`。
- `user_role` 主键 `(user_id, role)`，`role CHECK IN ('OWNER','DOCTOR','STAFF','ADMIN')`，`FK → users`。
- `refresh_token.token_hash` `UNIQUE`；`FK → users`。

> 说明：`/v3/api-docs`（springdoc）尚未接入，OpenAPI 全量同步见 T-36。
