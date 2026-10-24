package com.zjgsu.sfy.medical.dto.resp;

import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.service.result.MedicalRecordDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "门诊病历（含已开具的处方）")
public record MedicalRecordResponse(
    @Schema(description = "病历 ID", example = "9d1e3f5a-7b9c-4d1e-8f2a-3b4c5d6e7f80") UUID id,
    @Schema(description = "对应预约 ID", example = "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d")
        UUID appointmentId,
    @Schema(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "接诊医生 ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID doctorId,
    @Schema(description = "症状描述", example = "咳嗽、食欲下降") String symptoms,
    @Schema(description = "诊断结论", example = "急性支气管炎") String diagnosis,
    @Schema(description = "医嘱", example = "多休息、清淡饮食") String advice,
    @Schema(description = "就诊时间（UTC）", example = "2026-09-15T01:05:00Z") Instant visitedAt,
    @Schema(description = "创建时间（UTC）", example = "2026-09-15T01:05:00Z") Instant createdAt,
    @Schema(description = "该病历下的处方，未开处方时为空数组") List<PrescriptionResponse> prescriptions) {

  public static MedicalRecordResponse of(
      MedicalRecord record, List<PrescriptionResponse> prescriptions) {
    return new MedicalRecordResponse(
        record.getId(),
        record.getAppointmentId(),
        record.getPetId(),
        record.getDoctorId(),
        record.getSymptoms(),
        record.getDiagnosis(),
        record.getAdvice(),
        record.getVisitedAt(),
        record.getCreatedAt(),
        prescriptions);
  }

  public static MedicalRecordResponse from(MedicalRecordDetail detail) {
    return of(
        detail.record(), detail.prescriptions().stream().map(PrescriptionResponse::from).toList());
  }
}
