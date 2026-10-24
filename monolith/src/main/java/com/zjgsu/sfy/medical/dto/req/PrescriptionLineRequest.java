package com.zjgsu.sfy.medical.dto.req;

import com.zjgsu.sfy.medical.service.command.PrescriptionLineCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "一条用药明细")
public record PrescriptionLineRequest(
    @Schema(description = "药品名称，最多 100 字", example = "阿莫西林") @NotBlank @Size(max = 100)
        String drugName,
    @Schema(description = "规格，可空，最多 100 字；药房按名称 + 规格匹配药品", example = "0.5g") @Size(max = 100)
        String specification,
    @Schema(description = "单次剂量，最多 100 字", example = "1片") @NotBlank @Size(max = 100) String dosage,
    @Schema(description = "用药频次，最多 100 字", example = "每日2次") @NotBlank @Size(max = 100)
        String frequency,
    @Schema(description = "发药总数量，≥ 1", example = "6") @Min(1) @Max(1_000_000) int quantity,
    @Schema(description = "用药天数，≥ 1", example = "5") @Min(1) @Max(3650) int days,
    @Schema(description = "备注，可空，最多 255 字", example = "饭后服用") @Size(max = 255) String note) {

  public PrescriptionLineCommand toCommand() {
    return new PrescriptionLineCommand(
        drugName, specification, dosage, frequency, quantity, days, note);
  }
}
