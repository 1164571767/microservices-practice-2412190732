package com.zjgsu.sfy.medical.repository;

import com.zjgsu.sfy.medical.service.command.ConsultationDraft;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** SQL is restricted to medical-owned tables and joins the caller's JPA transaction. */
@Repository
public class ConsultationRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public ConsultationRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public record State(
      long version,
      ConsultationDraft draft,
      String prescriptionChoice,
      Instant completedAt,
      Instant updatedAt) {}

  public record Operation(UUID id, UUID actorId, String action, Instant occurredAt) {}

  public State get(UUID id) {
    return jdbc
        .query(
            "SELECT revision,draft_json,prescription_choice,completed_at,updated_at FROM medical_consultation WHERE appointment_id=?",
            (rs, n) ->
                new State(
                    rs.getLong(1),
                    rs.getString(2) == null
                        ? null
                        : json.readValue(rs.getString(2), ConsultationDraft.class),
                    rs.getString(3),
                    rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(),
                    rs.getTimestamp(5).toInstant()),
            bytes(id))
        .stream()
        .findFirst()
        .orElse(new State(0, null, "PENDING", null, null));
  }

  /** Caller holds the stable appointment lock, including the first draft save. */
  public void save(UUID id, State state, UUID actorId, String action) {
    Instant now = Instant.now();
    jdbc.update(
        "INSERT INTO medical_consultation(appointment_id,revision,draft_json,prescription_choice,completed_at,updated_at) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE revision=VALUES(revision),draft_json=VALUES(draft_json),prescription_choice=VALUES(prescription_choice),completed_at=VALUES(completed_at),updated_at=VALUES(updated_at)",
        bytes(id),
        state.version(),
        state.draft() == null ? null : json.writeValueAsString(state.draft()),
        state.prescriptionChoice(),
        state.completedAt() == null ? null : Timestamp.from(state.completedAt()),
        Timestamp.from(now));
    jdbc.update(
        "INSERT INTO medical_consultation_operation(id,appointment_id,actor_id,action,occurred_at) VALUES (?,?,?,?,?)",
        bytes(UUID.randomUUID()),
        bytes(id),
        bytes(actorId),
        action,
        Timestamp.from(now));
  }

  public List<Operation> operations(UUID id, int page) {
    return jdbc.query(
        "SELECT id,actor_id,action,occurred_at FROM medical_consultation_operation WHERE appointment_id=? ORDER BY occurred_at DESC,id DESC LIMIT 20 OFFSET ?",
        (rs, n) ->
            new Operation(
                uuid(rs.getBytes(1)),
                uuid(rs.getBytes(2)),
                rs.getString(3),
                rs.getTimestamp(4).toInstant()),
        bytes(id),
        (long) page * 20);
  }

  public java.util.Map<UUID, com.zjgsu.sfy.medical.ConsultationSummary> summaries(
      java.util.Collection<UUID> ids) {
    if (ids.isEmpty()) return java.util.Map.of();
    String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
    var rows =
        jdbc.query(
            "SELECT appointment_id,draft_json IS NOT NULL,completed_at FROM medical_consultation WHERE appointment_id IN ("
                + placeholders
                + ")",
            (rs, n) ->
                new com.zjgsu.sfy.medical.ConsultationSummary(
                    uuid(rs.getBytes(1)),
                    null,
                    rs.getBoolean(2),
                    rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()),
            ids.stream().map(ConsultationRepository::bytes).toArray());
    return rows.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                com.zjgsu.sfy.medical.ConsultationSummary::appointmentId, v -> v));
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
