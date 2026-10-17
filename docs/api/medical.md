# medical API（M4 门诊病历与处方）

- 统一错误体：`{"code": "<稳定机器码>", "message": "<可读信息>"}`；客户端依赖 `code`，见 `error-codes.md`。
- 认证方式：`Authorization: Bearer <accessToken>`；本模块接口均需认证。
- 时间统一用 **UTC ISO-8601**（如 `2026-09-15T01:05:00Z`）。
- 写侧权限：仅**接诊医生**可写。角色须为 `DOCTOR`，且请求者必须是该预约的接诊医生（否则 `AUTH_FORBIDDEN`）。基线校验（接诊医生）由 `MedicalWritePolicy` 保证；可用 `MedicalWriteGuard` 追加校验（见 ADR-0006）。

## 病历（T-19）

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| POST | `/api/medical/records` | `DOCTOR`（接诊医生） | 记录接诊病历；症状/诊断/医嘱结构化存储 | 201 `MedicalRecordResponse` | `AUTH_FORBIDDEN`（非接诊医生 / 非医生）；`RESOURCE_NOT_FOUND`（预约不存在）；`OPERATION_NOT_ALLOWED`（该预约已有病历）；`VALIDATION_FAILED` |

```jsonc
// POST /api/medical/records
{ "appointmentId": "<uuid>", "symptoms": "咳嗽、食欲下降", "diagnosis": "急性支气管炎",
  "advice": "多休息、清淡饮食", "visitedAt": "2026-09-15T01:05:00Z" }
// 201
{ "id": "<uuid>", "appointmentId": "<uuid>", "petId": "<uuid>", "doctorId": "<uuid>",
  "symptoms": "咳嗽、食欲下降", "diagnosis": "急性支气管炎", "advice": "多休息、清淡饮食",
  "visitedAt": "2026-09-15T01:05:00Z", "createdAt": "<instant>", "prescriptions": [] }
```

- `petId` 与 `doctorId` 取自预约（服务端决定，不接受客户端传入）。
- `visitedAt` 可省略，默认取服务端当前时间；不得为未来。
- 一次预约至多一份病历（DB 唯一约束兜底）。

## 处方（T-20）

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| POST | `/api/medical/records/{recordId}/prescriptions` | `DOCTOR`（接诊医生） | 为病历开具一张处方 | 201 `PrescriptionResponse` | `AUTH_FORBIDDEN`；`RESOURCE_NOT_FOUND`（病历不存在）；`OPERATION_NOT_ALLOWED`（该病历已开具处方）；`VALIDATION_FAILED` |

```jsonc
// POST /api/medical/records/{recordId}/prescriptions
{ "items": [
  { "drugName": "阿莫西林", "specification": "0.5g", "dosage": "1片", "frequency": "每日2次",
    "quantity": 6, "days": 5, "note": "饭后服用" } ] }
// 201
{ "id": "<uuid>", "medicalRecordId": "<uuid>", "doctorId": "<uuid>", "issuedAt": "<instant>",
  "items": [ { "id": "<uuid>", "drugName": "阿莫西林", "specification": "0.5g", "dosage": "1片",
    "frequency": "每日2次", "quantity": 6, "days": 5, "note": "饭后服用" } ] }
```

- 药品信息**内嵌**于处方明细行（名称/规格/剂量/频次/数量/天数），处方自包含。
- M5 pharmacy（药品目录 / 库存）落地后经 `MedicalApi` 对接发药与库存，此处不提前引入其领域。
- 一份病历至多一张处方（DB 唯一约束兜底）。

## 历史病历（T-21）

| 方法 | 路径 | 权限 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|---|
| GET | `/api/medical/pets/{petId}/records?page=0&size=20` | `STAFF/DOCTOR/ADMIN` | 临床视角：某宠物历史病历（含处方），按就诊时间倒序 | 200 `PageResponse<MedicalRecordResponse>` | `AUTH_FORBIDDEN` |

```jsonc
// GET /api/medical/pets/{petId}/records
{ "items": [ { "id": "<uuid>", "diagnosis": "急性支气管炎", "prescriptions": [ /* … */ ] } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

- **无 N+1**：某页的病历一次取回后，处方与明细**各一次**批量加载；查询条数不随记录数增长（有测试断言）。
- **主人视角**：主人经 `GET /api/owner/pets/{petId}/medical-records`（T-13）只读聚合查看自己宠物的病历索引；归属校验在 owner 侧完成，owner 不直连 medical 表。临床历史查询接口限临床角色。

## 校验规则（Bean Validation）

| 字段 | 规则 |
|---|---|
| record.appointmentId | 非空 |
| record.symptoms / diagnosis | 非空，≤ 2000 |
| record.advice | ≤ 2000，可空 |
| record.visitedAt | 可空；不得为未来（`@PastOrPresent`） |
| prescription.items | 非空列表 |
| item.drugName / dosage / frequency | 非空，≤ 100 |
| item.specification | ≤ 100，可空 |
| item.quantity / days | ≥ 1 |
| item.note | ≤ 255，可空 |

## 数据约束（DB 最终守卫）

- `medical_record`：`UNIQUE (appointment_id)`；`CHECK (length(trim(symptoms)) > 0)`、`CHECK (length(trim(diagnosis)) > 0)`。
- `prescription`：`UNIQUE (medical_record_id)`；`FK → medical_record ON DELETE CASCADE`。
- `prescription_item`：`FK → prescription ON DELETE CASCADE`；`CHECK (quantity > 0 AND days > 0)`。
- `appointment_id` / `pet_id` / `doctor_id` **不建跨模块外键**：模块数据所有权独立，归属与接诊校验经端口 / Module API 完成（见 ADR-0003、ADR-0006）。

## Module API 与端口

- `medical.MedicalApi#findRecordsByPet(UUID petId) → List<MedicalRecordSummary>`：供其他模块（owner）只读聚合病历索引。
- `medical.AppointmentLookup`：medical 所需的预约只读端口，由 `appointment` 模块实现（依赖方向 `appointment → medical`）。
