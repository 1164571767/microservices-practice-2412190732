package com.zjgsu.sfy.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 收费成功事件；供通知 / 统计等下游模块订阅，不暴露 billing 内部实体。 */
public record BillPaid(
    UUID billId,
    UUID paymentId,
    UUID petId,
    UUID medicalRecordId,
    UUID prescriptionId,
    BigDecimal amount,
    String currency,
    UUID paidBy,
    Instant paidAt) {}
