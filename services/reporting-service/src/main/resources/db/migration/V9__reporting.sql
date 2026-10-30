-- M8 统计报表。reporting 只拥有汇总事实表，不拥有任何业务主数据。
-- record_id / bill_id / payment_id / pet_id / doctor_id / drug_id / dispense_id 均为跨模块引用，
-- 故意不建跨模块外键；事实由领域事件投影写入，源数据仍以各业务模块为准。

CREATE TABLE reporting_visit_fact (
    id BINARY(16) NOT NULL,
    record_id BINARY(16) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    doctor_id BINARY(16) NOT NULL,
    visited_at DATETIME(6) NOT NULL,
    -- 统计日 = visited_at 在配置时区（默认 Asia/Shanghai）的本地日期。
    stat_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 消费幂等的最终守卫：同一病历重复投递只落一行。
    CONSTRAINT uq_reporting_visit_record UNIQUE (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_reporting_visit_date ON reporting_visit_fact (stat_date, doctor_id);
CREATE INDEX idx_reporting_visit_doctor ON reporting_visit_fact (doctor_id, stat_date);

CREATE TABLE reporting_revenue_fact (
    id BINARY(16) NOT NULL,
    bill_id BINARY(16) NOT NULL,
    payment_id BINARY(16) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    stat_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 同一账单的支付 / 退款各只允许一行；重复事件被唯一键忽略。
    CONSTRAINT uq_reporting_revenue_source UNIQUE (bill_id, kind),
    CONSTRAINT ck_reporting_revenue_kind CHECK (kind IN ('PAID', 'REFUNDED')),
    CONSTRAINT ck_reporting_revenue_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_reporting_revenue_currency CHECK (currency = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_reporting_revenue_date ON reporting_revenue_fact (stat_date, kind);

CREATE TABLE reporting_drug_consumption_fact (
    id BINARY(16) NOT NULL,
    dispense_id BINARY(16) NOT NULL,
    drug_id BINARY(16) NOT NULL,
    drug_name VARCHAR(100) NOT NULL,
    specification VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    dispensed_at DATETIME(6) NOT NULL,
    stat_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 一次发药中的同一药品只落一行（发药侧已按药品聚合）。
    CONSTRAINT uq_reporting_drug_dispense UNIQUE (dispense_id, drug_id),
    CONSTRAINT ck_reporting_drug_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_reporting_drug_name_not_blank CHECK (CHAR_LENGTH(TRIM(drug_name)) > 0),
    CONSTRAINT ck_reporting_drug_spec_not_blank CHECK (CHAR_LENGTH(TRIM(specification)) > 0),
    CONSTRAINT ck_reporting_drug_unit_not_blank CHECK (CHAR_LENGTH(TRIM(unit)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_reporting_drug_date ON reporting_drug_consumption_fact (stat_date, drug_id);
