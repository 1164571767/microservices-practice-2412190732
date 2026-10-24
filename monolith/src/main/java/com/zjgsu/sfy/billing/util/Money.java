package com.zjgsu.sfy.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 金额工具：统一 2 位小数、非负校验与精确求和，币种固定 CNY。 */
public final class Money {

  public static final String CURRENCY = "CNY";
  private static final int SCALE = 2;

  private Money() {}

  public static BigDecimal normalize(BigDecimal amount) {
    if (amount == null) {
      throw new IllegalArgumentException("amount must not be null");
    }
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must not be negative");
    }
    try {
      return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("amount must have at most 2 fraction digits", ex);
    }
  }

  /** 行金额 = 单价 × 数量；数量必须为正，结果保留 2 位小数且不做舍入。 */
  public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    return normalize(unitPrice)
        .multiply(BigDecimal.valueOf(quantity))
        .setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  public static BigDecimal sum(List<BigDecimal> amounts) {
    return amounts.stream()
        .reduce(BigDecimal.ZERO.setScale(SCALE), BigDecimal::add)
        .setScale(SCALE, RoundingMode.UNNECESSARY);
  }
}
