package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.entity.Appointment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "预约信息")
public record AppointmentResponse(
    @Schema(description = "预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d") UUID id,
    @Schema(description = "就诊宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "接诊医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID doctorId,
    @Schema(description = "预约开始时间（UTC）", example = "2026-11-10T01:00:00Z") Instant startAt,
    @Schema(description = "预约结束时间（UTC）", example = "2026-11-10T01:30:00Z") Instant endAt,
    @Schema(
            description = "预约状态：SCHEDULED 已预约 / CHECKED_IN 已签到 / CALLED 已叫号 / CANCELLED 已取消",
            example = "SCHEDULED")
        String status,
    @Schema(description = "到院签到时间（UTC），未签到为空", example = "2026-11-10T01:02:00Z")
        Instant checkedInAt,
    @Schema(description = "被叫号时间（UTC），未叫号为空", example = "2026-11-10T01:20:00Z") Instant calledAt) {

  public static AppointmentResponse from(Appointment appointment) {
    return new AppointmentResponse(
        appointment.getId(),
        appointment.getPetId(),
        appointment.getDoctorId(),
        appointment.getStartAt(),
        appointment.getEndAt(),
        appointment.getStatus().name(),
        appointment.getCheckedInAt(),
        appointment.getCalledAt());
  }
}
