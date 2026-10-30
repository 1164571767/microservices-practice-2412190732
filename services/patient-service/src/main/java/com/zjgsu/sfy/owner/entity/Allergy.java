package com.zjgsu.sfy.owner.entity;

import com.zjgsu.sfy.owner.common.AllergySeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 过敏史记录；归属某只宠物，仅追加、不可改。 */
@Entity
@Table(name = "allergy")
public class Allergy {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(nullable = false, length = 100)
  private String allergen;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AllergySeverity severity;

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

  protected Allergy() {}

  public static Allergy create(UUID petId, String allergen, AllergySeverity severity, String note) {
    if (allergen == null || allergen.isBlank()) {
      throw new IllegalArgumentException("allergen must not be blank");
    }
    if (severity == null) {
      throw new IllegalArgumentException("severity must not be null");
    }
    Allergy allergy = new Allergy();
    allergy.petId = Objects.requireNonNull(petId, "petId must not be null");
    allergy.allergen = allergen.trim();
    allergy.severity = severity;
    allergy.note = note == null || note.isBlank() ? null : note.trim();
    return allergy;
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

  public String getAllergen() {
    return allergen;
  }

  public AllergySeverity getSeverity() {
    return severity;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
