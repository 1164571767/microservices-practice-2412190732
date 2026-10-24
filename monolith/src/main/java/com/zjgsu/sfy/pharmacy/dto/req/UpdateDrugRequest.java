package com.zjgsu.sfy.pharmacy.dto.req;

import com.zjgsu.sfy.pharmacy.service.command.UpdateDrugCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "修改药品档案的请求体（整体覆盖）")
public record UpdateDrugRequest(
    @Schema(description = "药品名称，最多 100 字；与规格组合后不能与别的药品重复", example = "阿莫西林")
        @NotBlank
        @Size(max = 100)
        String name,
    @Schema(description = "规格，最多 100 字", example = "0.5g") @NotBlank @Size(max = 100)
        String specification,
    @Schema(description = "单位，最多 20 字", example = "盒") @NotBlank @Size(max = 20) String unit,
    @Schema(description = "低库存阈值，≥ 0", example = "5") @Min(0) int lowStockThreshold,
    @Schema(description = "是否启用；停用后不能再发药，必须显式传入以免误停用", example = "true") @NotNull Boolean active) {

  public UpdateDrugCommand toCommand() {
    return new UpdateDrugCommand(name, specification, unit, lowStockThreshold, active);
  }
}
