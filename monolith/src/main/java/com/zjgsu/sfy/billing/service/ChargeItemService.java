package com.zjgsu.sfy.billing.service;

import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.billing.repository.ChargeItemRepository;
import com.zjgsu.sfy.billing.service.command.CreateChargeItemCommand;
import com.zjgsu.sfy.billing.service.command.UpdateChargeItemCommand;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.query.LikePatterns;
import com.zjgsu.sfy.identity.CurrentUser;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 收费项目目录用例：仅管理员可维护，金额由服务端校验并统一为 2 位小数。 */
@Service
public class ChargeItemService {

  private static final Logger log = LoggerFactory.getLogger(ChargeItemService.class);

  private final ChargeItemRepository chargeItemRepository;

  public ChargeItemService(ChargeItemRepository chargeItemRepository) {
    this.chargeItemRepository = chargeItemRepository;
  }

  @Transactional
  public ChargeItem create(CurrentUser requester, CreateChargeItemCommand command) {
    requireAdmin(requester);
    String name = requireName(command.name());
    if (chargeItemRepository.findByName(name).isPresent()) {
      throw new BusinessException(ErrorCode.CHARGE_ITEM_ALREADY_EXISTS);
    }
    ChargeItem item = ChargeItem.create(name, command.amount());
    try {
      ChargeItem saved = chargeItemRepository.saveAndFlush(item);
      log.atInfo()
          .addKeyValue("operatorId", requester.userId())
          .addKeyValue("chargeItemId", saved.getId())
          .addKeyValue("name", saved.getName())
          .addKeyValue("amount", saved.getAmount())
          .addKeyValue("active", saved.isActive())
          .log("billing.charge_item.created");
      return saved;
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.CHARGE_ITEM_ALREADY_EXISTS);
    }
  }

  @Transactional
  public ChargeItem update(
      CurrentUser requester, UUID chargeItemId, UpdateChargeItemCommand command) {
    requireAdmin(requester);
    ChargeItem item =
        chargeItemRepository
            .findById(chargeItemId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "收费项目不存在"));
    String name = requireName(command.name());
    if (chargeItemRepository.existsByNameAndIdNot(name, chargeItemId)) {
      throw new BusinessException(ErrorCode.CHARGE_ITEM_ALREADY_EXISTS);
    }
    BigDecimal previousAmount = item.getAmount();
    String previousName = item.getName();
    boolean previousActive = item.isActive();
    item.update(name, command.amount(), command.active());
    try {
      ChargeItem saved = chargeItemRepository.saveAndFlush(item);
      // 价格主数据变更直接影响后续账单金额，必须留可检索的操作审计（历史账单仍用快照，不受影响）。
      log.atInfo()
          .addKeyValue("operatorId", requester.userId())
          .addKeyValue("chargeItemId", saved.getId())
          .addKeyValue("beforeName", previousName)
          .addKeyValue("afterName", saved.getName())
          .addKeyValue("beforeAmount", previousAmount)
          .addKeyValue("afterAmount", saved.getAmount())
          .addKeyValue("beforeActive", previousActive)
          .addKeyValue("afterActive", saved.isActive())
          .log("billing.charge_item.updated");
      return saved;
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.CHARGE_ITEM_ALREADY_EXISTS);
    }
  }

  @Transactional(readOnly = true)
  public ChargeItem get(UUID chargeItemId) {
    return chargeItemRepository
        .findById(chargeItemId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "收费项目不存在"));
  }

  @Transactional(readOnly = true)
  public Page<ChargeItem> list(String query, Pageable pageable) {
    return chargeItemRepository.search(LikePatterns.contains(query), pageable);
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "收费项目名称不能为空");
    }
    return name.trim();
  }

  private static void requireAdmin(CurrentUser requester) {
    if (!requester.isAdmin()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
