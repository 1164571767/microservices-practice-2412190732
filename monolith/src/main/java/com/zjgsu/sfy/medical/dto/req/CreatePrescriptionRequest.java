package com.zjgsu.sfy.medical.dto.req;

import com.zjgsu.sfy.medical.service.command.IssuePrescriptionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreatePrescriptionRequest(@NotEmpty @Valid List<PrescriptionLineRequest> items) {

  public IssuePrescriptionCommand toCommand() {
    return new IssuePrescriptionCommand(
        items.stream().map(PrescriptionLineRequest::toCommand).toList());
  }
}
