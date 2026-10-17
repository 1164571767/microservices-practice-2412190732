package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import java.time.Instant;
import java.util.UUID;

public record ScheduleResponse(
    UUID id, UUID doctorId, Instant startAt, Instant endAt, int slotMinutes) {

  public static ScheduleResponse from(DoctorSchedule schedule) {
    return new ScheduleResponse(
        schedule.getId(),
        schedule.getDoctorId(),
        schedule.getStartAt(),
        schedule.getEndAt(),
        schedule.getSlotMinutes());
  }
}
