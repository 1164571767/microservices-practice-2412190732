package com.zjgsu.sfy.appointment.service.result;

import java.time.Instant;
import java.util.UUID;

public record ScheduleOperation(
    UUID id, UUID actorId, String actorName, String action, String reason, Instant occurredAt) {}
