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

/** 发药明细：按药品聚合，保存发药时的名称 / 规格 / 单位快照与扣减后库存。 */
@Entity
@Table(name = "dispense_item")
public class DispenseItem {

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

  @Column(name = "stock_after", nullable = false)
  private int stockAfter;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DispenseItem() {}

  public static DispenseItem create(
      UUID dispenseId,
      UUID drugId,
      String drugName,
      String specification,
      String unit,
      int quantity,
      int stockAfter) {
    Objects.requireNonNull(dispenseId, "dispenseId must not be null");
    Objects.requireNonNull(drugId, "drugId must not be null");
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (stockAfter < 0) {
      throw new IllegalArgumentException("stockAfter must not be negative");
    }
    DispenseItem item = new DispenseItem();
    item.dispenseId = dispenseId;
    item.drugId = drugId;
    item.drugName = requireText(drugName, "drugName");
    item.specification = requireText(specification, "specification");
    item.unit = requireText(unit, "unit");
    item.quantity = quantity;
    item.stockAfter = stockAfter;
    return item;
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

  public int getStockAfter() {
    return stockAfter;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
