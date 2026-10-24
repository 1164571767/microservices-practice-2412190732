package com.zjgsu.sfy.billing.service;

import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.entity.Bill;
import com.zjgsu.sfy.billing.repository.PaymentRecordRepository;
import com.zjgsu.sfy.billing.service.command.CreateBillCommand;
import com.zjgsu.sfy.billing.service.result.BillDetail;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.idempotency.IdempotencyKeys;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecordRepository;
import com.zjgsu.sfy.common.idempotency.RequestHashes;
import com.zjgsu.sfy.identity.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 收费与账单用例编排。
 *
 * <p>创建账单与支付本身不开启事务：写侧在 {@link BillWriter} / {@link BillPaymentWriter} 的独立事务中完成；
 * 并发重复请求触发幂等键或支付记录唯一键冲突时，事务已回滚，这里在事务外回读已提交结果并回放。
 */
@Service
public class BillService {

  private final BillWriter billWriter;
  private final BillPaymentWriter billPaymentWriter;
  private final BillQueryService queryService;
  private final IdempotencyRecordRepository idempotencyRepository;
  private final PaymentRecordRepository paymentRecordRepository;

  public BillService(
      BillWriter billWriter,
      BillPaymentWriter billPaymentWriter,
      BillQueryService queryService,
      IdempotencyRecordRepository idempotencyRepository,
      PaymentRecordRepository paymentRecordRepository) {
    this.billWriter = billWriter;
    this.billPaymentWriter = billPaymentWriter;
    this.queryService = queryService;
    this.idempotencyRepository = idempotencyRepository;
    this.paymentRecordRepository = paymentRecordRepository;
  }

  public BillDetail create(
      CurrentUser requester, CreateBillCommand command, String idempotencyKey) {
    requireBillingStaff(requester);
    String key = normalizeKey(idempotencyKey);
    String requestHash = createHash(command);
    if (key != null) {
      Optional<BillDetail> replay = replay(key, requestHash, requester);
      if (replay.isPresent()) {
        return replay.get();
      }
    }

    try {
      Bill bill = billWriter.create(requester, command, requestHash, key);
      return queryService.get(requester, bill.getId());
    } catch (DataIntegrityViolationException ex) {
      if (key != null) {
        Optional<BillDetail> replay = replay(key, requestHash, requester);
        if (replay.isPresent()) {
          return replay.get();
        }
      }
      if (queryService.hasEffectiveBill(command.medicalRecordId())) {
        throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该病历已有有效账单，仅作废后可重开");
      }
      throw ex;
    }
  }

  public BillDetail pay(CurrentUser requester, UUID billId, String idempotencyKey) {
    requireBillingStaff(requester);
    Bill bill = queryService.requireBill(requester, billId);
    String key = normalizeKey(idempotencyKey);
    String requestHash = payHash(billId);
    if (key != null) {
      Optional<BillDetail> replay = replay(key, requestHash, requester);
      if (replay.isPresent()) {
        return replay.get();
      }
    }
    if (bill.getStatus() == BillStatus.PAID) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
    }
    if (!bill.canPay()) {
      throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
    }

    try {
      billPaymentWriter.pay(requester, billId, requestHash, key);
      return queryService.get(requester, billId);
    } catch (DataIntegrityViolationException ex) {
      if (key != null) {
        Optional<BillDetail> replay = replay(key, requestHash, requester);
        if (replay.isPresent()) {
          return replay.get();
        }
      }
      if (paymentRecordRepository.findByBillId(billId).isPresent()) {
        throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
      }
      throw ex;
    }
  }

  public BillDetail refund(CurrentUser requester, UUID billId, String reason) {
    requireBillingStaff(requester);
    billPaymentWriter.refund(requester, billId, reason);
    return queryService.get(requester, billId);
  }

  public BillDetail voidBill(CurrentUser requester, UUID billId, String reason) {
    requireBillingStaff(requester);
    billPaymentWriter.voidBill(requester, billId, reason);
    return queryService.get(requester, billId);
  }

  public BillDetail get(CurrentUser requester, UUID billId) {
    return queryService.get(requester, billId);
  }

  public Page<Bill> list(CurrentUser requester, UUID petId, BillStatus status, Pageable pageable) {
    return queryService.list(requester, petId, status, pageable);
  }

  private Optional<BillDetail> replay(String key, String requestHash, CurrentUser requester) {
    return idempotencyRepository
        .findByIdempotencyKey(key)
        .map(
            record -> {
              if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
              }
              UUID billId = record.getResultRef();
              if (billId == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "幂等记录缺少账单");
              }
              return queryService.get(requester, billId);
            });
  }

  private static String normalizeKey(String idempotencyKey) {
    return IdempotencyKeys.normalize(idempotencyKey);
  }

  private static String createHash(CreateBillCommand command) {
    String items =
        command.items().stream()
            .map(line -> line.chargeItemId() + ":" + line.quantity())
            .sorted()
            .collect(Collectors.joining(","));
    return RequestHashes.sha256(
        "create-bill|" + command.medicalRecordId() + "|" + command.prescriptionId() + "|" + items);
  }

  private static String payHash(UUID billId) {
    return RequestHashes.sha256("pay-bill|" + billId);
  }

  private static void requireBillingStaff(CurrentUser requester) {
    if (!requester.isBillingStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
