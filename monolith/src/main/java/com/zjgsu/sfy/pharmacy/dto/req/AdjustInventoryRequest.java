package com.zjgsu.sfy.pharmacy.dto.req;

import com.zjgsu.sfy.pharmacy.service.command.AdjustInventoryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "人工调整库存的请求体")
public record AdjustInventoryRequest(
    @Schema(description = "调整数量：正数入库、负数出库，不能为 0", example = "-3") @NotNull Integer delta,
    @Schema(description = "调整原因，会写入流水便于追溯，最多 255 字", example = "盘点报损") @NotBlank @Size(max = 255)
        String reason) {

  @AssertTrue(message = "调整数量不能为 0")
  public boolean isDeltaNonZero() {
    return delta == null || delta != 0;
  }

  public AdjustInventoryCommand toCommand() {
    return new AdjustInventoryCommand(delta, reason);
  }
}
