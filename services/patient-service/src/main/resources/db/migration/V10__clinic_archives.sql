-- Existing profiles remain bound; new staff-created profiles may be unbound.
ALTER TABLE owner MODIFY user_id BINARY(16) NULL, MODIFY phone VARCHAR(21) NOT NULL,
  ADD archived_at DATETIME(6), ADD archived_by BINARY(16), ADD archive_reason VARCHAR(255),
  ADD CONSTRAINT ck_owner_archive CHECK ((archived_at IS NULL AND archived_by IS NULL AND archive_reason IS NULL) OR (archived_at IS NOT NULL AND archived_by IS NOT NULL AND archive_reason IS NOT NULL AND CHAR_LENGTH(TRIM(archive_reason)) > 0));
ALTER TABLE pet ADD birth_date_estimated BOOLEAN NOT NULL DEFAULT FALSE,
  ADD archived_at DATETIME(6), ADD archived_by BINARY(16), ADD archive_reason VARCHAR(255),
  ADD CONSTRAINT ck_pet_archive CHECK ((archived_at IS NULL AND archived_by IS NULL AND archive_reason IS NULL) OR (archived_at IS NOT NULL AND archived_by IS NOT NULL AND archive_reason IS NOT NULL AND CHAR_LENGTH(TRIM(archive_reason)) > 0));
-- Preserve history: replace cascades with RESTRICT even for direct database deletions.
ALTER TABLE pet DROP FOREIGN KEY fk_pet_owner, ADD CONSTRAINT fk_pet_owner_retained FOREIGN KEY (owner_id) REFERENCES owner(id) ON DELETE RESTRICT;
ALTER TABLE vaccination DROP FOREIGN KEY fk_vaccination_pet, ADD CONSTRAINT fk_vaccination_pet_retained FOREIGN KEY (pet_id) REFERENCES pet(id) ON DELETE RESTRICT,
  ADD recorded_by BINARY(16), ADD recorded_by_name VARCHAR(100), ADD supersedes_id BINARY(16), ADD correction_reason VARCHAR(255),
  ADD CONSTRAINT uq_vaccination_supersedes UNIQUE(supersedes_id),
  ADD CONSTRAINT fk_vaccination_supersedes FOREIGN KEY(supersedes_id) REFERENCES vaccination(id) ON DELETE RESTRICT;
ALTER TABLE allergy DROP FOREIGN KEY fk_allergy_pet, ADD CONSTRAINT fk_allergy_pet_retained FOREIGN KEY (pet_id) REFERENCES pet(id) ON DELETE RESTRICT,
  ADD recorded_by BINARY(16), ADD recorded_by_name VARCHAR(100), ADD supersedes_id BINARY(16), ADD correction_reason VARCHAR(255),
  ADD CONSTRAINT uq_allergy_supersedes UNIQUE(supersedes_id),
  ADD CONSTRAINT fk_allergy_supersedes FOREIGN KEY(supersedes_id) REFERENCES allergy(id) ON DELETE RESTRICT;
-- Pre-migration health records have unknown actor; never fabricate historical attribution.
CREATE TABLE owner_operation (
  id BINARY(16) NOT NULL PRIMARY KEY, target_id BINARY(16) NOT NULL, action VARCHAR(30) NOT NULL,
  actor_id BINARY(16) NOT NULL, actor_name VARCHAR(100) NOT NULL, reason VARCHAR(255),
  before_json LONGTEXT, after_json LONGTEXT NOT NULL, created_at DATETIME(6) NOT NULL,
  INDEX idx_owner_operation_target(target_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX idx_owner_archived_created ON owner(archived_at,created_at,id);
CREATE INDEX idx_pet_archived_created ON pet(archived_at,created_at,id);
