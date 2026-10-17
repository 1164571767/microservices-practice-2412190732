package com.zjgsu.sfy.appointment.service.command;

import java.time.Instant;
import java.util.UUID;

public record CreateScheduleCommand(
    UUID doctorId, Instant startAt, Instant endAt, int slotMinutes) {}
