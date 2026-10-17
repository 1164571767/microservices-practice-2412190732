package com.zjgsu.sfy.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 医生排班时段；同一医生的时段不允许重叠（DB 排除约束兜底）。 */
@Entity
@Table(name = "doctor_schedule")
public class DoctorSchedule {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @Column(name = "slot_minutes", nullable = false)
  private int slotMinutes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected DoctorSchedule() {}

  public static DoctorSchedule create(
      UUID doctorId, Instant startAt, Instant endAt, int slotMinutes) {
    Objects.requireNonNull(doctorId, "doctorId must not be null");
    Objects.requireNonNull(startAt, "startAt must not be null");
    Objects.requireNonNull(endAt, "endAt must not be null");
    if (!endAt.isAfter(startAt)) {
      throw new IllegalArgumentException("end must be after start");
    }
    if (slotMinutes <= 0 || slotMinutes > 480) {
      throw new IllegalArgumentException("slot minutes out of range");
    }
    DoctorSchedule schedule = new DoctorSchedule();
    schedule.doctorId = doctorId;
    schedule.startAt = startAt;
    schedule.endAt = endAt;
    schedule.slotMinutes = slotMinutes;
    return schedule;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public boolean covers(Instant rangeStart, Instant rangeEnd) {
    return !rangeStart.isBefore(startAt) && !rangeEnd.isAfter(endAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }

  public int getSlotMinutes() {
    return slotMinutes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
