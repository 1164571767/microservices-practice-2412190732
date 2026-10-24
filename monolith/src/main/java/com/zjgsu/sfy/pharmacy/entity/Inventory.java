package com.zjgsu.sfy.pharmacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单个药品的现有库存；与 {@link Drug} 一对一。
 *
 * <p>数量不变式为 {@code quantity >= 0}，由数据库 CHECK、行锁调整与条件扣减三处共同保证。 {@code lowStockNotifiedAt}
 * 记录本轮低库存是否已发布事件，用于避免每次扣减都重复告警。
 */
@Entity
@Table(name = "inventory")
public class Inventory {

  @Id
  @Column(name = "drug_id", nullable = false)
  private UUID drugId;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "low_stock_notified_at")
  private Instant lowStockNotifiedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "drug_id", nullable = false, insertable = false, updatable = false)
  private Drug drug;

  protected Inventory() {}

  public static Inventory initialize(UUID drugId, int quantity) {
    Objects.requireNonNull(drugId, "drugId must not be null");
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity must not be negative");
    }
    Inventory inventory = new Inventory();
    inventory.drugId = drugId;
    inventory.quantity = quantity;
    return inventory;
  }

  /** 按增量调整；调用方已保证结果不为负。 */
  public void adjust(int delta) {
    if (delta == 0) {
      throw new IllegalArgumentException("delta must not be zero");
    }
    long target = (long) quantity + delta;
    if (target < 0 || target > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("quantity out of range");
    }
    this.quantity = (int) target;
  }

  /**
   * 根据最新库存与阈值刷新低库存告警状态。
   *
   * @return 本轮首次进入低库存时返回 true；已经在低库存、或已回到阈值以上时返回 false。
   */
  public boolean refreshLowStockAlert(int lowStockThreshold) {
    if (lowStockThreshold < 0) {
      throw new IllegalArgumentException("lowStockThreshold must not be negative");
    }
    boolean low = quantity < lowStockThreshold;
    if (low && lowStockNotifiedAt == null) {
      lowStockNotifiedAt = Instant.now();
      return true;
    }
    if (!low) {
      lowStockNotifiedAt = null;
    }
    return false;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    this.updatedAt = Instant.now();
  }

  public UUID getDrugId() {
    return drugId;
  }

  public int getQuantity() {
    return quantity;
  }

  public Instant getLowStockNotifiedAt() {
    return lowStockNotifiedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Drug getDrug() {
    return drug;
  }
}
