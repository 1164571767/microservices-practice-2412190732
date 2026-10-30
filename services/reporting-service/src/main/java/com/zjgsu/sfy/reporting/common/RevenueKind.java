package com.zjgsu.sfy.reporting.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 收入事实类型；退款以负数口径单独记账，便于审计与重放。 */
@Schema(description = "收入事实类型：PAID 支付 / REFUNDED 退款（按负数口径单独记账）")
public enum RevenueKind {
  PAID,
  REFUNDED
}
