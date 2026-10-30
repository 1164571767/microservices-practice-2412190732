package com.zjgsu.sfy.billing.dto.req;

import com.zjgsu.sfy.billing.service.command.CreateChargeItemCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "新增收费项目的请求体")
public record CreateChargeItemRequest(
    @Schema(description = "收费项目名称，最多 100 字，不能与已有项目重名", example = "诊查费") @NotBlank @Size(max = 100)
        String name,
    @Schema(description = "单价，单位元（CNY），≥ 0，最多 2 位小数", example = "50.00")
        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount) {

  public CreateChargeItemCommand toCommand() {
    return new CreateChargeItemCommand(name, amount);
  }
}
