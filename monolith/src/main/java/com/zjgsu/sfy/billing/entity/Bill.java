package com.zjgsu.sfy.billing.entity;

import com.zjgsu.sfy.billing.common.BillStatus;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 账单聚合根；状态只能经领域方法流转，非法流转抛 {@link IllegalStateException}。 */
@Entity
@Table(name = "bill")
public class Bill {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "pet_id", nullable = false)
  private UUID petId;

  @Column(name = "medical_record_id", nullable = false)
  private UUID medicalRecordId;

  @Column(name = "prescription_id")
  private UUID prescriptionId;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BillStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Bill() {}

  public static Bill create(
      UUID petId, UUID medicalRecordId, UUID prescriptionId, UUID createdBy, List<BillLine> lines) {
    Objects.requireNonNull(petId, "petId must not be null");
    Objects.requireNonNull(medicalRecordId, "medicalRecordId must not be null");
    Objects.requireNonNull(createdBy, "createdBy must not be null");
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("bill must contain at least one line");
    }
    Bill bill = new Bill();
    bill.petId = petId;
    bill.medicalRecordId = medicalRecordId;
    bill.prescriptionId = prescriptionId;
    bill.totalAmount = Money.sum(lines.stream().map(BillLine::amount).toList());
    bill.currency = Money.CURRENCY;
    bill.status = BillStatus.UNPAID;
    bill.createdBy = createdBy;
    return bill;
  }

  public boolean canPay() {
    return status == BillStatus.UNPAID;
  }

  public boolean canRefund() {
    return status == BillStatus.PAID;
  }

  public boolean canVoid() {
    return status == BillStatus.UNPAID;
  }

  public void markPaid() {
    require(canPay());
    this.status = BillStatus.PAID;
  }

  public void markRefunded() {
    require(canRefund());
    this.status = BillStatus.REFUNDED;
  }

  public void markVoided() {
    require(canVoid());
    this.status = BillStatus.VOID;
  }

  private void require(boolean condition) {
    if (!condition) {
      throw new IllegalStateException("illegal bill state transition from " + status);
    }
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

  public UUID getPetId() {
    return petId;
  }

  public UUID getMedicalRecordId() {
    return medicalRecordId;
  }

  public UUID getPrescriptionId() {
    return prescriptionId;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public BillStatus getStatus() {
    return status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
