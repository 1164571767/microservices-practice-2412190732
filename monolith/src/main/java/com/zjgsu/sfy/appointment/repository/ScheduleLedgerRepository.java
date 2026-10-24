package com.zjgsu.sfy.appointment.repository;

import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import com.zjgsu.sfy.appointment.service.result.ScheduleOperation;
import com.zjgsu.sfy.identity.CurrentUser;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** All SQL remains within appointment-owned tables and the caller's JPA transaction. */
@Repository
public class ScheduleLedgerRepository {
  private final JdbcTemplate jdbc;

  public ScheduleLedgerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Upsert locks one stable row, including a doctor's first-ever schedule. */
  public void lockDoctor(UUID doctorId) {
    jdbc.update(
        "INSERT INTO schedule_guard (doctor_id) VALUES (?) ON DUPLICATE KEY UPDATE doctor_id = doctor_id",
        bytes(doctorId));
  }

  public void remember(Appointment appointment) {
    jdbc.update(
        "INSERT IGNORE INTO appointment_schedule_history (appointment_id,schedule_id) VALUES (?,?)",
        bytes(appointment.getId()),
        bytes(appointment.getScheduleId()));
  }

  public void rememberLegacy(Appointment appointment) {
    if (appointment.getScheduleId() == null) {
      jdbc.update(
          "INSERT IGNORE INTO appointment_schedule_history (appointment_id,schedule_id) SELECT ?,id FROM doctor_schedule WHERE doctor_id=? AND start_at<? AND end_at>?",
          bytes(appointment.getId()),
          bytes(appointment.getDoctorId()),
          Timestamp.from(appointment.getEndAt()),
          Timestamp.from(appointment.getStartAt()));
    }
  }

  public boolean hasHistory(DoctorSchedule s) {
    return jdbc.queryForObject(
        "SELECT (EXISTS(SELECT 1 FROM appointment_schedule_history WHERE schedule_id=?) OR EXISTS(SELECT 1 FROM appointment WHERE schedule_id=? OR (schedule_id IS NULL AND doctor_id=? AND start_at<? AND end_at>?)))",
        Boolean.class,
        bytes(s.getId()),
        bytes(s.getId()),
        bytes(s.getDoctorId()),
        Timestamp.from(s.getEndAt()),
        Timestamp.from(s.getStartAt()));
  }

  public boolean hasActiveAppointments(DoctorSchedule s) {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM appointment WHERE status<>'CANCELLED' AND (schedule_id=? OR (schedule_id IS NULL AND doctor_id=? AND start_at<? AND end_at>?)))",
        Boolean.class,
        bytes(s.getId()),
        bytes(s.getDoctorId()),
        Timestamp.from(s.getEndAt()),
        Timestamp.from(s.getStartAt()));
  }

  public void audit(UUID scheduleId, CurrentUser actor, String action, String reason, Instant now) {
    jdbc.update(
        "INSERT INTO schedule_operation (id,schedule_id,actor_id,actor_name,action,reason,occurred_at) VALUES (?,?,?,?,?,?,?)",
        bytes(UUID.randomUUID()),
        bytes(scheduleId),
        bytes(actor.userId()),
        actor.username(),
        action,
        reason,
        Timestamp.from(now));
  }

  public List<ScheduleOperation> operations(UUID scheduleId, int page) {
    return jdbc.query(
        "SELECT id,actor_id,actor_name,action,reason,occurred_at FROM schedule_operation WHERE schedule_id=? ORDER BY occurred_at DESC,id DESC LIMIT 20 OFFSET ?",
        (rs, row) ->
            new ScheduleOperation(
                uuid(rs.getBytes(1)),
                uuid(rs.getBytes(2)),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getTimestamp(6).toInstant()),
        bytes(scheduleId),
        (long) page * 20);
  }

  private static byte[] bytes(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }

  private static UUID uuid(byte[] value) {
    ByteBuffer b = ByteBuffer.wrap(value);
    return new UUID(b.getLong(), b.getLong());
  }
}
