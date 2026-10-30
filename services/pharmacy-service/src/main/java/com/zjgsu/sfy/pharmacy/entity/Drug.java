package com.zjgsu.sfy.pharmacy.entity;

import com.zjgsu.sfy.pharmacy.common.DrugStatus;
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

/** 药品目录条目；名称 + 规格唯一，库存策略（低库存阈值）随目录维护。 */
@Entity
@Table(name = "drug")
public class Drug {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 100)
  private String specification;

  @Column(nullable = false, length = 20)
  private String unit;

  @Column(name = "low_stock_threshold", nullable = false)
  private int lowStockThreshold;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DrugStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Drug() {}

  public static Drug create(String name, String specification, String unit, int lowStockThreshold) {
    Drug drug = new Drug();
    drug.status = DrugStatus.ACTIVE;
    drug.updateDetails(name, specification, unit, lowStockThreshold, true);
    return drug;
  }

  public void updateDetails(
      String name, String specification, String unit, int lowStockThreshold, boolean active) {
    if (lowStockThreshold < 0) {
      throw new IllegalArgumentException("lowStockThreshold must not be negative");
    }
    this.name = requireText(name, "name");
    this.specification = requireText(specification, "specification");
    this.unit = requireText(unit, "unit");
    this.lowStockThreshold = lowStockThreshold;
    this.status = active ? DrugStatus.ACTIVE : DrugStatus.INACTIVE;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  public boolean isActive() {
    return status == DrugStatus.ACTIVE;
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

  public String getName() {
    return name;
  }

  public String getSpecification() {
    return specification;
  }

  public String getUnit() {
    return unit;
  }

  public int getLowStockThreshold() {
    return lowStockThreshold;
  }

  public DrugStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Drug drug && Objects.equals(id, drug.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
