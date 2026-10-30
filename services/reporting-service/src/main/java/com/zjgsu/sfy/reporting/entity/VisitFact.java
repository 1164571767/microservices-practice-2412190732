package com.zjgsu.sfy.reporting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 门诊量事实（一次就诊一行）。
 *
 * <p>由 {@code medical.MedicalRecordCreated} 事件投影而来；{@code record_id} 唯一键保证同一病历重复投递只落一行。 {@code
 * stat_date} 是 {@code visited_at} 在配置时区的本地日期，作为统计口径。
 */
@Entity
@Table(name = "reporting_visit_fact")
public class VisitFact {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "record_id", nullable = false, unique = true)
  private UUID recordId;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "visited_at", nullable = false)
  private Instant visitedAt;

  @Column(name = "stat_date", nullable = false)
  private LocalDate statDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected VisitFact() {}

  public static VisitFact create(
      UUID recordId, UUID petId, UUID doctorId, Instant visitedAt, LocalDate statDate) {
    VisitFact fact = new VisitFact();
    fact.recordId = Objects.requireNonNull(recordId, "recordId must not be null");
    fact.petId = Objects.requireNonNull(petId, "petId must not be null");
    fact.doctorId = Objects.requireNonNull(doctorId, "doctorId must not be null");
    fact.visitedAt = Objects.requireNonNull(visitedAt, "visitedAt must not be null");
    fact.statDate = Objects.requireNonNull(statDate, "statDate must not be null");
    return fact;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecordId() {
    return recordId;
  }

  public UUID getPetId() {
    return petId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public Instant getVisitedAt() {
    return visitedAt;
  }

  public LocalDate getStatDate() {
    return statDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
