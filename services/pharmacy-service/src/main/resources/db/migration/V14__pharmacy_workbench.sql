-- pharmacy 的本地处方索引；通过 HTTP 分页查询已提交处方后幂等刷新。
CREATE TABLE pharmacy_prescription_task (
  prescription_id BINARY(16) NOT NULL PRIMARY KEY,
  pet_id BINARY(16) NOT NULL,
  doctor_id BINARY(16) NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  INDEX idx_pharmacy_task_issued (issued_at DESC, prescription_id),
  INDEX idx_pharmacy_task_pet (pet_id, issued_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
