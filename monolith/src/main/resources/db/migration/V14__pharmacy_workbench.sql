-- pharmacy 的只读交接索引。升级时一次性复制历史；运行时只经 medical 事件更新。
CREATE TABLE pharmacy_prescription_task (
  prescription_id BINARY(16) NOT NULL PRIMARY KEY,
  pet_id BINARY(16) NOT NULL,
  doctor_id BINARY(16) NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  INDEX idx_pharmacy_task_issued (issued_at DESC, prescription_id),
  INDEX idx_pharmacy_task_pet (pet_id, issued_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO pharmacy_prescription_task (prescription_id, pet_id, doctor_id, issued_at)
SELECT p.id, r.pet_id, p.doctor_id, p.issued_at
FROM prescription p JOIN medical_record r ON r.id = p.medical_record_id;
