package com.zjgsu.sfy.medical.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 处方明细行；药品信息内嵌（名称/规格/剂量/频次/数量/天数），处方自包含。 M5 pharmacy（药品目录/库存）落地后经 MedicalApi 对接，此处不提前引入其领域。 */
@Entity
@Table(name = "prescription_item")
public class PrescriptionItem {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "prescription_id", nullable = false)
  private UUID prescriptionId;

  @Column(name = "drug_name", nullable = false, length = 100)
  private String drugName;

  @Column(length = 100)
  private String specification;

  @Column(nullable = false, length = 100)
  private String dosage;

  @Column(nullable = false, length = 100)
  private String frequency;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private int days;

  @Column(length = 255)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PrescriptionItem() {}

  public static PrescriptionItem create(
      UUID prescriptionId,
      String drugName,
      String specification,
      String dosage,
      String frequency,
      int quantity,
      int days,
      String note) {
    Objects.requireNonNull(prescriptionId, "prescriptionId must not be null");
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (days <= 0) {
      throw new IllegalArgumentException("days must be positive");
    }
    PrescriptionItem item = new PrescriptionItem();
    item.prescriptionId = prescriptionId;
    item.drugName = requireText(drugName, "drugName");
    item.specification = normalizeOptionalText(specification);
    item.dosage = requireText(dosage, "dosage");
    item.frequency = requireText(frequency, "frequency");
    item.quantity = quantity;
    item.days = days;
    item.note = normalizeOptionalText(note);
    return item;
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
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPrescriptionId() {
    return prescriptionId;
  }

  public String getDrugName() {
    return drugName;
  }

  public String getSpecification() {
    return specification;
  }

  public String getDosage() {
    return dosage;
  }

  public String getFrequency() {
    return frequency;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getDays() {
    return days;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
