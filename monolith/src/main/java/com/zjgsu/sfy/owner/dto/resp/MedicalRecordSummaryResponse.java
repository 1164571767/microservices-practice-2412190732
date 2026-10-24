package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.medical.MedicalRecordSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "病历摘要（只读索引，详情在门诊模块）")
public record MedicalRecordSummaryResponse(
    @Schema(description = "病历 ID", example = "9d1e3f5a-7b9c-4d1e-8f2a-3b4c5d6e7f80") UUID recordId,
    @Schema(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "就诊时间（UTC）", example = "2026-10-18T02:30:00Z") Instant visitedAt,
    @Schema(description = "诊断结论", example = "急性肠胃炎") String diagnosis,
    @Schema(description = "接诊医生 ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID doctorId) {

  public static MedicalRecordSummaryResponse from(MedicalRecordSummary summary) {
    return new MedicalRecordSummaryResponse(
        summary.recordId(),
        summary.petId(),
        summary.visitedAt(),
        summary.diagnosis(),
        summary.doctorId());
  }
}
