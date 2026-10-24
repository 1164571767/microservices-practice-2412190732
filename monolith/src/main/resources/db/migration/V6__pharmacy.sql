-- M5 药品与库存。pharmacy 拥有药品目录、库存、出入库流水与发药记录。
-- prescription_id / medical_record_id / pet_id / doctor_id / operator_id 均为跨模块引用，
-- 故意不建跨模块外键；归属与处方内容经 medical.MedicalApi 只读获取（见 ADR-0009）。

-- 新增药房角色；先删除旧 CHECK 再以扩展后的枚举重建。
ALTER TABLE user_role DROP CHECK ck_user_role_role;
ALTER TABLE user_role ADD CONSTRAINT ck_user_role_role
    CHECK (role IN ('OWNER', 'DOCTOR', 'STAFF', 'ADMIN', 'PHARMACY'));

CREATE TABLE drug (
    id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    specification VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    low_stock_threshold INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 同一药品名称 + 规格唯一，避免目录中出现两个同规格条目。
    CONSTRAINT uq_drug_name_spec UNIQUE (name, specification),
    CONSTRAINT ck_drug_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_drug_spec_not_blank CHECK (CHAR_LENGTH(TRIM(specification)) > 0),
    CONSTRAINT ck_drug_unit_not_blank CHECK (CHAR_LENGTH(TRIM(unit)) > 0),
    CONSTRAINT ck_drug_threshold_non_negative CHECK (low_stock_threshold >= 0),
    CONSTRAINT ck_drug_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_drug_name ON drug (name);

CREATE TABLE inventory (
    drug_id BINARY(16) NOT NULL,
    quantity INT NOT NULL,
    low_stock_notified_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (drug_id),
    CONSTRAINT fk_inventory_drug FOREIGN KEY (drug_id) REFERENCES drug (id) ON DELETE RESTRICT,
    -- 数据库最终守卫：库存绝不为负。
    CONSTRAINT ck_inventory_quantity_non_negative CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_adjustment (
    id BINARY(16) NOT NULL,
    drug_id BINARY(16) NOT NULL,
    delta INT NOT NULL,
    quantity_before INT NOT NULL,
    quantity_after INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    operator_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inventory_adjustment_drug FOREIGN KEY (drug_id) REFERENCES drug (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_adjustment_delta_non_zero CHECK (delta <> 0),
    CONSTRAINT ck_inventory_adjustment_before_non_negative CHECK (quantity_before >= 0),
    CONSTRAINT ck_inventory_adjustment_after_non_negative CHECK (quantity_after >= 0),
    CONSTRAINT ck_inventory_adjustment_reason_not_blank CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_inventory_adjustment_drug_created
    ON inventory_adjustment (drug_id, created_at DESC);

CREATE TABLE dispense_record (
    id BINARY(16) NOT NULL,
    prescription_id BINARY(16) NOT NULL,
    medical_record_id BINARY(16) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    doctor_id BINARY(16) NOT NULL,
    dispensed_by BINARY(16) NOT NULL,
    dispensed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 一张处方至多发一次药；并发重复发药由该唯一键兜底。
    CONSTRAINT uq_dispense_record_prescription UNIQUE (prescription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_dispense_record_pet_dispensed
    ON dispense_record (pet_id, dispensed_at DESC);

CREATE TABLE dispense_item (
    id BINARY(16) NOT NULL,
    dispense_id BINARY(16) NOT NULL,
    drug_id BINARY(16) NOT NULL,
    drug_name VARCHAR(100) NOT NULL,
    specification VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    stock_after INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_dispense_item_record FOREIGN KEY (dispense_id)
        REFERENCES dispense_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_dispense_item_drug FOREIGN KEY (drug_id) REFERENCES drug (id) ON DELETE RESTRICT,
    -- 同一张发药单中同一药品聚合为一行。
    CONSTRAINT uq_dispense_item_drug UNIQUE (dispense_id, drug_id),
    CONSTRAINT ck_dispense_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_dispense_item_stock_after_non_negative CHECK (stock_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_dispense_item_dispense ON dispense_item (dispense_id);
