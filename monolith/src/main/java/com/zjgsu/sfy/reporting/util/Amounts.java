package com.zjgsu.sfy.reporting.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 报表金额工具：统一 CNY、两位小数，空值视为 0。 */
public final class Amounts {

  public static final String CURRENCY = "CNY";
  public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private Amounts() {}

  public static BigDecimal normalize(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }
}
