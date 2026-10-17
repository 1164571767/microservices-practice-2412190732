package com.zjgsu.sfy.owner.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PetTest {

  @Test
  void createNormalizesWeightToTwoDecimals() {
    Pet pet =
        Pet.create(
            UUID.randomUUID(), " 旺财 ", " 犬 ", LocalDate.of(2020, 1, 1), new BigDecimal("5.5"));

    assertThat(pet.getName()).isEqualTo("旺财");
    assertThat(pet.getSpecies()).isEqualTo("犬");
    assertThat(pet.getWeightKg()).isEqualByComparingTo("5.50");
    assertThat(pet.getWeightKg().scale()).isEqualTo(2);
  }

  @Test
  void createWithFutureBirthDateIsRejected() {
    assertThatThrownBy(
            () ->
                Pet.create(
                    UUID.randomUUID(), "旺财", "犬", LocalDate.now().plusDays(1), BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createWithNonPositiveWeightIsRejected() {
    assertThatThrownBy(
            () ->
                Pet.create(
                    UUID.randomUUID(), "旺财", "犬", LocalDate.now().minusYears(1), BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createWithBlankNameOrSpeciesIsRejected() {
    assertThatThrownBy(
            () ->
                Pet.create(
                    UUID.randomUUID(), " ", "犬", LocalDate.now().minusYears(1), BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Pet.create(
                    UUID.randomUUID(), "旺财", " ", LocalDate.now().minusYears(1), BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isOwnedByComparesOwnerId() {
    UUID ownerId = UUID.randomUUID();
    Pet pet = Pet.create(ownerId, "旺财", "犬", LocalDate.now().minusYears(1), new BigDecimal("3.20"));

    assertThat(pet.isOwnedBy(ownerId)).isTrue();
    assertThat(pet.isOwnedBy(UUID.randomUUID())).isFalse();
  }
}
