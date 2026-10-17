package com.zjgsu.sfy.appointment.dto.req;

import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record BookAppointmentRequest(
    @NotNull UUID petId, @NotNull UUID doctorId, @NotNull Instant startAt, @NotNull Instant endAt) {

  @AssertTrue(message = "结束时间必须晚于开始时间")
  public boolean isRangeValid() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }

  public BookAppointmentCommand toCommand() {
    return new BookAppointmentCommand(petId, doctorId, startAt, endAt);
  }
}
