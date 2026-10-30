package com.zjgsu.sfy.billing.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 支付记录状态；退款后由 SUCCEEDED 变为 REFUNDED。 */
@Schema(description = "支付状态：SUCCEEDED 已支付 / REFUNDED 已退款")
public enum PaymentStatus {
  SUCCEEDED,
  REFUNDED
}
