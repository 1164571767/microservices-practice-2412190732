package com.zjgsu.sfy.appointment.dto.req;

import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "创建预约的请求体")
public record BookAppointmentRequest(
    @Schema(description = "就诊宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") @NotNull
        UUID petId,
    @Schema(description = "接诊医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d") @NotNull
        UUID doctorId,
    @Schema(description = "预约开始时间（UTC ISO-8601），需落在医生排班内", example = "2026-11-10T01:00:00Z")
        @NotNull
        Instant startAt,
    @Schema(description = "预约结束时间（UTC ISO-8601），必须晚于开始时间", example = "2026-11-10T01:30:00Z")
        @NotNull
        Instant endAt) {

  @AssertTrue(message = "结束时间必须晚于开始时间")
  public boolean isRangeValid() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }

  public BookAppointmentCommand toCommand() {
    return new BookAppointmentCommand(petId, doctorId, startAt, endAt);
  }
}
