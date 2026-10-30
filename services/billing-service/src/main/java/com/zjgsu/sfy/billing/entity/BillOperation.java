package com.zjgsu.sfy.billing.entity;

import com.zjgsu.sfy.billing.common.BillAction;
import com.zjgsu.sfy.billing.common.BillStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 账单操作审计：记录动作、状态迁移、原因与操作人，禁止无痕修改账单状态。 */
@Entity
@Table(name = "bill_operation")
public class BillOperation {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "bill_id", nullable = false)
  private UUID billId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BillAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 20)
  private BillStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 20)
  private BillStatus toStatus;

  @Column(length = 255)
  private String reason;

  @Column(name = "operator_id", nullable = false)
  private UUID operatorId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected BillOperation() {}

  public static BillOperation created(UUID billId, UUID operatorId) {
    return transition(billId, BillAction.CREATED, null, BillStatus.UNPAID, null, operatorId);
  }

  public static BillOperation transition(
      UUID billId,
      BillAction action,
      BillStatus fromStatus,
      BillStatus toStatus,
      String reason,
      UUID operatorId) {
    Objects.requireNonNull(billId, "billId must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(toStatus, "toStatus must not be null");
    Objects.requireNonNull(operatorId, "operatorId must not be null");
    BillOperation operation = new BillOperation();
    operation.billId = billId;
    operation.action = action;
    operation.fromStatus = fromStatus;
    operation.toStatus = toStatus;
    operation.reason = reason == null || reason.isBlank() ? null : reason.trim();
    operation.operatorId = operatorId;
    return operation;
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

  public BillAction getAction() {
    return action;
  }

  public BillStatus getFromStatus() {
    return fromStatus;
  }

  public BillStatus getToStatus() {
    return toStatus;
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
