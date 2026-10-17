package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.service.result.TimeSlot;
import java.time.Instant;

public record TimeSlotResponse(Instant startAt, Instant endAt) {

  public static TimeSlotResponse from(TimeSlot slot) {
    return new TimeSlotResponse(slot.startAt(), slot.endAt());
  }
}
