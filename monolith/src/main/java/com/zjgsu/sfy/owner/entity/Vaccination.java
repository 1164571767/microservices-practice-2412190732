package com.zjgsu.sfy.owner.entity;

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

/** 疫苗记录；归属某只宠物，仅追加、不可改。 */
@Entity
@Table(name = "vaccination")
public class Vaccination {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "vaccine_name", nullable = false, length = 100)
  private String vaccineName;

  @Column(name = "administered_on", nullable = false)
  private LocalDate administeredOn;

  @Column(name = "next_due_on")
  private LocalDate nextDueOn;

  @Column(length = 255)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "recorded_by")
  private UUID recordedBy;

  @Column(name = "recorded_by_name", length = 100)
  private String recordedByName;

  @Column(name = "supersedes_id")
  private UUID supersedesId;

  @Column(name = "correction_reason", length = 255)
  private String correctionReason;

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public String getRecordedByName() {
    return recordedByName;
  }

  public UUID getSupersedesId() {
    return supersedesId;
  }

  public String getCorrectionReason() {
    return correctionReason;
  }

  public void recordActor(UUID actor, String name) {
    recordedBy = Objects.requireNonNull(actor);
    recordedByName = name;
  }

  public void corrects(UUID recordId, String reason) {
    if (reason == null || reason.isBlank())
      throw new IllegalArgumentException("correction reason required");
    supersedesId = Objects.requireNonNull(recordId);
    correctionReason = reason.trim();
  }

  protected Vaccination() {}

  public static Vaccination create(
      UUID petId, String vaccineName, LocalDate administeredOn, LocalDate nextDueOn, String note) {
    if (vaccineName == null || vaccineName.isBlank()) {
      throw new IllegalArgumentException("vaccine name must not be blank");
    }
    if (administeredOn == null) {
      throw new IllegalArgumentException("administered date must not be null");
    }
    if (administeredOn.isAfter(LocalDate.now())
        || (nextDueOn != null && nextDueOn.isBefore(administeredOn))) {
      throw new com.zjgsu.sfy.common.error.BusinessException(
          com.zjgsu.sfy.common.error.ErrorCode.VALIDATION_FAILED, "接种日期不能为未来，下次日期不能早于接种日期");
    }
    Vaccination vaccination = new Vaccination();
    vaccination.petId = Objects.requireNonNull(petId, "petId must not be null");
    vaccination.vaccineName = vaccineName.trim();
    vaccination.administeredOn = administeredOn;
    vaccination.nextDueOn = nextDueOn;
    vaccination.note = note == null || note.isBlank() ? null : note.trim();
    return vaccination;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPetId() {
    return petId;
  }

  public String getVaccineName() {
    return vaccineName;
  }

  public LocalDate getAdministeredOn() {
    return administeredOn;
  }

  public LocalDate getNextDueOn() {
    return nextDueOn;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
