package com.zjgsu.sfy.pharmacy.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryTest {

  private static final UUID DRUG_ID = UUID.randomUUID();

  @Test
  void initializeRejectsNegativeQuantity() {
    assertThatIllegalArgumentException().isThrownBy(() -> Inventory.initialize(DRUG_ID, -1));
  }

  @Test
  void adjustMovesQuantityAndRejectsZero() {
    Inventory inventory = Inventory.initialize(DRUG_ID, 5);

    inventory.adjust(3);
    assertThat(inventory.getQuantity()).isEqualTo(8);

    inventory.adjust(-8);
    assertThat(inventory.getQuantity()).isZero();

    assertThatIllegalArgumentException().isThrownBy(() -> inventory.adjust(0));
    assertThatIllegalArgumentException().isThrownBy(() -> inventory.adjust(-1));
  }

  @Test
  void lowStockAlertFiresOncePerLowPeriodAndRearmsAfterReplenishment() {
    Inventory inventory = Inventory.initialize(DRUG_ID, 5);

    assertThat(inventory.refreshLowStockAlert(3)).isFalse();

    inventory.adjust(-3);
    assertThat(inventory.getQuantity()).isEqualTo(2);
    assertThat(inventory.refreshLowStockAlert(3)).isTrue();
    assertThat(inventory.getLowStockNotifiedAt()).isNotNull();

    inventory.adjust(-1);
    assertThat(inventory.refreshLowStockAlert(3)).isFalse();

    inventory.adjust(5);
    assertThat(inventory.refreshLowStockAlert(3)).isFalse();
    assertThat(inventory.getLowStockNotifiedAt()).isNull();

    inventory.adjust(-6);
    assertThat(inventory.refreshLowStockAlert(3)).isTrue();
  }
}
