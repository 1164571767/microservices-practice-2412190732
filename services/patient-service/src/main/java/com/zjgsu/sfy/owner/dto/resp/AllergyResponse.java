package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Allergy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "过敏记录")
public record AllergyResponse(
    @Schema(description = "过敏记录 ID", example = "5c7d9e1f-2a3b-4c5d-8e6f-7a8b9c0d1e23") UUID id,
    @Schema(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "过敏原名称", example = "花粉") String allergen,
    @Schema(description = "严重程度：MILD 轻微 / MODERATE 中度 / SEVERE 严重", example = "MODERATE")
        String severity,
    @Schema(description = "备注", example = "接触后出现皮疹") String note,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    UUID recordedBy,
    String recordedByName,
    UUID supersedesId,
    String correctionReason) {

  public static AllergyResponse from(Allergy allergy) {
    return new AllergyResponse(
        allergy.getId(),
        allergy.getPetId(),
        allergy.getAllergen(),
        allergy.getSeverity().name(),
        allergy.getNote(),
        allergy.getCreatedAt(),
        allergy.getRecordedBy(),
        allergy.getRecordedByName(),
        allergy.getSupersedesId(),
        allergy.getCorrectionReason());
  }
}
