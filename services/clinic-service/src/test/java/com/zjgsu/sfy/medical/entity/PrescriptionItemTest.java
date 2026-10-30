package com.zjgsu.sfy.medical.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrescriptionItemTest {

  private static PrescriptionItem createItem(int quantity, int days) {
    return PrescriptionItem.create(
        UUID.randomUUID(), "阿莫西林", "0.5g", "1片", "每日2次", quantity, days, null);
  }

  @Test
  void createRejectsNonPositiveQuantityOrDays() {
    assertThatThrownBy(() -> createItem(0, 5)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> createItem(3, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsBlankRequiredText() {
    assertThatThrownBy(
            () ->
                PrescriptionItem.create(UUID.randomUUID(), "  ", "0.5g", "1片", "每日2次", 3, 5, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createNormalizesOptionalFields() {
    PrescriptionItem item =
        PrescriptionItem.create(UUID.randomUUID(), " 阿莫西林 ", " ", " 1片 ", "每日2次", 3, 5, "  ");

    assertThat(item.getDrugName()).isEqualTo("阿莫西林");
    assertThat(item.getSpecification()).isNull();
    assertThat(item.getDosage()).isEqualTo("1片");
    assertThat(item.getNote()).isNull();
  }
}
