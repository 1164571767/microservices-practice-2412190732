-- Consultation state belongs to medical; appointment references remain module-owned.
CREATE TABLE medical_consultation (
    appointment_id BINARY(16) NOT NULL PRIMARY KEY,
    draft_json MEDIUMTEXT NULL,
    prescription_choice VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_consultation_choice CHECK (prescription_choice IN ('PENDING','NONE','PRESCRIPTION')),
    CONSTRAINT ck_consultation_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE medical_consultation_operation (
    id BINARY(16) NOT NULL PRIMARY KEY,
    appointment_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_consultation_action CHECK (action IN ('DRAFT_SAVED','SUBMITTED','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_consultation_operation ON medical_consultation_operation(appointment_id,occurred_at,id);
