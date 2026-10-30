package com.zjgsu.sfy.pharmacy.dto.resp;

import com.zjgsu.sfy.pharmacy.service.result.InventoryDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "某种药品的当前库存")
public record InventoryResponse(
    @Schema(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d") UUID drugId,
    @Schema(description = "药品名称", example = "阿莫西林") String drugName,
    @Schema(description = "规格", example = "0.5g") String specification,
    @Schema(description = "单位", example = "盒") String unit,
    @Schema(description = "当前库存数量", example = "7") int quantity,
    @Schema(description = "低库存阈值", example = "5") int lowStockThreshold,
    @Schema(description = "是否低于阈值（quantity < lowStockThreshold）", example = "false")
        boolean lowStock,
    @Schema(description = "库存最后更新时间（UTC）", example = "2026-10-20T01:05:00Z") Instant updatedAt) {

  public static InventoryResponse from(InventoryDetail detail) {
    return new InventoryResponse(
        detail.drug().getId(),
        detail.drug().getName(),
        detail.drug().getSpecification(),
        detail.drug().getUnit(),
        detail.inventory().getQuantity(),
        detail.drug().getLowStockThreshold(),
        detail.inventory().getQuantity() < detail.drug().getLowStockThreshold(),
        detail.inventory().getUpdatedAt());
  }
}
