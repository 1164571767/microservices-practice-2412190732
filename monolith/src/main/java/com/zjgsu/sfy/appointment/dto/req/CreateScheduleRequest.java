package com.zjgsu.sfy.appointment.dto.req;

import com.zjgsu.sfy.appointment.service.command.CreateScheduleCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateScheduleRequest(
    @NotNull UUID doctorId,
    @NotNull Instant startAt,
    @NotNull Instant endAt,
    @Min(1) @Max(480) int slotMinutes) {

  @AssertTrue(message = "结束时间必须晚于开始时间")
  public boolean isRangeValid() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }

  public CreateScheduleCommand toCommand() {
    return new CreateScheduleCommand(doctorId, startAt, endAt, slotMinutes);
  }
}
