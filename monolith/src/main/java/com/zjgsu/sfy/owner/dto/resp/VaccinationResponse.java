package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Vaccination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "疫苗接种记录")
public record VaccinationResponse(
    @Schema(description = "疫苗记录 ID", example = "8b3c5d7e-9f10-4a2b-8c3d-4e5f6a7b8c90") UUID id,
    @Schema(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "疫苗名称", example = "狂犬疫苗") String vaccineName,
    @Schema(description = "接种日期", example = "2026-01-01") LocalDate administeredOn,
    @Schema(description = "下次应接种日期", example = "2027-01-01") LocalDate nextDueOn,
    @Schema(description = "备注", example = "无不良反应") String note,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    UUID recordedBy,
    String recordedByName,
    UUID supersedesId,
    String correctionReason) {

  public static VaccinationResponse from(Vaccination vaccination) {
    return new VaccinationResponse(
        vaccination.getId(),
        vaccination.getPetId(),
        vaccination.getVaccineName(),
        vaccination.getAdministeredOn(),
        vaccination.getNextDueOn(),
        vaccination.getNote(),
        vaccination.getCreatedAt(),
        vaccination.getRecordedBy(),
        vaccination.getRecordedByName(),
        vaccination.getSupersedesId(),
        vaccination.getCorrectionReason());
  }
}
