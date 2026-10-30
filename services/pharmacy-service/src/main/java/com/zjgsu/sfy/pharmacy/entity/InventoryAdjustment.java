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

/** 库存调整流水；记录调整前后数量、原因与操作人，保证库存变动可追溯。 */
@Entity
@Table(name = "inventory_adjustment")
public class InventoryAdjustment {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "drug_id", nullable = false)
  private UUID drugId;

  @Column(nullable = false)
  private int delta;

  @Column(name = "quantity_before", nullable = false)
  private int quantityBefore;

  @Column(name = "quantity_after", nullable = false)
  private int quantityAfter;

  @Column(nullable = false, length = 255)
  private String reason;

  @Column(name = "operator_id", nullable = false)
  private UUID operatorId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InventoryAdjustment() {}

  public static InventoryAdjustment record(
      UUID drugId,
      int delta,
      int quantityBefore,
      int quantityAfter,
      String reason,
      UUID operatorId) {
    Objects.requireNonNull(drugId, "drugId must not be null");
    Objects.requireNonNull(operatorId, "operatorId must not be null");
    if (delta == 0) {
      throw new IllegalArgumentException("delta must not be zero");
    }
    if (quantityBefore < 0 || quantityAfter < 0) {
      throw new IllegalArgumentException("quantities must not be negative");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    InventoryAdjustment adjustment = new InventoryAdjustment();
    adjustment.drugId = drugId;
    adjustment.delta = delta;
    adjustment.quantityBefore = quantityBefore;
    adjustment.quantityAfter = quantityAfter;
    adjustment.reason = reason.trim();
    adjustment.operatorId = operatorId;
    return adjustment;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getDrugId() {
    return drugId;
  }

  public int getDelta() {
    return delta;
  }

  public int getQuantityBefore() {
    return quantityBefore;
  }

  public int getQuantityAfter() {
    return quantityAfter;
  }

  public String getReason() {
    return reason;
  }

  public UUID getOperatorId() {
    return operatorId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
