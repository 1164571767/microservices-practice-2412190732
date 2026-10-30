-- 门诊病历与处方。appointment_id / pet_id / doctor_id 均为跨模块引用，故意不建外键，
-- 归属与接诊校验经 Module API / 端口完成（见 ADR-0003、ADR-0006）。

CREATE TABLE medical_record (
    id BINARY(16) NOT NULL,
    appointment_id BINARY(16) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    doctor_id BINARY(16) NOT NULL,
    symptoms VARCHAR(2000) NOT NULL,
    diagnosis VARCHAR(2000) NOT NULL,
    advice VARCHAR(2000),
    visited_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 一次预约至多一份病历。
    CONSTRAINT uq_medical_record_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_medical_record_symptoms_not_blank CHECK (CHAR_LENGTH(TRIM(symptoms)) > 0),
    CONSTRAINT ck_medical_record_diagnosis_not_blank CHECK (CHAR_LENGTH(TRIM(diagnosis)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_medical_record_pet_visited ON medical_record (pet_id, visited_at DESC);

CREATE TABLE prescription (
    id BINARY(16) NOT NULL,
    medical_record_id BINARY(16) NOT NULL,
    doctor_id BINARY(16) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 一份病历至多一张处方。
    CONSTRAINT uq_prescription_record UNIQUE (medical_record_id),
    CONSTRAINT fk_prescription_record FOREIGN KEY (medical_record_id)
        REFERENCES medical_record (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_prescription_record ON prescription (medical_record_id);

CREATE TABLE prescription_item (
    id BINARY(16) NOT NULL,
    prescription_id BINARY(16) NOT NULL,
    drug_name VARCHAR(100) NOT NULL,
    specification VARCHAR(100),
    dosage VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    days INT NOT NULL,
    note VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_prescription_item_prescription FOREIGN KEY (prescription_id)
        REFERENCES prescription (id) ON DELETE CASCADE,
    CONSTRAINT ck_prescription_item_drug_not_blank CHECK (CHAR_LENGTH(TRIM(drug_name)) > 0),
    CONSTRAINT ck_prescription_item_dosage_not_blank CHECK (CHAR_LENGTH(TRIM(dosage)) > 0),
    CONSTRAINT ck_prescription_item_frequency_not_blank CHECK (CHAR_LENGTH(TRIM(frequency)) > 0),
    CONSTRAINT ck_prescription_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_prescription_item_days_positive CHECK (days > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_prescription_item_prescription ON prescription_item (prescription_id);
