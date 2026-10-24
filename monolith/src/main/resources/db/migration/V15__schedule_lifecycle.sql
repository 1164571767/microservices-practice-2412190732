ALTER TABLE doctor_schedule
    ADD COLUMN state VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    ADD CONSTRAINT ck_schedule_state CHECK (state IN ('OPEN','BOOKING_CLOSED','CANCELLED')),
    DROP INDEX uq_doctor_schedule_start,
    ADD COLUMN active_start_at DATETIME(6) GENERATED ALWAYS AS
        (CASE WHEN state <> 'CANCELLED' THEN start_at ELSE NULL END) STORED,
    ADD CONSTRAINT uq_doctor_schedule_active_start UNIQUE (doctor_id, active_start_at);

CREATE TABLE schedule_guard (
    doctor_id BINARY(16) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

ALTER TABLE appointment ADD COLUMN schedule_id BINARY(16) NULL,
    ADD INDEX idx_appointment_schedule (schedule_id, status);

-- Only uniquely covered legacy appointments can acquire a definite schedule.
UPDATE appointment a JOIN (
    SELECT a0.id, UNHEX(MIN(HEX(s.id))) AS schedule_id
    FROM appointment a0 JOIN doctor_schedule s ON s.doctor_id = a0.doctor_id
        AND s.start_at <= a0.start_at AND s.end_at >= a0.end_at
    GROUP BY a0.id HAVING COUNT(*) = 1
) mapped ON mapped.id = a.id SET a.schedule_id = mapped.schedule_id;

CREATE TABLE appointment_schedule_history (
    appointment_id BINARY(16) NOT NULL,
    schedule_id BINARY(16) NOT NULL,
    PRIMARY KEY (appointment_id, schedule_id),
    INDEX idx_schedule_history (schedule_id),
    CONSTRAINT fk_schedule_history_schedule FOREIGN KEY (schedule_id) REFERENCES doctor_schedule(id),
    CONSTRAINT fk_schedule_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointment(id)
) ENGINE=InnoDB;
INSERT INTO appointment_schedule_history (appointment_id, schedule_id)
    SELECT id, schedule_id FROM appointment WHERE schedule_id IS NOT NULL;
ALTER TABLE appointment ADD CONSTRAINT fk_appointment_schedule
    FOREIGN KEY (schedule_id) REFERENCES doctor_schedule(id);

-- Audit deliberately has no schedule FK, so safe deletion still leaves evidence.
CREATE TABLE schedule_operation (
    id BINARY(16) NOT NULL PRIMARY KEY,
    schedule_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    actor_name VARCHAR(50) NOT NULL,
    action VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    INDEX idx_schedule_operation (schedule_id, occurred_at, id)
) ENGINE=InnoDB;
