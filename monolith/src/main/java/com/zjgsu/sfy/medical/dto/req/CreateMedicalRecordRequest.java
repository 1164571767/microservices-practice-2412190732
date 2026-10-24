package com.zjgsu.sfy.medical.dto.req;

import com.zjgsu.sfy.medical.service.command.CreateMedicalRecordCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "接诊病历的请求体")
public record CreateMedicalRecordRequest(
    @Schema(description = "对应的预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d") @NotNull
        UUID appointmentId,
    @Schema(description = "症状描述，最多 2000 字", example = "咳嗽、食欲下降") @NotBlank @Size(max = 2000)
        String symptoms,
    @Schema(description = "诊断结论，最多 2000 字", example = "急性支气管炎") @NotBlank @Size(max = 2000)
        String diagnosis,
    @Schema(description = "医嘱，可空，最多 2000 字", example = "多休息、清淡饮食") @Size(max = 2000) String advice,
    @Schema(description = "就诊时间（UTC），可空，默认取当前时间，不能是未来", example = "2026-09-15T01:05:00Z")
        @PastOrPresent(message = "就诊时间不能是未来")
        Instant visitedAt) {

  public CreateMedicalRecordCommand toCommand() {
    return new CreateMedicalRecordCommand(appointmentId, symptoms, diagnosis, advice, visitedAt);
  }
}
