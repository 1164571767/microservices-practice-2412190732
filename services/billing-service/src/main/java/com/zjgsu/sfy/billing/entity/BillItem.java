package com.zjgsu.sfy.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 账单明细：保存收费项目名称 / 单价快照与精确行金额，不受目录后续改价影响。 */
@Entity
@Table(name = "bill_item")
public class BillItem {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "bill_id", nullable = false)
  private UUID billId;

  @Column(name = "charge_item_id", nullable = false)
  private UUID chargeItemId;

  @Column(name = "item_name", nullable = false, length = 100)
  private String itemName;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected BillItem() {}

  public static BillItem create(UUID billId, BillLine line) {
    Objects.requireNonNull(billId, "billId must not be null");
    Objects.requireNonNull(line, "line must not be null");
    BillItem item = new BillItem();
    item.billId = billId;
    item.chargeItemId = line.chargeItemId();
    item.itemName = line.itemName();
    item.unitPrice = line.unitPrice();
    item.quantity = line.quantity();
    item.amount = line.amount();
    return item;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getBillId() {
    return billId;
  }

  public UUID getChargeItemId() {
    return chargeItemId;
  }

  public String getItemName() {
    return itemName;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
