package com.zjgsu.sfy.appointment.dto.req;

import com.zjgsu.sfy.appointment.service.command.RescheduleCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(description = "预约改期的请求体")
public record RescheduleRequest(
    @Schema(description = "新的开始时间（UTC ISO-8601），需可预约", example = "2026-11-11T01:00:00Z") @NotNull
        Instant startAt,
    @Schema(description = "新的结束时间（UTC ISO-8601），必须晚于开始时间", example = "2026-11-11T01:30:00Z")
        @NotNull
        Instant endAt) {

  @AssertTrue(message = "结束时间必须晚于开始时间")
  public boolean isRangeValid() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }

  public RescheduleCommand toCommand() {
    return new RescheduleCommand(startAt, endAt);
  }
}
