package com.zjgsu.sfy.appointment.common;

/** 预约状态机：SCHEDULED →(check-in) CHECKED_IN →(叫号) CALLED；SCHEDULED/CHECKED_IN →(取消) CANCELLED。 */
public enum AppointmentStatus {
  SCHEDULED,
  CHECKED_IN,
  CALLED,
  CANCELLED
}
