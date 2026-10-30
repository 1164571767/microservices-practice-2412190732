package com.zjgsu.sfy.billing.service;

import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.entity.Bill;
import com.zjgsu.sfy.billing.entity.BillOperation;
import com.zjgsu.sfy.billing.entity.PaymentRecord;
import com.zjgsu.sfy.billing.repository.BillItemRepository;
import com.zjgsu.sfy.billing.repository.BillOperationRepository;
import com.zjgsu.sfy.billing.repository.BillRepository;
import com.zjgsu.sfy.billing.repository.PaymentRecordRepository;
import com.zjgsu.sfy.billing.service.result.BillDetail;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.owner.OwnerApi;
import com.zjgsu.sfy.owner.PetSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账单只读查询与资源归属校验；主人只能读取自己宠物的账单。 */
@Service
public class BillQueryService {

  private final BillRepository billRepository;
  private final BillItemRepository billItemRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final BillOperationRepository billOperationRepository;
  private final OwnerApi ownerApi;

  public BillQueryService(
      BillRepository billRepository,
      BillItemRepository billItemRepository,
      PaymentRecordRepository paymentRecordRepository,
      BillOperationRepository billOperationRepository,
      OwnerApi ownerApi) {
    this.billRepository = billRepository;
    this.billItemRepository = billItemRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.billOperationRepository = billOperationRepository;
    this.ownerApi = ownerApi;
  }

  @Transactional(readOnly = true)
  public BillDetail get(CurrentUser requester, UUID billId) {
    return detail(requireBill(requester, billId));
  }

  @Transactional(readOnly = true)
  public boolean hasEffectiveBill(UUID recordId) {
    return billRepository.existsByMedicalRecordIdAndStatusNot(recordId, BillStatus.VOID);
  }

  @Transactional(readOnly = true)
  public Bill requireBill(CurrentUser requester, UUID billId) {
    Bill bill =
        billRepository
            .findById(billId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "账单不存在"));
    requireCanView(requester, bill);
    return bill;
  }

  @Transactional(readOnly = true)
  public Page<Bill> list(CurrentUser requester, UUID petId, BillStatus status, Pageable pageable) {
    requireAccessiblePet(requester, petId);
    return status == null
        ? billRepository.findByPetIdOrderByCreatedAtDesc(petId, pageable)
        : billRepository.findByPetIdAndStatusOrderByCreatedAtDesc(petId, status, pageable);
  }

  /** 内部装配：调用方已完成鉴权，禁止直接暴露给 Controller。 */
  private BillDetail detail(Bill bill) {
    List<BillOperation> operations =
        billOperationRepository.findByBillIdOrderByCreatedAtAscIdAsc(bill.getId());
    Optional<PaymentRecord> payment = paymentRecordRepository.findByBillId(bill.getId());
    return new BillDetail(
        bill,
        billItemRepository.findByBillIdOrderByCreatedAtAscIdAsc(bill.getId()),
        payment,
        operations);
  }

  private void requireCanView(CurrentUser requester, Bill bill) {
    if (requester.isClinicStaff()) {
      return;
    }
    requireAccessiblePet(requester, bill.getPetId());
  }

  private void requireAccessiblePet(CurrentUser requester, UUID petId) {
    PetSummary pet =
        ownerApi
            .findPet(petId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "宠物不存在"));
    if (requester.isClinicStaff()) {
      return;
    }
    UUID myOwnerId =
        ownerApi
            .findOwnerIdByUser(requester.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FORBIDDEN, "尚未创建主人档案"));
    if (!pet.ownerId().equals(myOwnerId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
