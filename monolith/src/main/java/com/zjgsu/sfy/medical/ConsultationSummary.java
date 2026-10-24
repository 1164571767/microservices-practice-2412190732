package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.UUID;

/** Minimal reception handoff state; never includes draft or clinical text. */
public record ConsultationSummary(
    UUID appointmentId, UUID recordId, boolean hasDraft, Instant completedAt) {}
