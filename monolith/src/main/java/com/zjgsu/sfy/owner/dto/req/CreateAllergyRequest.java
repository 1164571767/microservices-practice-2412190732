package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.common.AllergySeverity;
import com.zjgsu.sfy.owner.service.command.CreateAllergyCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "一条过敏记录")
public record CreateAllergyRequest(
    @Schema(description = "过敏原名称，最多 100 字", example = "花粉") @NotBlank @Size(max = 100)
        String allergen,
    @Schema(description = "严重程度：MILD 轻微 / MODERATE 中度 / SEVERE 严重", example = "MODERATE") @NotNull
        AllergySeverity severity,
    @Schema(description = "备注，可空，最多 255 字", example = "接触后出现皮疹") @Size(max = 255) String note) {

  public CreateAllergyCommand toCommand() {
    return new CreateAllergyCommand(allergen, severity, note);
  }
}
