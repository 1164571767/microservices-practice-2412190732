package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecord;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecordRepository;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.medical.PrescriptionItemSummary;
import com.zjgsu.sfy.medical.PrescriptionSummary;
import com.zjgsu.sfy.pharmacy.PrescriptionDispensed;
import com.zjgsu.sfy.pharmacy.entity.DispenseItem;
import com.zjgsu.sfy.pharmacy.entity.DispenseRecord;
import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.pharmacy.entity.Inventory;
import com.zjgsu.sfy.pharmacy.repository.DispenseItemRepository;
import com.zjgsu.sfy.pharmacy.repository.DispenseRecordRepository;
import com.zjgsu.sfy.pharmacy.repository.DrugRepository;
import com.zjgsu.sfy.pharmacy.repository.InventoryRepository;
import com.zjgsu.sfy.pharmacy.service.result.DispenseDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发药写侧事务：锁定 / 条件扣减库存、写发药记录与明细、登记幂等键、发布事件。
 *
 * <p>独立成 bean，使 {@link DispenseService} 能在事务外捕获并发重复（唯一键）后回放已提交结果。
 */
@Service
public class DispenseWriter {

  private final MedicalApi medicalApi;
  private final DrugRepository drugRepository;
  private final InventoryRepository inventoryRepository;
  private final DispenseRecordRepository dispenseRecordRepository;
  private final DispenseItemRepository dispenseItemRepository;
  private final IdempotencyRecordRepository idempotencyRepository;
  private final LowStockAlertPublisher lowStockAlertPublisher;
  private final ApplicationEventPublisher events;

  public DispenseWriter(
      MedicalApi medicalApi,
      DrugRepository drugRepository,
      InventoryRepository inventoryRepository,
      DispenseRecordRepository dispenseRecordRepository,
      DispenseItemRepository dispenseItemRepository,
      IdempotencyRecordRepository idempotencyRepository,
      LowStockAlertPublisher lowStockAlertPublisher,
      ApplicationEventPublisher events) {
    this.medicalApi = medicalApi;
    this.drugRepository = drugRepository;
    this.inventoryRepository = inventoryRepository;
    this.dispenseRecordRepository = dispenseRecordRepository;
    this.dispenseItemRepository = dispenseItemRepository;
    this.idempotencyRepository = idempotencyRepository;
    this.lowStockAlertPublisher = lowStockAlertPublisher;
    this.events = events;
  }

  @Transactional
  public DispenseDetail write(
      CurrentUser requester, UUID prescriptionId, String requestHash, String idempotencyKey) {
    PrescriptionSummary prescription =
        medicalApi
            .findPrescription(prescriptionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "处方不存在"));
    if (prescription.items().isEmpty()) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "处方没有明细");
    }

    Map<UUID, RequiredDrug> required = resolveRequiredDrugs(prescription.items());
    if (required.isEmpty()) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "处方没有可发药明细");
    }

    Instant now = Instant.now();
    DispenseRecord record =
        DispenseRecord.create(
            prescription.id(),
            prescription.medicalRecordId(),
            prescription.petId(),
            prescription.doctorId(),
            requester.userId(),
            now);
    // 先落发药单头：prescription_id 唯一键在并发下串行化，并作为「已发药」的最终守卫。
    dispenseRecordRepository.saveAndFlush(record);

    List<RequiredDrug> lines =
        required.values().stream()
            .sorted(Comparator.comparing(line -> line.drug().getId().toString()))
            .toList();
    List<DispenseItem> items = new ArrayList<>(lines.size());
    for (RequiredDrug line : lines) {
      int changed = inventoryRepository.deduct(line.drug().getId(), line.quantity(), now);
      if (changed == 0) {
        if (!inventoryRepository.existsById(line.drug().getId())) {
          throw new BusinessException(ErrorCode.INVENTORY_INVALID_STATE, "药品库存记录不存在");
        }
        throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
      }
    }
    for (RequiredDrug line : lines) {
      Inventory inventory =
          inventoryRepository
              .findById(line.drug().getId())
              .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_INVALID_STATE));
      if (lowStockAlertPublisher.refreshAndPublish(line.drug(), inventory)) {
        inventoryRepository.saveAndFlush(inventory);
      }
      items.add(
          DispenseItem.create(
              record.getId(),
              line.drug().getId(),
              line.drug().getName(),
              line.drug().getSpecification(),
              line.drug().getUnit(),
              line.quantity(),
              inventory.getQuantity()));
    }
    dispenseItemRepository.saveAllAndFlush(items);

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      idempotencyRepository.saveAndFlush(
          new IdempotencyRecord(idempotencyKey, requestHash, record.getId()));
    }
    List<PrescriptionDispensed.DispensedItem> dispensedItems =
        items.stream()
            .map(
                item ->
                    new PrescriptionDispensed.DispensedItem(
                        item.getDrugId(),
                        item.getDrugName(),
                        item.getSpecification(),
                        item.getUnit(),
                        item.getQuantity()))
            .toList();
    events.publishEvent(
        new PrescriptionDispensed(
            record.getId(),
            record.getPrescriptionId(),
            record.getMedicalRecordId(),
            record.getPetId(),
            record.getDispensedBy(),
            record.getDispensedAt(),
            dispensedItems));
    return new DispenseDetail(record, items);
  }

  private Map<UUID, RequiredDrug> resolveRequiredDrugs(List<PrescriptionItemSummary> items) {
    Map<UUID, RequiredDrug> required = new LinkedHashMap<>();
    for (PrescriptionItemSummary item : items) {
      Drug drug = resolveDrug(item);
      required.merge(
          drug.getId(),
          new RequiredDrug(drug, item.quantity()),
          (left, right) ->
              new RequiredDrug(left.drug(), Math.addExact(left.quantity(), right.quantity())));
    }
    return required;
  }

  private Drug resolveDrug(PrescriptionItemSummary item) {
    String name = item.drugName().trim();
    String specification = item.specification() == null ? null : item.specification().trim();
    Drug drug;
    if (specification == null || specification.isBlank()) {
      List<Drug> candidates = drugRepository.findByName(name);
      if (candidates.isEmpty()) {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "药品目录中不存在: " + name);
      }
      if (candidates.size() > 1) {
        throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "药品存在多个规格，请指定规格");
      }
      drug = candidates.get(0);
    } else {
      drug =
          drugRepository
              .findByNameAndSpecification(name, specification)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.RESOURCE_NOT_FOUND, "药品目录中不存在: " + name + " " + specification));
    }
    if (!drug.isActive()) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "药品已停用: " + drug.getName());
    }
    return drug;
  }

  private record RequiredDrug(Drug drug, int quantity) {}
}
