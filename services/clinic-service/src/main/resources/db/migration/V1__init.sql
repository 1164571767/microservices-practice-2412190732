CREATE TABLE idempotency_record (
    id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_record_key UNIQUE (idempotency_key),
    CONSTRAINT ck_idempotency_record_key_not_blank CHECK (CHAR_LENGTH(idempotency_key) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
