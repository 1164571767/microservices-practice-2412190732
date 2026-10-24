package com.zjgsu.sfy.medical.dto.resp;

import com.zjgsu.sfy.medical.service.result.PrescriptionDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "处方（含用药明细）")
public record PrescriptionResponse(
    @Schema(description = "处方 ID", example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e") UUID id,
    @Schema(description = "所属病历 ID", example = "9d1e3f5a-7b9c-4d1e-8f2a-3b4c5d6e7f80")
        UUID medicalRecordId,
    @Schema(description = "开方医生 ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID doctorId,
    @Schema(description = "开方时间（UTC）", example = "2026-09-15T01:20:00Z") Instant issuedAt,
    @Schema(description = "用药明细") List<PrescriptionLineResponse> items) {

  public static PrescriptionResponse from(PrescriptionDetail detail) {
    return new PrescriptionResponse(
        detail.prescription().getId(),
        detail.prescription().getMedicalRecordId(),
        detail.prescription().getDoctorId(),
        detail.prescription().getIssuedAt(),
        detail.items().stream().map(PrescriptionLineResponse::from).toList());
  }
}
