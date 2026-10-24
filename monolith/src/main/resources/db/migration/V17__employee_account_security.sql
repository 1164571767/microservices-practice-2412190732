ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE identity_management_guard (id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO identity_management_guard (id) VALUES (1);

CREATE TABLE identity_account_audit (
    id BINARY(16) NOT NULL PRIMARY KEY,
    target_user_id BINARY(16) NOT NULL,
    actor_user_id BINARY(16) NOT NULL,
    action VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_summary VARCHAR(1000) NULL,
    after_summary VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_account_audit_target (target_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
