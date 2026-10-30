package com.zjgsu.sfy.pharmacy.dto.resp;

import com.zjgsu.sfy.pharmacy.entity.Drug;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "药品档案")
public record DrugResponse(
    @Schema(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d") UUID id,
    @Schema(description = "药品名称", example = "阿莫西林") String name,
    @Schema(description = "规格", example = "0.5g") String specification,
    @Schema(description = "单位", example = "盒") String unit,
    @Schema(description = "低库存阈值", example = "5") int lowStockThreshold,
    @Schema(description = "是否启用；停用后不能发药", example = "true") boolean active,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    @Schema(description = "最后更新时间（UTC）", example = "2026-10-20T01:05:00Z") Instant updatedAt) {

  public static DrugResponse from(Drug drug) {
    return new DrugResponse(
        drug.getId(),
        drug.getName(),
        drug.getSpecification(),
        drug.getUnit(),
        drug.getLowStockThreshold(),
        drug.isActive(),
        drug.getCreatedAt(),
        drug.getUpdatedAt());
  }
}
