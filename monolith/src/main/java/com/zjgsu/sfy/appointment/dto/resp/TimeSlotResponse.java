package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.service.result.TimeSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "一个可预约的时段")
public record TimeSlotResponse(
    @Schema(description = "时段开始时间（UTC）", example = "2026-11-10T01:00:00Z") Instant startAt,
    @Schema(description = "时段结束时间（UTC）", example = "2026-11-10T01:30:00Z") Instant endAt) {

  public static TimeSlotResponse from(TimeSlot slot) {
    return new TimeSlotResponse(slot.startAt(), slot.endAt());
  }
}
