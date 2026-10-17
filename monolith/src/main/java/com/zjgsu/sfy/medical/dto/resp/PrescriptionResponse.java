package com.zjgsu.sfy.medical.dto.resp;

import com.zjgsu.sfy.medical.service.result.PrescriptionDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
    UUID id,
    UUID medicalRecordId,
    UUID doctorId,
    Instant issuedAt,
    List<PrescriptionLineResponse> items) {

  public static PrescriptionResponse from(PrescriptionDetail detail) {
    return new PrescriptionResponse(
        detail.prescription().getId(),
        detail.prescription().getMedicalRecordId(),
        detail.prescription().getDoctorId(),
        detail.prescription().getIssuedAt(),
        detail.items().stream().map(PrescriptionLineResponse::from).toList());
  }
}
