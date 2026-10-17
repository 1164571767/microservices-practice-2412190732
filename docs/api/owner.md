# owner API（M2 主人与宠物档案）

- 统一错误体：`{"code": "<稳定机器码>", "message": "<可读信息>"}`；客户端依赖 `code`，见 `error-codes.md`。
- 认证方式：`Authorization: Bearer <accessToken>`；本模块接口均需认证，能力不足或越权返回 `AUTH_FORBIDDEN`。
- 归属校验在服务端进行：主人只能读写自己的档案与宠物；`STAFF / DOCTOR / ADMIN` 可只读他人档案与宠物。
- 列表接口分页：`?page=0&size=20`（`size` 上限 100），排序固定为 `createdAt DESC`，不接受客户端排序字段。

## 主人档案

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| PUT | `/api/owner/me` | 任意已认证 | 创建或更新本人档案（幂等 upsert） | 200 `OwnerResponse` | `VALIDATION_FAILED` |
| GET | `/api/owner/me` | 任意已认证 | 本人档案 | 200 `OwnerResponse` | `RESOURCE_NOT_FOUND`（尚未创建） |
| GET | `/api/owner/owners/{ownerId}` | 本人或 `STAFF/DOCTOR/ADMIN` | 按 ownerId 查询档案 | 200 `OwnerResponse` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |

```jsonc
// PUT /api/owner/me
{ "name": "张三", "phone": "13800000000", "address": "杭州市西湖区" }
// 200
{ "id": "<uuid>", "userId": "<uuid>", "name": "张三", "phone": "13800000000", "address": "杭州市西湖区",
  "createdAt": "<instant>", "updatedAt": "<instant>" }
```

## 宠物档案

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| GET | `/api/owner/pets` | 任意已认证 | 分页列出本人宠物 | 200 `PageResponse<PetResponse>` | `RESOURCE_NOT_FOUND`（无主人档案） |
| POST | `/api/owner/pets` | 任意已认证 | 新增本人宠物 | 201 `PetResponse` | `VALIDATION_FAILED`；`RESOURCE_NOT_FOUND`（无主人档案） |
| GET | `/api/owner/pets/{petId}` | 本人或员工 | 宠物详情 | 200 `PetResponse` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |
| PUT | `/api/owner/pets/{petId}` | 归属主人 | 更新宠物 | 200 `PetResponse` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND`；`VALIDATION_FAILED` |
| DELETE | `/api/owner/pets/{petId}` | 归属主人 | 删除宠物（级联删除疫苗/过敏记录） | 204 | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |

```jsonc
// POST /api/owner/pets
{ "name": "旺财", "species": "犬", "birthDate": "2022-05-01", "weightKg": 5.50 }
// 201
{ "id": "<uuid>", "ownerId": "<uuid>", "name": "旺财", "species": "犬",
  "birthDate": "2022-05-01", "weightKg": 5.50, "createdAt": "<instant>", "updatedAt": "<instant>" }

// GET /api/owner/pets?page=0&size=20
{ "items": [ /* PetResponse */ ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

## 疫苗与过敏史

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| GET | `/api/owner/pets/{petId}/vaccinations` | 本人或员工 | 疫苗记录（按接种日期倒序） | 200 `VaccinationResponse[]` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |
| POST | `/api/owner/pets/{petId}/vaccinations` | 归属主人 | 新增疫苗记录 | 201 `VaccinationResponse` | 同上；`VALIDATION_FAILED` |
| GET | `/api/owner/pets/{petId}/allergies` | 本人或员工 | 过敏史（按创建时间正序） | 200 `AllergyResponse[]` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |
| POST | `/api/owner/pets/{petId}/allergies` | 归属主人 | 新增过敏记录 | 201 `AllergyResponse` | 同上；`VALIDATION_FAILED` |

```jsonc
// POST /api/owner/pets/{petId}/vaccinations
{ "vaccineName": "狂犬疫苗", "administeredOn": "2026-01-01", "nextDueOn": "2027-01-01", "note": null }
// POST /api/owner/pets/{petId}/allergies
{ "allergen": "花粉", "severity": "MODERATE", "note": null }
```

## 病历索引（T-13）

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| GET | `/api/owner/pets/{petId}/medical-records` | 本人或员工 | 经 `medical` Module API 只读聚合，不直连其表 | 200 `MedicalRecordSummaryResponse[]` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND` |

> 说明：`medical` 主数据已在 M4（T-19 ~ T-21）落地，`MedicalApi` 返回真实病历索引（按就诊时间倒序）；owner 侧只读聚合与越权校验经联调测试验证。

## 校验规则（Bean Validation）

| 字段 | 规则 |
|---|---|
| 主人 name | 1–100 |
| 主人 phone | `^\+?[0-9]{6,20}$` |
| 主人 address | ≤ 255，可空 |
| 宠物 name / species | 1–50 |
| 宠物 birthDate | 非空且不得为未来（`@PastOrPresent`） |
| 宠物 weightKg | `0.01 ~ 500.00` |
| 疫苗 vaccineName | 1–100 |
| 疫苗 administeredOn | 非空且不得为未来 |
| 过敏 allergen | 1–100 |
| 过敏 severity | `MILD / MODERATE / SEVERE` |

## 数据约束（DB 最终守卫）

- `owner.user_id` `UNIQUE`（一名用户至多一份档案）；`name` / `phone` 非空 `CHECK`。
- `pet.owner_id` `FK → owner ON DELETE CASCADE`；`weight_kg NUMERIC(5,2) CHECK (weight_kg > 0 AND weight_kg <= 500)`。
- `vaccination.pet_id` / `allergy.pet_id` `FK → pet ON DELETE CASCADE`；`allergy.severity CHECK IN ('MILD','MODERATE','SEVERE')`。
- `owner.user_id` 不设对 `users` 的外键：owner 与 identity 数据所有权独立，拆分服务时无需解耦。

## Module API

- `owner.OwnerApi#findPet(UUID) → Optional<PetSummary>`：供其他模块只读获取宠物归属与基本信息，禁止跨模块访问 owner 的 Repository / Entity。
