package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Pet;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PetResponse(
    UUID id,
    UUID ownerId,
    String name,
    String species,
    LocalDate birthDate,
    BigDecimal weightKg,
    Instant createdAt,
    Instant updatedAt) {

  public static PetResponse from(Pet pet) {
    return new PetResponse(
        pet.getId(),
        pet.getOwnerId(),
        pet.getName(),
        pet.getSpecies(),
        pet.getBirthDate(),
        pet.getWeightKg(),
        pet.getCreatedAt(),
        pet.getUpdatedAt());
  }
}
