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
 * 药品消耗事实（一次发药中的一种药品一行）。
 *
 * <p>由 {@code pharmacy.PrescriptionDispensed} 事件携带的发药明细投影而来；{@code (dispense_id, drug_id)}
 * 唯一键保证重复投递只落一行。 药品名称 / 规格 / 单位取发药时快照，与 {@code dispense_item} 口径一致。
 */
@Entity
@Table(name = "reporting_drug_consumption_fact")
public class DrugConsumptionFact {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "dispense_id", nullable = false)
  private UUID dispenseId;

  @Column(name = "drug_id", nullable = false)
  private UUID drugId;

  @Column(name = "drug_name", nullable = false, length = 100)
  private String drugName;

  @Column(nullable = false, length = 100)
  private String specification;

  @Column(nullable = false, length = 20)
  private String unit;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "dispensed_at", nullable = false)
  private Instant dispensedAt;

  @Column(name = "stat_date", nullable = false)
  private LocalDate statDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DrugConsumptionFact() {}

  public static DrugConsumptionFact create(
      UUID dispenseId,
      UUID drugId,
      String drugName,
      String specification,
      String unit,
      int quantity,
      Instant dispensedAt,
      LocalDate statDate) {
    Objects.requireNonNull(dispenseId, "dispenseId must not be null");
    Objects.requireNonNull(drugId, "drugId must not be null");
    Objects.requireNonNull(dispensedAt, "dispensedAt must not be null");
    Objects.requireNonNull(statDate, "statDate must not be null");
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    DrugConsumptionFact fact = new DrugConsumptionFact();
    fact.dispenseId = dispenseId;
    fact.drugId = drugId;
    fact.drugName = requireText(drugName, "drugName");
    fact.specification = requireText(specification, "specification");
    fact.unit = requireText(unit, "unit");
    fact.quantity = quantity;
    fact.dispensedAt = dispensedAt;
    fact.statDate = statDate;
    return fact;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
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

  public UUID getDispenseId() {
    return dispenseId;
  }

  public UUID getDrugId() {
    return drugId;
  }

  public String getDrugName() {
    return drugName;
  }

  public String getSpecification() {
    return specification;
  }

  public String getUnit() {
    return unit;
  }

  public int getQuantity() {
    return quantity;
  }

  public Instant getDispensedAt() {
    return dispensedAt;
  }

  public LocalDate getStatDate() {
    return statDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
