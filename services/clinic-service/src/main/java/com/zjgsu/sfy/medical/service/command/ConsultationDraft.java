package com.zjgsu.sfy.medical.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Drafts allow incomplete clinical text, but still enforce size and numeric bounds. */
public record ConsultationDraft(
    @NotNull @Size(max = 2000) String symptoms,
    @NotNull @Size(max = 2000) String diagnosis,
    @NotNull @Size(max = 2000) String advice,
    @NotNull Choice prescriptionChoice,
    @NotNull @Size(max = 50) List<@NotNull @Valid Line> items) {
  public enum Choice {
    PENDING,
    NONE,
    PRESCRIPTION
  }

  public record Line(
      @NotNull @Size(max = 100) String drugName,
      @Size(max = 100) String specification,
      @NotNull @Size(max = 100) String dosage,
      @NotNull @Size(max = 100) String frequency,
      @Min(1) @Max(1_000_000) int quantity,
      @Min(1) @Max(3650) int days,
      @Size(max = 255) String note) {
    public PrescriptionLineCommand toCommand() {
      return new PrescriptionLineCommand(
          drugName.trim(),
          specification == null ? null : specification.trim(),
          dosage.trim(),
          frequency.trim(),
          quantity,
          days,
          note);
    }
  }
}
