package com.zjgsu.sfy.appointment.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预约状态机：SCHEDULED →(check-in) CHECKED_IN →(叫号) CALLED；SCHEDULED/CHECKED_IN →(取消) CANCELLED。 */
@Schema(description = "预约状态：SCHEDULED 已预约 / CHECKED_IN 已签到 / CALLED 已叫号 / CANCELLED 已取消")
public enum AppointmentStatus {
  SCHEDULED,
  CHECKED_IN,
  CALLED,
  CANCELLED
}
