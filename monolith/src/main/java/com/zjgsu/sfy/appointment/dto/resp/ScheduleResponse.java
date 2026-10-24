package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.common.ScheduleState;
import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "医生的排班时段")
public record ScheduleResponse(
    @Schema(description = "排班 ID", example = "7e8f9a0b-1c2d-4e3f-8a5b-6c7d8e9f0a1b") UUID id,
    @Schema(description = "医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID doctorId,
    @Schema(description = "排班开始时间（UTC）", example = "2026-11-10T00:00:00Z") Instant startAt,
    @Schema(description = "排班结束时间（UTC）", example = "2026-11-10T04:00:00Z") Instant endAt,
    @Schema(description = "每个号源的时长（分钟）", example = "30") int slotMinutes,
    ScheduleState state,
    long version) {

  public static ScheduleResponse from(DoctorSchedule schedule) {
    return new ScheduleResponse(
        schedule.getId(),
        schedule.getDoctorId(),
        schedule.getStartAt(),
        schedule.getEndAt(),
        schedule.getSlotMinutes(),
        schedule.getState(),
        schedule.getVersion());
  }
}
