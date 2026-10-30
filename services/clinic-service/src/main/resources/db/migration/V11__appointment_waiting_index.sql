-- Queue claims must stop at one unlocked row without filesort locking the full waiting range.
-- Equality prefix + stable order supports LIMIT 1 FOR UPDATE SKIP LOCKED.
CREATE INDEX idx_appointment_waiting ON appointment (doctor_id, status, checked_in_at, id);
