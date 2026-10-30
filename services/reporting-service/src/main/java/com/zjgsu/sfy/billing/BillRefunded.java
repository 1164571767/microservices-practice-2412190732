package com.zjgsu.sfy.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 账单退款成功事件；供统计等下游模块冲减收入，不暴露 billing 内部实体。 */
public record BillRefunded(
    UUID billId,
    UUID paymentId,
    UUID petId,
    BigDecimal amount,
    String currency,
    UUID refundedBy,
    Instant refundedAt) {}
