package com.zjgsu.sfy.billing.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 账单审计动作，与状态变更一一对应。 */
@Schema(description = "账单操作类型：CREATED 开单 / PAID 支付 / REFUNDED 退款 / VOIDED 作废")
public enum BillAction {
  CREATED,
  PAID,
  REFUNDED,
  VOIDED
}
