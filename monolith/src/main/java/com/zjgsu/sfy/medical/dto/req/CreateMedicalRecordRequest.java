package com.zjgsu.sfy.medical.dto.req;

import com.zjgsu.sfy.medical.service.command.CreateMedicalRecordCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateMedicalRecordRequest(
    @NotNull UUID appointmentId,
    @NotBlank @Size(max = 2000) String symptoms,
    @NotBlank @Size(max = 2000) String diagnosis,
    @Size(max = 2000) String advice,
    @PastOrPresent(message = "就诊时间不能是未来") Instant visitedAt) {

  public CreateMedicalRecordCommand toCommand() {
    return new CreateMedicalRecordCommand(appointmentId, symptoms, diagnosis, advice, visitedAt);
  }
}
