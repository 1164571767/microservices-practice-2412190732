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

/** 处方；一份病历至多一张（DB 唯一约束兜底）。药品明细见 {@link PrescriptionItem}。 */
@Entity
@Table(name = "prescription")
public class Prescription {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "medical_record_id", nullable = false)
  private UUID medicalRecordId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Prescription() {}

  public static Prescription issue(UUID medicalRecordId, UUID doctorId) {
    Objects.requireNonNull(medicalRecordId, "medicalRecordId must not be null");
    Objects.requireNonNull(doctorId, "doctorId must not be null");
    Prescription prescription = new Prescription();
    prescription.medicalRecordId = medicalRecordId;
    prescription.doctorId = doctorId;
    prescription.issuedAt = Instant.now();
    return prescription;
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

  public UUID getMedicalRecordId() {
    return medicalRecordId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
