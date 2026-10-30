package com.zjgsu.sfy.appointment.entity;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 预约聚合根；状态只能经领域方法流转，非法流转抛异常。 */
@Entity
@Table(name = "appointment")
public class Appointment {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  public UUID getScheduleId() {
    return scheduleId;
  }

  public void assignSchedule(UUID scheduleId) {
    this.scheduleId = Objects.requireNonNull(scheduleId);
  }

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AppointmentStatus status;

  @Column(name = "checked_in_at")
  private Instant checkedInAt;

  @Column(name = "called_at")
  private Instant calledAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Appointment() {}

  public static Appointment book(UUID petId, UUID doctorId, Instant startAt, Instant endAt) {
    Objects.requireNonNull(petId, "petId must not be null");
    Objects.requireNonNull(doctorId, "doctorId must not be null");
    Objects.requireNonNull(startAt, "startAt must not be null");
    Objects.requireNonNull(endAt, "endAt must not be null");
    if (!endAt.isAfter(startAt)) {
      throw new IllegalArgumentException("end must be after start");
    }
    Appointment appointment = new Appointment();
    appointment.petId = petId;
    appointment.doctorId = doctorId;
    appointment.startAt = startAt;
    appointment.endAt = endAt;
    appointment.status = AppointmentStatus.SCHEDULED;
    return appointment;
  }

  public boolean canCancel() {
    return status == AppointmentStatus.SCHEDULED || status == AppointmentStatus.CHECKED_IN;
  }

  public boolean canReschedule() {
    return status == AppointmentStatus.SCHEDULED;
  }

  public boolean canCheckIn() {
    return status == AppointmentStatus.SCHEDULED;
  }

  public boolean canCall() {
    return status == AppointmentStatus.CHECKED_IN;
  }

  public void cancel() {
    require(canCancel());
    this.status = AppointmentStatus.CANCELLED;
  }

  public void reschedule(Instant newStartAt, Instant newEndAt) {
    require(canReschedule());
    if (!newEndAt.isAfter(newStartAt)) {
      throw new IllegalArgumentException("end must be after start");
    }
    this.startAt = newStartAt;
    this.endAt = newEndAt;
  }

  public void checkIn() {
    checkIn(Instant.now());
  }

  public void checkIn(Instant now) {
    require(canCheckIn());
    this.status = AppointmentStatus.CHECKED_IN;
    this.checkedInAt = Objects.requireNonNull(now);
  }

  public void call() {
    call(Instant.now());
  }

  public void call(Instant now) {
    require(canCall());
    this.status = AppointmentStatus.CALLED;
    this.calledAt = Objects.requireNonNull(now);
  }

  private void require(boolean condition) {
    if (!condition) {
      throw new IllegalStateException("illegal appointment state transition from " + status);
    }
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

  public UUID getId() {
    return id;
  }

  public UUID getPetId() {
    return petId;
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

  public AppointmentStatus getStatus() {
    return status;
  }

  public Instant getCheckedInAt() {
    return checkedInAt;
  }

  public Instant getCalledAt() {
    return calledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
