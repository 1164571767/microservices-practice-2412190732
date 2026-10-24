package com.zjgsu.sfy.billing.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ChargeItemTest {

  @Test
  void createTrimsNameAndNormalizesAmount() {
    ChargeItem item = ChargeItem.create(" 诊查费 ", new BigDecimal("50"));

    assertThat(item.getName()).isEqualTo("诊查费");
    assertThat(item.getAmount()).isEqualByComparingTo("50.00");
    assertThat(item.getAmount().scale()).isEqualTo(2);
    assertThat(item.isActive()).isTrue();
  }

  @Test
  void zeroAmountIsAllowed() {
    ChargeItem item = ChargeItem.create("公益诊疗", BigDecimal.ZERO);

    assertThat(item.getAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void negativeAmountIsRejected() {
    assertThatThrownBy(() -> ChargeItem.create("诊查费", new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  @Test
  void moreThanTwoFractionDigitsIsRejected() {
    assertThatThrownBy(() -> ChargeItem.create("诊查费", new BigDecimal("1.234")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fraction");
  }

  @Test
  void blankNameIsRejected() {
    assertThatThrownBy(() -> ChargeItem.create("  ", BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void updateCanChangeAmountAndDeactivate() {
    ChargeItem item = ChargeItem.create("诊查费", new BigDecimal("50.00"));

    item.update("复诊费", new BigDecimal("30.00"), false);

    assertThat(item.getName()).isEqualTo("复诊费");
    assertThat(item.getAmount()).isEqualByComparingTo("30.00");
    assertThat(item.isActive()).isFalse();
  }
}
