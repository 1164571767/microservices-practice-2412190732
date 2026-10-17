package com.zjgsu.sfy.owner.dto.req;

import com.zjgsu.sfy.owner.common.AllergySeverity;
import com.zjgsu.sfy.owner.service.command.CreateAllergyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAllergyRequest(
    @NotBlank @Size(max = 100) String allergen,
    @NotNull AllergySeverity severity,
    @Size(max = 255) String note) {

  public CreateAllergyCommand toCommand() {
    return new CreateAllergyCommand(allergen, severity, note);
  }
}
