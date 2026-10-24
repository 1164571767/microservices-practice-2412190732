package com.zjgsu.sfy.appointment.dto.req;

import com.zjgsu.sfy.appointment.service.command.CreateScheduleCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "新建排班的请求体")
public record CreateScheduleRequest(
    @Schema(description = "接诊医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d") @NotNull
        UUID doctorId,
    @Schema(description = "排班开始时间（UTC ISO-8601）", example = "2026-11-10T00:00:00Z") @NotNull
        Instant startAt,
    @Schema(description = "排班结束时间（UTC ISO-8601），必须晚于开始时间", example = "2026-11-10T04:00:00Z")
        @NotNull
        Instant endAt,
    @Schema(description = "每个号源的时长（分钟），1–480", example = "30") @Min(1) @Max(480) int slotMinutes) {

  @AssertTrue(message = "结束时间必须晚于开始时间")
  public boolean isRangeValid() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }

  public CreateScheduleCommand toCommand() {
    return new CreateScheduleCommand(doctorId, startAt, endAt, slotMinutes);
  }
}
