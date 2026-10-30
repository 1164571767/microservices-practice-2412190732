package com.zjgsu.sfy.billing.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 本期只做模拟支付，不接入真实支付渠道。 */
@Schema(description = "支付方式：MOCK 模拟支付（本期不接真实支付渠道）")
public enum PaymentMethod {
  MOCK
}
