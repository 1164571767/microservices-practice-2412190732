package com.zjgsu.sfy.billing.entity;

import com.zjgsu.sfy.billing.util.Money;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * 账单行草稿：保存收费项目快照（名称、单价）与数量。
 *
 * <p>行金额由单价与数量精确计算；账单总金额由 {@link Bill} 从同一批行汇总，避免客户端传入金额。
 */
public record BillLine(UUID chargeItemId, String itemName, BigDecimal unitPrice, int quantity) {

  public BillLine {
    Objects.requireNonNull(chargeItemId, "chargeItemId must not be null");
    if (itemName == null || itemName.isBlank()) {
      throw new IllegalArgumentException("itemName must not be blank");
    }
    itemName = itemName.trim();
    unitPrice = Money.normalize(unitPrice);
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
  }

  public BigDecimal amount() {
    return Money.multiply(unitPrice, quantity);
  }
}
