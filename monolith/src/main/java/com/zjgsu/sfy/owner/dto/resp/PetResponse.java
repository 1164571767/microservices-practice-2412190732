package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Pet;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "宠物档案")
public record PetResponse(
    @Schema(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID id,
    @Schema(description = "所属主人档案 ID", example = "2d7e9a41-3b6c-4f82-9d10-8a5c7e3b1f04")
        UUID ownerId,
    @Schema(description = "宠物名字", example = "旺财") String name,
    @Schema(description = "物种", example = "犬") String species,
    @Schema(description = "出生日期", example = "2022-05-01") LocalDate birthDate,
    @Schema(description = "体重，单位千克", example = "5.50") BigDecimal weightKg,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    @Schema(description = "最后更新时间（UTC）", example = "2026-10-20T01:05:00Z") Instant updatedAt,
    long version,
    boolean archived,
    Instant archivedAt,
    UUID archivedBy,
    String archiveReason,
    boolean birthDateEstimated) {

  public static PetResponse from(Pet pet) {
    return new PetResponse(
        pet.getId(),
        pet.getOwnerId(),
        pet.getName(),
        pet.getSpecies(),
        pet.getBirthDate(),
        pet.getWeightKg(),
        pet.getCreatedAt(),
        pet.getUpdatedAt(),
        pet.getVersion(),
        pet.isArchived(),
        pet.getArchivedAt(),
        pet.getArchivedBy(),
        pet.getArchiveReason(),
        pet.isBirthDateEstimated());
  }
}
