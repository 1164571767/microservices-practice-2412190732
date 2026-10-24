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
import com.zjgsu.sfy.pharmacy.service.command.CreateDrugCommand;
import com.zjgsu.sfy.pharmacy.service.command.UpdateDrugCommand;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 药品目录用例：药房 / 管理员维护名称、规格、单位、低库存阈值与启用状态。 */
@Service
public class DrugService {

  private static final Logger log = LoggerFactory.getLogger(DrugService.class);
  private static final String INITIAL_STOCK_REASON = "初始入库";

  private final DrugRepository drugRepository;
  private final InventoryRepository inventoryRepository;
  private final InventoryAdjustmentRepository adjustmentRepository;
  private final LowStockAlertPublisher lowStockAlertPublisher;

  public DrugService(
      DrugRepository drugRepository,
      InventoryRepository inventoryRepository,
      InventoryAdjustmentRepository adjustmentRepository,
      LowStockAlertPublisher lowStockAlertPublisher) {
    this.drugRepository = drugRepository;
    this.inventoryRepository = inventoryRepository;
    this.adjustmentRepository = adjustmentRepository;
    this.lowStockAlertPublisher = lowStockAlertPublisher;
  }

  @Transactional
  public Drug create(CurrentUser requester, CreateDrugCommand command) {
    requirePharmacyStaff(requester);
    String name = command.name().trim();
    String specification = command.specification().trim();
    if (drugRepository.findByNameAndSpecification(name, specification).isPresent()) {
      throw new BusinessException(ErrorCode.DRUG_ALREADY_EXISTS);
    }

    Drug drug = Drug.create(name, specification, command.unit(), command.lowStockThreshold());
    try {
      drugRepository.saveAndFlush(drug);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.DRUG_ALREADY_EXISTS);
    }

    Inventory inventory =
        inventoryRepository.saveAndFlush(
            Inventory.initialize(drug.getId(), command.initialQuantity()));
    if (command.initialQuantity() > 0) {
      adjustmentRepository.saveAndFlush(
          InventoryAdjustment.record(
              drug.getId(),
              command.initialQuantity(),
              0,
              command.initialQuantity(),
              INITIAL_STOCK_REASON,
              requester.userId()));
    }
    if (lowStockAlertPublisher.refreshAndPublish(drug, inventory)) {
      inventoryRepository.saveAndFlush(inventory);
    }
    log.atInfo()
        .addKeyValue("operatorId", requester.userId())
        .addKeyValue("drugId", drug.getId())
        .addKeyValue("name", drug.getName())
        .addKeyValue("specification", drug.getSpecification())
        .addKeyValue("unit", drug.getUnit())
        .addKeyValue("lowStockThreshold", drug.getLowStockThreshold())
        .addKeyValue("initialQuantity", command.initialQuantity())
        .log("pharmacy.drug.created");
    return drug;
  }

  @Transactional
  public Drug update(CurrentUser requester, UUID drugId, UpdateDrugCommand command) {
    requirePharmacyStaff(requester);
    Drug drug =
        drugRepository
            .findById(drugId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "药品不存在"));
    String name = command.name().trim();
    String specification = command.specification().trim();
    if (drugRepository.existsByNameAndSpecificationAndIdNot(name, specification, drugId)) {
      throw new BusinessException(ErrorCode.DRUG_ALREADY_EXISTS);
    }
    String previousName = drug.getName();
    String previousSpecification = drug.getSpecification();
    int previousThreshold = drug.getLowStockThreshold();
    boolean previousActive = drug.isActive();
    drug.updateDetails(
        name, specification, command.unit(), command.lowStockThreshold(), command.active());
    try {
      drugRepository.saveAndFlush(drug);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.DRUG_ALREADY_EXISTS);
    }

    Inventory inventory =
        inventoryRepository
            .findByDrugIdForUpdate(drugId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_INVALID_STATE));
    if (lowStockAlertPublisher.refreshAndPublish(drug, inventory)) {
      inventoryRepository.saveAndFlush(inventory);
    }
    // 目录改名 / 规格变更会影响处方匹配，阈值变更会影响预警，必须留操作审计。
    log.atInfo()
        .addKeyValue("operatorId", requester.userId())
        .addKeyValue("drugId", drug.getId())
        .addKeyValue("beforeName", previousName)
        .addKeyValue("afterName", drug.getName())
        .addKeyValue("beforeSpecification", previousSpecification)
        .addKeyValue("afterSpecification", drug.getSpecification())
        .addKeyValue("beforeLowStockThreshold", previousThreshold)
        .addKeyValue("afterLowStockThreshold", drug.getLowStockThreshold())
        .addKeyValue("beforeActive", previousActive)
        .addKeyValue("afterActive", drug.isActive())
        .log("pharmacy.drug.updated");
    return drug;
  }

  @Transactional(readOnly = true)
  public Drug get(UUID drugId) {
    return drugRepository
        .findById(drugId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "药品不存在"));
  }

  @Transactional(readOnly = true)
  public Page<Drug> list(String query, Pageable pageable) {
    return drugRepository.search(LikePatterns.contains(query), pageable);
  }

  private static void requirePharmacyStaff(CurrentUser requester) {
    if (!requester.isPharmacyStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
