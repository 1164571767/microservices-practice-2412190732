package com.zjgsu.sfy.appointment.dto.resp;

import com.zjgsu.sfy.appointment.entity.Appointment;
import java.time.Instant;
import java.util.UUID;

public record QueueEntryResponse(
    int position,
    UUID appointmentId,
    UUID petId,
    UUID doctorId,
    Instant startAt,
    Instant checkedInAt) {

  public static QueueEntryResponse of(int position, Appointment appointment) {
    return new QueueEntryResponse(
        position,
        appointment.getId(),
        appointment.getPetId(),
        appointment.getDoctorId(),
        appointment.getStartAt(),
        appointment.getCheckedInAt());
  }
}
