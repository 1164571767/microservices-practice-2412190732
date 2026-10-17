package com.zjgsu.sfy.medical.entity;

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

/** 门诊病历聚合根；一次预约至多一份（DB 唯一约束兜底）。症状/诊断/医嘱为独立字段。 接诊医生与宠物的归属由应用层经预约端口校验后写入，不做跨模块外键。 */
@Entity
@Table(name = "medical_record")
public class MedicalRecord {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(nullable = false, length = 2000)
  private String symptoms;

  @Column(nullable = false, length = 2000)
  private String diagnosis;

  @Column(length = 2000)
  private String advice;

  @Column(name = "visited_at", nullable = false)
  private Instant visitedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected MedicalRecord() {}

  public static MedicalRecord create(
      UUID appointmentId,
      UUID petId,
      UUID doctorId,
      String symptoms,
      String diagnosis,
      String advice,
      Instant visitedAt) {
    Objects.requireNonNull(appointmentId, "appointmentId must not be null");
    Objects.requireNonNull(petId, "petId must not be null");
    Objects.requireNonNull(doctorId, "doctorId must not be null");
    Objects.requireNonNull(visitedAt, "visitedAt must not be null");
    MedicalRecord record = new MedicalRecord();
    record.appointmentId = appointmentId;
    record.petId = petId;
    record.doctorId = doctorId;
    record.symptoms = requireText(symptoms, "symptoms");
    record.diagnosis = requireText(diagnosis, "diagnosis");
    record.advice = normalizeOptionalText(advice);
    record.visitedAt = visitedAt;
    return record;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String normalizeOptionalText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
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

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public UUID getPetId() {
    return petId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public String getSymptoms() {
    return symptoms;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public String getAdvice() {
    return advice;
  }

  public Instant getVisitedAt() {
    return visitedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
