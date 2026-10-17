package com.zjgsu.sfy.medical.service.command;

import java.time.Instant;
import java.util.UUID;

public record CreateMedicalRecordCommand(
    UUID appointmentId, String symptoms, String diagnosis, String advice, Instant visitedAt) {}
