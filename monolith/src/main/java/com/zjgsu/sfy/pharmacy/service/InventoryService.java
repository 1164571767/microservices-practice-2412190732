package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.query.LikePatterns;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.pharmacy.entity.Inventory;
import com.zjgsu.sfy.pharmacy.entity.InventoryAdjustment;
import com.zjgsu.sfy.pharmacy.repository.DrugRepository;
import com.zjgsu.sfy.pharmacy.repository.InventoryAdjustmentRepository;
import com.zjgsu.sfy.pharmacy.repository.InventoryRepository;
import com.zjgsu.sfy.pharmacy.service.command.AdjustInventoryCommand;
import com.zjgsu.sfy.pharmacy.service.result.InventoryAdjustmentResult;
import com.zjgsu.sfy.pharmacy.service.result.InventoryDetail;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 库存用例：查询、低库存查询与人工调整；每次调整都写流水，可追溯操作人与前后数量。 */
@Service
public class InventoryService {

  private final DrugRepository drugRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryAdjustmentRepository adjustmentRepository;
  private final LowStockAlertPublisher lowStockAlertPublisher;

  public InventoryService(
      DrugRepository drugRepository,
      InventoryRepository inventoryRepository,
      InventoryAdjustmentRepository adjustmentRepository,
      LowStockAlertPublisher lowStockAlertPublisher) {
    this.drugRepository = drugRepository;
    this.inventoryRepository = inventoryRepository;
    this.adjustmentRepository = adjustmentRepository;
    this.lowStockAlertPublisher = lowStockAlertPublisher;
  }

  @Transactional(readOnly = true)
  public InventoryDetail get(UUID drugId) {
    Inventory inventory =
        inventoryRepository
            .findWithDrugByDrugId(drugId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "药品不存在"));
    return new InventoryDetail(inventory, inventory.getDrug());
  }

  @Transactional(readOnly = true)
  public Page<InventoryDetail> list(String query, boolean lowStockOnly, Pageable pageable) {
    String pattern = LikePatterns.contains(query);
    Page<Inventory> page =
        lowStockOnly
            ? inventoryRepository.searchLowStock(pattern, pageable)
            : inventoryRepository.search(pattern, pageable);
    return page.map(inventory -> new InventoryDetail(inventory, inventory.getDrug()));
  }

  @Transactional(readOnly = true)
  public Page<InventoryAdjustment> adjustments(UUID drugId, Pageable pageable) {
    if (!drugRepository.existsById(drugId)) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "药品不存在");
    }
    return adjustmentRepository.findByDrugIdOrderByCreatedAtDesc(drugId, pageable);
  }

  @Transactional
  public InventoryAdjustmentResult adjust(
      CurrentUser requester, UUID drugId, AdjustInventoryCommand command) {
    requirePharmacyStaff(requester);
    if (command.delta() == 0) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "调整数量不能为 0");
    }
    Drug drug =
        drugRepository
            .findById(drugId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "药品不存在"));
    Inventory inventory =
        inventoryRepository
            .findByDrugIdForUpdate(drugId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_INVALID_STATE));

    int before = inventory.getQuantity();
    if ((long) before + command.delta() < 0) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
    }
    inventory.adjust(command.delta());
    int after = inventory.getQuantity();

    InventoryAdjustment adjustment =
        adjustmentRepository.saveAndFlush(
            InventoryAdjustment.record(
                drugId, command.delta(), before, after, command.reason(), requester.userId()));
    if (lowStockAlertPublisher.refreshAndPublish(drug, inventory)) {
      inventoryRepository.saveAndFlush(inventory);
    }
    return new InventoryAdjustmentResult(new InventoryDetail(inventory, drug), adjustment);
  }

  private static void requirePharmacyStaff(CurrentUser requester) {
    if (!requester.isPharmacyStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
