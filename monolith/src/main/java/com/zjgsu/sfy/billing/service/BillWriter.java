package com.zjgsu.sfy.billing.service;

import com.zjgsu.sfy.billing.entity.Bill;
import com.zjgsu.sfy.billing.entity.BillItem;
import com.zjgsu.sfy.billing.entity.BillLine;
import com.zjgsu.sfy.billing.entity.BillOperation;
import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.billing.repository.BillItemRepository;
import com.zjgsu.sfy.billing.repository.BillOperationRepository;
import com.zjgsu.sfy.billing.repository.BillRepository;
import com.zjgsu.sfy.billing.repository.ChargeItemRepository;
import com.zjgsu.sfy.billing.service.command.BillLineCommand;
import com.zjgsu.sfy.billing.service.command.CreateBillCommand;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecord;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecordRepository;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.medical.MedicalRecordSummary;
import com.zjgsu.sfy.medical.PrescriptionSummary;
import com.zjgsu.sfy.owner.OwnerApi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账单创建写侧事务：校验病历 / 处方 / 宠物归属，按目录价格生成行金额与总金额，写审计流水与幂等记录。
 *
 * <p>独立成 bean，使 {@link BillService} 能在事务外捕获并发幂等键冲突后回放已提交结果。
 */
@Service
public class BillWriter {

  private final MedicalApi medicalApi;
  private final OwnerApi ownerApi;
  private final ChargeItemRepository chargeItemRepository;
  private final BillRepository billRepository;
  private final BillItemRepository billItemRepository;
  private final BillOperationRepository billOperationRepository;
  private final IdempotencyRecordRepository idempotencyRepository;

  public BillWriter(
      MedicalApi medicalApi,
      OwnerApi ownerApi,
      ChargeItemRepository chargeItemRepository,
      BillRepository billRepository,
      BillItemRepository billItemRepository,
      BillOperationRepository billOperationRepository,
      IdempotencyRecordRepository idempotencyRepository) {
    this.medicalApi = medicalApi;
    this.ownerApi = ownerApi;
    this.chargeItemRepository = chargeItemRepository;
    this.billRepository = billRepository;
    this.billItemRepository = billItemRepository;
    this.billOperationRepository = billOperationRepository;
    this.idempotencyRepository = idempotencyRepository;
  }

  @Transactional
  public Bill create(
      CurrentUser requester, CreateBillCommand command, String requestHash, String idempotencyKey) {
    if (idempotencyKey != null) {
      Optional<IdempotencyRecord> existing =
          idempotencyRepository.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
        IdempotencyRecord record = existing.get();
        if (!record.getRequestHash().equals(requestHash)) {
          throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        UUID billId = record.getResultRef();
        if (billId == null) {
          throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "幂等记录缺少账单");
        }
        return billRepository
            .findById(billId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "账单不存在"));
      }
    }

    MedicalRecordSummary record =
        medicalApi
            .findRecord(command.medicalRecordId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "病历不存在"));
    ownerApi
        .findPet(record.petId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "宠物不存在"));
    requirePrescriptionBelongsToRecord(command.prescriptionId(), record);

    if (billRepository.existsByMedicalRecordIdAndStatusNot(
        record.recordId(), com.zjgsu.sfy.billing.common.BillStatus.VOID)) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该病历已有有效账单，仅作废后可重开");
    }
    List<BillLine> lines = resolveLines(command.items());
    Bill bill =
        Bill.create(
            record.petId(), record.recordId(), command.prescriptionId(), requester.userId(), lines);
    billRepository.saveAndFlush(bill);
    List<BillItem> items = lines.stream().map(line -> BillItem.create(bill.getId(), line)).toList();
    billItemRepository.saveAllAndFlush(items);
    billOperationRepository.saveAndFlush(BillOperation.created(bill.getId(), requester.userId()));
    if (idempotencyKey != null) {
      idempotencyRepository.saveAndFlush(
          new IdempotencyRecord(idempotencyKey, requestHash, bill.getId()));
    }
    return bill;
  }

  private void requirePrescriptionBelongsToRecord(
      UUID prescriptionId, MedicalRecordSummary record) {
    if (prescriptionId == null) {
      return;
    }
    PrescriptionSummary prescription =
        medicalApi
            .findPrescription(prescriptionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "处方不存在"));
    if (!prescription.medicalRecordId().equals(record.recordId())
        || !prescription.petId().equals(record.petId())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "处方不属于该病历");
    }
  }

  private List<BillLine> resolveLines(List<BillLineCommand> commands) {
    if (commands.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "账单至少包含一个收费项目");
    }
    Set<UUID> ids = new HashSet<>();
    for (BillLineCommand command : commands) {
      if (command.chargeItemId() == null) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "收费项目 id 不能为空");
      }
      if (command.quantity() <= 0) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "收费项目数量必须为正");
      }
      if (!ids.add(command.chargeItemId())) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "同一收费项目不能重复");
      }
    }

    Map<UUID, ChargeItem> catalog =
        chargeItemRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(ChargeItem::getId, Function.identity()));
    List<BillLine> lines = new ArrayList<>(commands.size());
    for (BillLineCommand command : commands) {
      ChargeItem item = catalog.get(command.chargeItemId());
      if (item == null) {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "收费项目不存在");
      }
      if (!item.isActive()) {
        throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "收费项目已停用: " + item.getName());
      }
      lines.add(new BillLine(item.getId(), item.getName(), item.getAmount(), command.quantity()));
    }
    return lines;
  }
}
