package com.zjgsu.sfy.pharmacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 一次处方发药记录；一张处方至多一条（DB 唯一约束兜底），明细见 {@link DispenseItem}。 */
@Entity
@Table(name = "dispense_record")
public class DispenseRecord {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "prescription_id", nullable = false, unique = true)
  private UUID prescriptionId;

  @Column(name = "medical_record_id", nullable = false)
  private UUID medicalRecordId;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "dispensed_by", nullable = false)
  private UUID dispensedBy;

  @Column(name = "dispensed_at", nullable = false)
  private Instant dispensedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DispenseRecord() {}

  public static DispenseRecord create(
      UUID prescriptionId,
      UUID medicalRecordId,
      UUID petId,
      UUID doctorId,
      UUID dispensedBy,
      Instant dispensedAt) {
    Objects.requireNonNull(prescriptionId, "prescriptionId must not be null");
    Objects.requireNonNull(medicalRecordId, "medicalRecordId must not be null");
    Objects.requireNonNull(petId, "petId must not be null");
    Objects.requireNonNull(doctorId, "doctorId must not be null");
    Objects.requireNonNull(dispensedBy, "dispensedBy must not be null");
    Objects.requireNonNull(dispensedAt, "dispensedAt must not be null");
    DispenseRecord record = new DispenseRecord();
    record.prescriptionId = prescriptionId;
    record.medicalRecordId = medicalRecordId;
    record.petId = petId;
    record.doctorId = doctorId;
    record.dispensedBy = dispensedBy;
    record.dispensedAt = dispensedAt;
    return record;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPrescriptionId() {
    return prescriptionId;
  }

  public UUID getMedicalRecordId() {
    return medicalRecordId;
  }

  public UUID getPetId() {
    return petId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public UUID getDispensedBy() {
    return dispensedBy;
  }

  public Instant getDispensedAt() {
    return dispensedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
