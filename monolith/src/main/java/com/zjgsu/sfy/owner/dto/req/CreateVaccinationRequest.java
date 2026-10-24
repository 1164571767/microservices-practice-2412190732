package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.service.command.CreateVaccinationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "一条疫苗接种记录")
public record CreateVaccinationRequest(
    @Schema(description = "疫苗名称，最多 100 字", example = "狂犬疫苗") @NotBlank @Size(max = 100)
        String vaccineName,
    @Schema(description = "接种日期，不能是未来，格式 yyyy-MM-dd", example = "2026-01-01")
        @NotNull
        @PastOrPresent(message = "接种日期不能是未来")
        LocalDate administeredOn,
    @Schema(description = "下次应接种日期，可空，格式 yyyy-MM-dd", example = "2027-01-01") LocalDate nextDueOn,
    @Schema(description = "备注，可空，最多 255 字", example = "无不良反应") @Size(max = 255) String note) {

  public CreateVaccinationCommand toCommand() {
    return new CreateVaccinationCommand(vaccineName, administeredOn, nextDueOn, note);
  }
}
