package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Allergy;
import java.time.Instant;
import java.util.UUID;

public record AllergyResponse(
    UUID id, UUID petId, String allergen, String severity, String note, Instant createdAt) {

  public static AllergyResponse from(Allergy allergy) {
    return new AllergyResponse(
        allergy.getId(),
        allergy.getPetId(),
        allergy.getAllergen(),
        allergy.getSeverity().name(),
        allergy.getNote(),
        allergy.getCreatedAt());
  }
}
