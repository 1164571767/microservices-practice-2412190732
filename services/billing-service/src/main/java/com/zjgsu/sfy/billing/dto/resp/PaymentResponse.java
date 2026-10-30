package com.zjgsu.sfy.billing.dto.resp;

import com.zjgsu.sfy.billing.entity.PaymentRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "支付记录")
public record PaymentResponse(
    @Schema(description = "支付记录 ID", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b") UUID id,
    @Schema(description = "账单 ID", example = "9a1f2b3c-4d5e-4f60-8a7b-1c2d3e4f5a6b") UUID billId,
    @Schema(description = "支付金额，单位元", example = "75.00") BigDecimal amount,
    @Schema(description = "币种，固定 CNY", example = "CNY") String currency,
    @Schema(description = "支付方式，本期固定 MOCK（模拟支付）", example = "MOCK") String method,
    @Schema(description = "支付状态：SUCCEEDED 已支付 / REFUNDED 已退款", example = "SUCCEEDED") String status,
    @Schema(description = "支付人（账号）ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
        UUID paidBy,
    @Schema(description = "支付时间（UTC）", example = "2026-10-20T02:00:00Z") Instant paidAt,
    @Schema(description = "退款操作人（账号）ID，未退款为空", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
        UUID refundedBy,
    @Schema(description = "退款时间（UTC），未退款为空", example = "2026-10-20T03:00:00Z") Instant refundedAt,
    @Schema(description = "退款原因，未退款为空", example = "主人取消服务") String refundReason) {

  public static PaymentResponse from(PaymentRecord payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getBillId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getMethod().name(),
        payment.getStatus().name(),
        payment.getPaidBy(),
        payment.getPaidAt(),
        payment.getRefundedBy(),
        payment.getRefundedAt(),
        payment.getRefundReason());
  }
}
