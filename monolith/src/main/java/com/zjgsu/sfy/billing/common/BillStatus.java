package com.zjgsu.sfy.billing.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 账单状态机：UNPAID → PAID → REFUNDED；UNPAID → VOID。 */
@Schema(description = "账单状态：UNPAID 待支付 / PAID 已支付 / REFUNDED 已退款 / VOID 已作废")
public enum BillStatus {
  UNPAID,
  PAID,
  REFUNDED,
  VOID
}
