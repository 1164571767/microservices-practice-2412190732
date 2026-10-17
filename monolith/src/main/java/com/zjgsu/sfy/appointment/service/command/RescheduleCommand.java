package com.zjgsu.sfy.appointment.service.command;

import java.time.Instant;

public record RescheduleCommand(Instant startAt, Instant endAt) {}
