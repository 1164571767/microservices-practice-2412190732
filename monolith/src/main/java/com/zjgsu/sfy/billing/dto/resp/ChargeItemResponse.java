package com.zjgsu.sfy.billing.dto.resp;

import com.zjgsu.sfy.billing.entity.ChargeItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "收费项目")
public record ChargeItemResponse(
    @Schema(description = "收费项目 ID", example = "0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f") UUID id,
    @Schema(description = "收费项目名称", example = "诊查费") String name,
    @Schema(description = "单价，单位元（CNY）", example = "50.00") BigDecimal amount,
    @Schema(description = "是否启用；停用后不能用于生成新账单", example = "true") boolean active,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    @Schema(description = "最后更新时间（UTC）", example = "2026-10-20T01:05:00Z") Instant updatedAt) {

  public static ChargeItemResponse from(ChargeItem item) {
    return new ChargeItemResponse(
        item.getId(),
        item.getName(),
        item.getAmount(),
        item.isActive(),
        item.getCreatedAt(),
        item.getUpdatedAt());
  }
}
