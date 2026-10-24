-- M6 收费与账单。billing 拥有收费项目、账单、账单明细、支付记录与操作审计。
-- pet_id / medical_record_id / prescription_id / created_by / paid_by / refunded_by / operator_id
-- 均为跨模块引用，故意不建跨模块外键；病历与处方归属经 medical.MedicalApi、宠物归属经 owner.OwnerApi 校验。

CREATE TABLE charge_item (
    id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_charge_item_name UNIQUE (name),
    CONSTRAINT ck_charge_item_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    -- 金额精度与下界由数据库最终守卫。
    CONSTRAINT ck_charge_item_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_charge_item_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_charge_item_name ON charge_item (name);

CREATE TABLE bill (
    id BINARY(16) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    medical_record_id BINARY(16) NOT NULL,
    prescription_id BINARY(16) NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_bill_total_non_negative CHECK (total_amount >= 0),
    CONSTRAINT ck_bill_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_bill_status CHECK (status IN ('UNPAID', 'PAID', 'REFUNDED', 'VOID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_bill_pet_created ON bill (pet_id, created_at DESC);
CREATE INDEX idx_bill_medical_record ON bill (medical_record_id);
CREATE INDEX idx_bill_prescription ON bill (prescription_id);

CREATE TABLE bill_item (
    id BINARY(16) NOT NULL,
    bill_id BINARY(16) NOT NULL,
    charge_item_id BINARY(16) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bill_item_bill FOREIGN KEY (bill_id) REFERENCES bill (id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_item_charge_item FOREIGN KEY (charge_item_id)
        REFERENCES charge_item (id) ON DELETE RESTRICT,
    -- 同一账单中同一收费项目只出现一行（重复行在 Service 层也已拒绝）。
    CONSTRAINT uq_bill_item_charge_item UNIQUE (bill_id, charge_item_id),
    CONSTRAINT ck_bill_item_name_not_blank CHECK (CHAR_LENGTH(TRIM(item_name)) > 0),
    CONSTRAINT ck_bill_item_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_bill_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_bill_item_amount_non_negative CHECK (amount >= 0),
    -- 行金额必须等于单价 × 数量，杜绝明细金额与单价脱节。
    CONSTRAINT ck_bill_item_amount_consistent CHECK (amount = unit_price * quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_bill_item_bill ON bill_item (bill_id);

CREATE TABLE payment_record (
    id BINARY(16) NOT NULL,
    bill_id BINARY(16) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_by BINARY(16) NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    refunded_by BINARY(16) NULL,
    refunded_at DATETIME(6) NULL,
    refund_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 一张账单至多一条支付记录；并发重复支付由该唯一键兜底。
    CONSTRAINT uq_payment_record_bill UNIQUE (bill_id),
    CONSTRAINT fk_payment_record_bill FOREIGN KEY (bill_id) REFERENCES bill (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_record_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_payment_record_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_payment_record_method CHECK (method IN ('MOCK')),
    CONSTRAINT ck_payment_record_status CHECK (status IN ('SUCCEEDED', 'REFUNDED')),
    -- 退款字段与状态必须一致，避免「已退款但没有退款人 / 时间」。
    CONSTRAINT ck_payment_record_refund_fields CHECK (
        (status = 'SUCCEEDED' AND refunded_by IS NULL AND refunded_at IS NULL)
        OR (status = 'REFUNDED' AND refunded_by IS NOT NULL AND refunded_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_record_paid_at ON payment_record (paid_at DESC);

CREATE TABLE bill_operation (
    id BINARY(16) NOT NULL,
    bill_id BINARY(16) NOT NULL,
    action VARCHAR(20) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NULL,
    operator_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bill_operation_bill FOREIGN KEY (bill_id) REFERENCES bill (id) ON DELETE CASCADE,
    -- 动作与状态迁移必须匹配，审计流水不能出现无意义的组合。
    CONSTRAINT ck_bill_operation_transition CHECK (
        (action = 'CREATED' AND from_status IS NULL AND to_status = 'UNPAID')
        OR (action = 'PAID' AND from_status = 'UNPAID' AND to_status = 'PAID')
        OR (action = 'REFUNDED' AND from_status = 'PAID' AND to_status = 'REFUNDED')
        OR (action = 'VOIDED' AND from_status = 'UNPAID' AND to_status = 'VOID')
    ),
    CONSTRAINT ck_bill_operation_reason_not_blank CHECK (
        reason IS NULL OR CHAR_LENGTH(TRIM(reason)) > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_bill_operation_bill_created ON bill_operation (bill_id, created_at DESC);
