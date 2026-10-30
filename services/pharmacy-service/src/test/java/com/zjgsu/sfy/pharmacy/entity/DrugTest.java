package com.zjgsu.sfy.pharmacy.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.zjgsu.sfy.pharmacy.common.DrugStatus;
import org.junit.jupiter.api.Test;

class DrugTest {

  @Test
  void createTrimsTextAndStartsActive() {
    Drug drug = Drug.create("  阿莫西林 ", " 0.5g ", " 盒 ", 5);

    assertThat(drug.getName()).isEqualTo("阿莫西林");
    assertThat(drug.getSpecification()).isEqualTo("0.5g");
    assertThat(drug.getUnit()).isEqualTo("盒");
    assertThat(drug.getLowStockThreshold()).isEqualTo(5);
    assertThat(drug.getStatus()).isEqualTo(DrugStatus.ACTIVE);
    assertThat(drug.isActive()).isTrue();
  }

  @Test
  void blankFieldsAreRejected() {
    assertThatIllegalArgumentException().isThrownBy(() -> Drug.create(" ", "0.5g", "盒", 0));
    assertThatIllegalArgumentException().isThrownBy(() -> Drug.create("药", " ", "盒", 0));
    assertThatIllegalArgumentException().isThrownBy(() -> Drug.create("药", "0.5g", " ", 0));
  }

  @Test
  void negativeThresholdIsRejected() {
    assertThatIllegalArgumentException().isThrownBy(() -> Drug.create("药", "0.5g", "盒", -1));
  }

  @Test
  void updateDetailsCanDeactivateAndChangeThreshold() {
    Drug drug = Drug.create("药", "0.5g", "盒", 5);

    drug.updateDetails("新药", "1g", "瓶", 0, false);

    assertThat(drug.getName()).isEqualTo("新药");
    assertThat(drug.getSpecification()).isEqualTo("1g");
    assertThat(drug.getUnit()).isEqualTo("瓶");
    assertThat(drug.getLowStockThreshold()).isZero();
    assertThat(drug.getStatus()).isEqualTo(DrugStatus.INACTIVE);
    assertThat(drug.isActive()).isFalse();
  }

  @Test
  void updateRejectsNegativeThreshold() {
    Drug drug = Drug.create("药", "0.5g", "盒", 5);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> drug.updateDetails("药", "0.5g", "盒", -1, true));
  }
}
