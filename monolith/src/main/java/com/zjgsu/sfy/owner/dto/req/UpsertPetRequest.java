package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.service.command.UpsertPetCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertPetRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(max = 50) String species,
    @NotNull @PastOrPresent(message = "出生日期不能是未来") LocalDate birthDate,
    @NotNull
        @DecimalMin(value = "0.01", message = "体重必须大于 0")
        @DecimalMax(value = "500.00", message = "体重超出合理范围")
        BigDecimal weightKg) {

  public UpsertPetCommand toCommand() {
    return new UpsertPetCommand(name, species, birthDate, weightKg);
  }
}
