package com.zjgsu.sfy.pharmacy.dto.resp;

import com.zjgsu.sfy.owner.PatientContactSummary;
import java.time.Instant;
import java.util.UUID;

public record PrescriptionTaskResponse(
    UUID prescriptionId,
    UUID petId,
    Instant issuedAt,
    PatientContactSummary patient,
    String doctorName,
    boolean dispensed) {}
