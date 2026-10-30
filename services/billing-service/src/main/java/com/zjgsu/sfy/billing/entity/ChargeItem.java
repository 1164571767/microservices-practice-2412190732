package com.zjgsu.sfy.billing.entity;

import com.zjgsu.sfy.billing.common.ChargeItemStatus;
import com.zjgsu.sfy.billing.util.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 收费项目目录；金额精度固定 19,2，停用后不能用于生成新账单。 */
@Entity
@Table(name = "charge_item")
public class ChargeItem {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ChargeItemStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected ChargeItem() {}

  public static ChargeItem create(String name, BigDecimal amount) {
    ChargeItem item = new ChargeItem();
    item.apply(name, amount, true);
    return item;
  }

  public void update(String name, BigDecimal amount, boolean active) {
    apply(name, amount, active);
  }

  private void apply(String name, BigDecimal amount, boolean active) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    this.name = name.trim();
    this.amount = Money.normalize(amount);
    this.status = active ? ChargeItemStatus.ACTIVE : ChargeItemStatus.INACTIVE;
  }

  public boolean isActive() {
    return status == ChargeItemStatus.ACTIVE;
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

  public BigDecimal getAmount() {
    return amount;
  }

  public ChargeItemStatus getStatus() {
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
    return other instanceof ChargeItem item && Objects.equals(id, item.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
