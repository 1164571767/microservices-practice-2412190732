package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.owner.entity.Vaccination;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VaccinationResponse(
    UUID id,
    UUID petId,
    String vaccineName,
    LocalDate administeredOn,
    LocalDate nextDueOn,
    String note,
    Instant createdAt) {

  public static VaccinationResponse from(Vaccination vaccination) {
    return new VaccinationResponse(
        vaccination.getId(),
        vaccination.getPetId(),
        vaccination.getVaccineName(),
        vaccination.getAdministeredOn(),
        vaccination.getNextDueOn(),
        vaccination.getNote(),
        vaccination.getCreatedAt());
  }
}
