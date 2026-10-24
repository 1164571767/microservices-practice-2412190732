package com.zjgsu.sfy.reporting.entity;

import com.zjgsu.sfy.reporting.common.RevenueKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 收入事实（一次支付或一次退款一行）。
 *
 * <p>由 {@code billing.BillPaid} / {@code billing.BillRefunded} 事件投影而来；{@code (bill_id, kind)}
 * 唯一键保证同一账单的 支付 / 退款各只落一行。收入与退款分开记账，净收入 = PAID 合计 − REFUNDED 合计，口径与账单状态机一致。
 */
@Entity
@Table(name = "reporting_revenue_fact")
public class RevenueFact {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "bill_id", nullable = false)
  private UUID billId;

  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RevenueKind kind;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "stat_date", nullable = false)
  private LocalDate statDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RevenueFact() {}

  public static RevenueFact create(
      UUID billId,
      UUID paymentId,
      RevenueKind kind,
      BigDecimal amount,
      String currency,
      UUID petId,
      Instant occurredAt,
      LocalDate statDate) {
    Objects.requireNonNull(billId, "billId must not be null");
    Objects.requireNonNull(paymentId, "paymentId must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(petId, "petId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(statDate, "statDate must not be null");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must not be negative");
    }
    if (amount.scale() > 2) {
      throw new IllegalArgumentException("amount must have at most 2 decimal places");
    }
    if (!"CNY".equals(currency)) {
      throw new IllegalArgumentException("currency must be CNY");
    }
    RevenueFact fact = new RevenueFact();
    fact.billId = billId;
    fact.paymentId = paymentId;
    fact.kind = kind;
    fact.amount = amount;
    fact.currency = currency;
    fact.petId = petId;
    fact.occurredAt = occurredAt;
    fact.statDate = statDate;
    return fact;
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

  public UUID getPaymentId() {
    return paymentId;
  }

  public RevenueKind getKind() {
    return kind;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public UUID getPetId() {
    return petId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public LocalDate getStatDate() {
    return statDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
