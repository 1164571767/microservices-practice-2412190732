package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.service.command.UpsertPetCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "宠物的完整信息（新增和修改都用它）")
public record UpsertPetRequest(
    @Schema(description = "宠物名字，最多 50 字", example = "旺财") @NotBlank @Size(max = 50) String name,
    @Schema(description = "物种，最多 50 字，如犬、猫、兔", example = "犬") @NotBlank @Size(max = 50)
        String species,
    @Schema(description = "出生日期，不能是未来，格式 yyyy-MM-dd", example = "2022-05-01")
        @NotNull
        @PastOrPresent(message = "出生日期不能是未来")
        LocalDate birthDate,
    @Schema(description = "体重，单位千克，0.01–500.00", example = "5.50")
        @NotNull
        @DecimalMin(value = "0.01", message = "体重必须大于 0")
        @DecimalMax(value = "500.00", message = "体重超出合理范围")
        BigDecimal weightKg,
    Boolean birthDateEstimated) {

  public UpsertPetCommand toCommand() {
    return new UpsertPetCommand(name, species, birthDate, weightKg, birthDateEstimated);
  }
}
