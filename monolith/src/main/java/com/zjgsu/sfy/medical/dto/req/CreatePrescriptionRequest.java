package com.zjgsu.sfy.medical.dto.req;

import com.zjgsu.sfy.medical.service.command.IssuePrescriptionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "处方内容（一张处方包含多条用药明细）")
public record CreatePrescriptionRequest(
    @Schema(description = "用药明细，至少 1 条，最多 100 条") @NotEmpty @Size(max = 100) @Valid
        List<PrescriptionLineRequest> items) {

  public IssuePrescriptionCommand toCommand() {
    return new IssuePrescriptionCommand(
        items.stream().map(PrescriptionLineRequest::toCommand).toList());
  }
}
