package com.zjgsu.sfy.appointment.dto.req;

import com.zjgsu.sfy.appointment.service.command.RescheduleCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleRequest(@NotNull Instant startAt, @NotNull Instant endAt) {

  @AssertTrue(message = "结束时间必须晚于开始时间")
  public boolean isRangeValid() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }

  public RescheduleCommand toCommand() {
    return new RescheduleCommand(startAt, endAt);
  }
}
