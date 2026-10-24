package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.entity.Appointment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "候诊队列中的一条记录")
public record QueueEntryResponse(
    @Schema(description = "排队序号，从 1 开始，1 号为下一位", example = "1") int position,
    @Schema(description = "预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d")
        UUID appointmentId,
    @Schema(description = "就诊宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "接诊医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID doctorId,
    @Schema(description = "预约开始时间（UTC）", example = "2026-11-10T01:00:00Z") Instant startAt,
    @Schema(description = "到院签到时间（UTC）", example = "2026-11-10T01:02:00Z") Instant checkedInAt,
    @Schema(description = "接待联系摘要，历史档案缺失时为空") com.zjgsu.sfy.owner.PatientContactSummary patient) {

  public static QueueEntryResponse of(
      int position, Appointment appointment, com.zjgsu.sfy.owner.PatientContactSummary patient) {
    return new QueueEntryResponse(
        position,
        appointment.getId(),
        appointment.getPetId(),
        appointment.getDoctorId(),
        appointment.getStartAt(),
        appointment.getCheckedInAt(),
        patient);
  }
}
