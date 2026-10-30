package com.zjgsu.sfy.appointment.service.command;

import java.time.Instant;
import java.util.UUID;

public record BookAppointmentCommand(UUID petId, UUID doctorId, Instant startAt, Instant endAt) {}
