-- 幂等记录补充：记录该幂等键对应的业务资源，实现重试回放。
ALTER TABLE idempotency_record ADD COLUMN result_ref BINARY(16) NULL;

-- 同一医生排班不得从同一时刻开始；任意区间重叠由 Service 在事务内检查。
CREATE TABLE doctor_schedule (
    id BINARY(16) NOT NULL,
    doctor_id BINARY(16) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    slot_minutes INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_doctor_schedule_range CHECK (end_at > start_at),
    CONSTRAINT ck_doctor_schedule_slot CHECK (slot_minutes > 0 AND slot_minutes <= 480),
    CONSTRAINT uq_doctor_schedule_start UNIQUE (doctor_id, start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_doctor_schedule_doctor_start ON doctor_schedule (doctor_id, start_at);

-- MySQL 无 EXCLUDE 约束：用生成列 + 唯一键保证「同一医生同一开始时刻最多一条未取消预约」。
-- 已取消时 active_start_at 为 NULL，InnoDB 唯一索引允许多个 NULL，从而释放时段；
-- 任意区间的重叠由 Service 在持有排班悲观锁时检查。
CREATE TABLE appointment (
    id BINARY(16) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    doctor_id BINARY(16) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    checked_in_at DATETIME(6) NULL,
    called_at DATETIME(6) NULL,
    active_start_at DATETIME(6) GENERATED ALWAYS AS (
        CASE WHEN status <> 'CANCELLED' THEN start_at ELSE NULL END
    ) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_appointment_range CHECK (end_at > start_at),
    CONSTRAINT ck_appointment_status
        CHECK (status IN ('SCHEDULED', 'CHECKED_IN', 'CALLED', 'CANCELLED')),
    CONSTRAINT uq_appointment_active_slot UNIQUE (doctor_id, active_start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_appointment_doctor_start ON appointment (doctor_id, start_at);
CREATE INDEX idx_appointment_pet ON appointment (pet_id);
