package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.entity.Appointment;
import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID petId,
    UUID doctorId,
    Instant startAt,
    Instant endAt,
    String status,
    Instant checkedInAt,
    Instant calledAt) {

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
