package com.zjgsu.sfy.billing.dto.resp;

import com.zjgsu.sfy.billing.entity.BillItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "账单中的一行明细（保存下单时的项目名与单价快照）")
public record BillItemResponse(
    @Schema(description = "明细 ID", example = "3d4e5f6a-7b8c-4d9e-8f0a-1b2c3d4e5f6a") UUID id,
    @Schema(description = "收费项目 ID", example = "0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        UUID chargeItemId,
    @Schema(description = "收费项目名称（快照）", example = "诊查费") String itemName,
    @Schema(description = "单价（快照），单位元", example = "50.00") BigDecimal unitPrice,
    @Schema(description = "数量", example = "1") int quantity,
    @Schema(description = "行金额 = 单价 × 数量，单位元", example = "50.00") BigDecimal amount) {

  public static BillItemResponse from(BillItem item) {
    return new BillItemResponse(
        item.getId(),
        item.getChargeItemId(),
        item.getItemName(),
        item.getUnitPrice(),
        item.getQuantity(),
        item.getAmount());
  }
}
