package com.zjgsu.sfy.pharmacy.dto.req;

import com.zjgsu.sfy.pharmacy.service.command.CreateDrugCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "新建药品档案的请求体")
public record CreateDrugRequest(
    @Schema(description = "药品名称，最多 100 字；与规格组合后不能重复", example = "阿莫西林") @NotBlank @Size(max = 100)
        String name,
    @Schema(description = "规格，最多 100 字", example = "0.5g") @NotBlank @Size(max = 100)
        String specification,
    @Schema(description = "单位，最多 20 字", example = "盒") @NotBlank @Size(max = 20) String unit,
    @Schema(description = "低库存阈值，≥ 0；库存低于该值时进入预警列表", example = "5") @Min(0) int lowStockThreshold,
    @Schema(description = "初始库存数量，≥ 0；大于 0 时会自动记一条「初始入库」流水", example = "20") @Min(0)
        int initialQuantity) {

  public CreateDrugCommand toCommand() {
    return new CreateDrugCommand(name, specification, unit, lowStockThreshold, initialQuantity);
  }
}
