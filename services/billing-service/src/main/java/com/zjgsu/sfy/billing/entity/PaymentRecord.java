package com.zjgsu.sfy.billing.entity;

import com.zjgsu.sfy.billing.common.PaymentMethod;
import com.zjgsu.sfy.billing.common.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 一次账单支付记录：一张账单至多一条（DB 唯一约束兜底），退款后状态转为 REFUNDED。 */
@Entity
@Table(name = "payment_record")
public class PaymentRecord {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "bill_id", nullable = false, unique = true)
  private UUID billId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(name = "paid_by", nullable = false)
  private UUID paidBy;

  @Column(name = "paid_at", nullable = false)
  private Instant paidAt;

  @Column(name = "refunded_by")
  private UUID refundedBy;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  @Column(name = "refund_reason", length = 255)
  private String refundReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PaymentRecord() {}

  public static PaymentRecord record(
      UUID billId,
      BigDecimal amount,
      String currency,
      PaymentMethod method,
      UUID paidBy,
      Instant paidAt) {
    Objects.requireNonNull(billId, "billId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(paidBy, "paidBy must not be null");
    Objects.requireNonNull(paidAt, "paidAt must not be null");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must not be negative");
    }
    PaymentRecord payment = new PaymentRecord();
    payment.billId = billId;
    payment.amount = amount;
    payment.currency = currency;
    payment.method = method;
    payment.status = PaymentStatus.SUCCEEDED;
    payment.paidBy = paidBy;
    payment.paidAt = paidAt;
    return payment;
  }

  public void refund(UUID operatorId, Instant refundedAt, String reason) {
    Objects.requireNonNull(operatorId, "operatorId must not be null");
    Objects.requireNonNull(refundedAt, "refundedAt must not be null");
    if (status != PaymentStatus.SUCCEEDED) {
      throw new IllegalStateException("payment is not refundable: " + status);
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("refund reason must not be blank");
    }
    this.status = PaymentStatus.REFUNDED;
    this.refundedBy = operatorId;
    this.refundedAt = refundedAt;
    this.refundReason = reason.trim();
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

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public UUID getPaidBy() {
    return paidBy;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public UUID getRefundedBy() {
    return refundedBy;
  }

  public Instant getRefundedAt() {
    return refundedAt;
  }

  public String getRefundReason() {
    return refundReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
