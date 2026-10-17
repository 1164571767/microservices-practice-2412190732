package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.medical.MedicalRecordSummary;
import java.time.Instant;
import java.util.UUID;

public record MedicalRecordSummaryResponse(
    UUID recordId, UUID petId, Instant visitedAt, String diagnosis, UUID doctorId) {

  public static MedicalRecordSummaryResponse from(MedicalRecordSummary summary) {
    return new MedicalRecordSummaryResponse(
        summary.recordId(),
        summary.petId(),
        summary.visitedAt(),
        summary.diagnosis(),
        summary.doctorId());
  }
}
