package com.zjgsu.sfy.medical.dto.req;

import com.zjgsu.sfy.medical.service.command.PrescriptionLineCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescriptionLineRequest(
    @NotBlank @Size(max = 100) String drugName,
    @Size(max = 100) String specification,
    @NotBlank @Size(max = 100) String dosage,
    @NotBlank @Size(max = 100) String frequency,
    @Min(1) int quantity,
    @Min(1) int days,
    @Size(max = 255) String note) {

  public PrescriptionLineCommand toCommand() {
    return new PrescriptionLineCommand(
        drugName, specification, dosage, frequency, quantity, days, note);
  }
}
