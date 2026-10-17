package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.service.command.CreateVaccinationCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateVaccinationRequest(
    @NotBlank @Size(max = 100) String vaccineName,
    @NotNull @PastOrPresent(message = "接种日期不能是未来") LocalDate administeredOn,
    LocalDate nextDueOn,
    @Size(max = 255) String note) {

  public CreateVaccinationCommand toCommand() {
    return new CreateVaccinationCommand(vaccineName, administeredOn, nextDueOn, note);
  }
}
