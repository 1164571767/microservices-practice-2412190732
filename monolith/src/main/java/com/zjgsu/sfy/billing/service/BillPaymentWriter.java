package com.zjgsu.sfy.billing.service;

import com.zjgsu.sfy.billing.BillPaid;
import com.zjgsu.sfy.billing.BillRefunded;
import com.zjgsu.sfy.billing.common.BillAction;
import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.common.PaymentMethod;
import com.zjgsu.sfy.billing.entity.Bill;
import com.zjgsu.sfy.billing.entity.BillOperation;
import com.zjgsu.sfy.billing.entity.PaymentRecord;
import com.zjgsu.sfy.billing.repository.BillOperationRepository;
import com.zjgsu.sfy.billing.repository.BillRepository;
import com.zjgsu.sfy.billing.repository.PaymentRecordRepository;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecord;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecordRepository;
import com.zjgsu.sfy.identity.CurrentUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账单支付 / 退款 / 作废写侧事务。
 *
 * <p>支付前以 {@code SELECT ... FOR UPDATE} 锁定账单行，串行化同一账单的并发状态迁移；支付记录 {@code bill_id}
 * 唯一键与幂等键唯一键是数据库最终守卫。
 */
@Service
public class BillPaymentWriter {

  private final BillRepository billRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final BillOperationRepository billOperationRepository;
  private final IdempotencyRecordRepository idempotencyRepository;
  private final ApplicationEventPublisher events;

  public BillPaymentWriter(
      BillRepository billRepository,
      PaymentRecordRepository paymentRecordRepository,
      BillOperationRepository billOperationRepository,
      IdempotencyRecordRepository idempotencyRepository,
      ApplicationEventPublisher events) {
    this.billRepository = billRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.billOperationRepository = billOperationRepository;
    this.idempotencyRepository = idempotencyRepository;
    this.events = events;
  }

  @Transactional
  public Bill pay(CurrentUser requester, UUID billId, String requestHash, String idempotencyKey) {
    Bill bill =
        billRepository
            .findByIdForUpdate(billId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "账单不存在"));

    if (idempotencyKey != null) {
      Optional<IdempotencyRecord> existing =
          idempotencyRepository.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
        IdempotencyRecord record = existing.get();
        if (!record.getRequestHash().equals(requestHash)) {
          throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return bill;
      }
    }
    if (bill.getStatus() == BillStatus.PAID) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
    }
    if (!bill.canPay()) {
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
    }

    Instant now = Instant.now();
    bill.markPaid();
    billRepository.saveAndFlush(bill);
    PaymentRecord payment =
        paymentRecordRepository.saveAndFlush(
            PaymentRecord.record(
                bill.getId(),
                bill.getTotalAmount(),
                bill.getCurrency(),
                PaymentMethod.MOCK,
                requester.userId(),
                now));
    billOperationRepository.saveAndFlush(
        BillOperation.transition(
            bill.getId(),
            BillAction.PAID,
            BillStatus.UNPAID,
            BillStatus.PAID,
            null,
            requester.userId()));
    if (idempotencyKey != null) {
      idempotencyRepository.saveAndFlush(
          new IdempotencyRecord(idempotencyKey, requestHash, bill.getId()));
    }
    events.publishEvent(
        new BillPaid(
            bill.getId(),
            payment.getId(),
            bill.getPetId(),
            bill.getMedicalRecordId(),
            bill.getPrescriptionId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaidBy(),
            payment.getPaidAt()));
    return bill;
  }

  @Transactional
  public Bill refund(CurrentUser requester, UUID billId, String reason) {
    requireBillingStaff(requester);
    String auditReason = requireReason(reason);
    Bill bill = lockBill(billId);
    if (!bill.canRefund()) {
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
    }
    PaymentRecord payment =
        paymentRecordRepository
            .findByBillId(billId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_INVALID_STATE));
    Instant now = Instant.now();
    bill.markRefunded();
    billRepository.saveAndFlush(bill);
    payment.refund(requester.userId(), now, auditReason);
    paymentRecordRepository.saveAndFlush(payment);
    billOperationRepository.saveAndFlush(
        BillOperation.transition(
            billId,
            BillAction.REFUNDED,
            BillStatus.PAID,
            BillStatus.REFUNDED,
            auditReason,
            requester.userId()));
    events.publishEvent(
        new BillRefunded(
            billId,
            payment.getId(),
            bill.getPetId(),
            payment.getAmount(),
            payment.getCurrency(),
            requester.userId(),
            payment.getRefundedAt()));
    return bill;
  }

  @Transactional
  public Bill voidBill(CurrentUser requester, UUID billId, String reason) {
    requireBillingStaff(requester);
    String auditReason = requireReason(reason);
    Bill bill = lockBill(billId);
    if (!bill.canVoid()) {
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
    }
    bill.markVoided();
    billRepository.saveAndFlush(bill);
    billOperationRepository.saveAndFlush(
        BillOperation.transition(
            billId,
            BillAction.VOIDED,
            BillStatus.UNPAID,
            BillStatus.VOID,
            auditReason,
            requester.userId()));
    return bill;
  }

  private static String requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "退款 / 作废原因不能为空");
    }
    return reason.trim();
  }

  private Bill lockBill(UUID billId) {
    return billRepository
        .findByIdForUpdate(billId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "账单不存在"));
  }

  private static void requireBillingStaff(CurrentUser requester) {
    if (!requester.isBillingStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
