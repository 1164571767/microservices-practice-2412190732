package com.zjgsu.sfy.pharmacy.dto.resp;

import com.zjgsu.sfy.pharmacy.entity.InventoryAdjustment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "一条库存调整流水")
public record InventoryAdjustmentResponse(
    @Schema(description = "流水 ID", example = "8c9d0e1f-2a3b-4c5d-9e6f-7a8b9c0d1e2f") UUID id,
    @Schema(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d") UUID drugId,
    @Schema(description = "调整数量，正数入库、负数出库", example = "-3") int delta,
    @Schema(description = "调整前库存", example = "10") int quantityBefore,
    @Schema(description = "调整后库存", example = "7") int quantityAfter,
    @Schema(description = "调整原因", example = "盘点报损") String reason,
    @Schema(description = "操作人（账号）ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
        UUID operatorId,
    @Schema(description = "发生时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt) {

  public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
    return new InventoryAdjustmentResponse(
        adjustment.getId(),
        adjustment.getDrugId(),
        adjustment.getDelta(),
        adjustment.getQuantityBefore(),
        adjustment.getQuantityAfter(),
        adjustment.getReason(),
        adjustment.getOperatorId(),
        adjustment.getCreatedAt());
  }
}
