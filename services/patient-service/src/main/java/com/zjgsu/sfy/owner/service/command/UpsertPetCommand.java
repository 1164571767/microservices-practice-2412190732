package com.zjgsu.sfy.owner.service.command;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertPetCommand(
    String name,
    String species,
    LocalDate birthDate,
    BigDecimal weightKg,
    Boolean birthDateEstimated) {
  public UpsertPetCommand(String name, String species, LocalDate birthDate, BigDecimal weightKg) {
    this(name, species, birthDate, weightKg, null);
  }
}
