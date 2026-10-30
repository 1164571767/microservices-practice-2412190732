package com.zjgsu.sfy.billing.dto.resp;

import com.zjgsu.sfy.billing.entity.BillOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "账单操作流水（审计用）")
public record BillOperationResponse(
    @Schema(description = "流水 ID", example = "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d") UUID id,
    @Schema(description = "账单 ID", example = "9a1f2b3c-4d5e-4f60-8a7b-1c2d3e4f5a6b") UUID billId,
    @Schema(
            description = "操作类型：CREATED 开单 / PAID 支付 / REFUNDED 退款 / VOIDED 作废",
            example = "CREATED")
        String action,
    @Schema(description = "操作前状态，开单时为空", example = "UNPAID") String fromStatus,
    @Schema(description = "操作后状态", example = "PAID") String toStatus,
    @Schema(description = "退款或作废原因，其他操作为空", example = "主人取消服务") String reason,
    @Schema(description = "操作人（账号）ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
        UUID operatorId,
    @Schema(description = "操作时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt) {

  public static BillOperationResponse from(BillOperation operation) {
    return new BillOperationResponse(
        operation.getId(),
        operation.getBillId(),
        operation.getAction().name(),
        operation.getFromStatus() == null ? null : operation.getFromStatus().name(),
        operation.getToStatus().name(),
        operation.getReason(),
        operation.getOperatorId(),
        operation.getCreatedAt());
  }
}
