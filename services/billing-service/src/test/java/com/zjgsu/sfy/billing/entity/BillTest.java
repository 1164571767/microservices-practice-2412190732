package com.zjgsu.sfy.billing.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.billing.common.BillStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillTest {

  private static final UUID PET_ID = UUID.randomUUID();
  private static final UUID RECORD_ID = UUID.randomUUID();
  private static final UUID CREATED_BY = UUID.randomUUID();

  @Test
  void createComputesTotalFromLineAmounts() {
    Bill bill =
        Bill.create(
            PET_ID, RECORD_ID, null, CREATED_BY, List.of(line("50.00", 1), line("12.50", 2)));

    assertThat(bill.getStatus()).isEqualTo(BillStatus.UNPAID);
    assertThat(bill.getCurrency()).isEqualTo("CNY");
    assertThat(bill.getTotalAmount()).isEqualByComparingTo("75.00");
    assertThat(bill.getTotalAmount().scale()).isEqualTo(2);
    assertThat(bill.getPetId()).isEqualTo(PET_ID);
    assertThat(bill.getMedicalRecordId()).isEqualTo(RECORD_ID);
    assertThat(bill.getPrescriptionId()).isNull();
    assertThat(bill.getCreatedBy()).isEqualTo(CREATED_BY);
  }

  @Test
  void emptyLinesAreRejected() {
    assertThatThrownBy(() -> Bill.create(PET_ID, RECORD_ID, null, CREATED_BY, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("line");
  }

  @Test
  void lineAmountIsUnitPriceTimesQuantity() {
    BillLine line = line("12.50", 3);

    assertThat(line.amount()).isEqualByComparingTo("37.50");
    assertThat(line.amount().scale()).isEqualTo(2);
  }

  @Test
  void lineQuantityMustBePositive() {
    assertThatThrownBy(() -> line("12.50", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quantity");
  }

  @Test
  void paidBillCannotBePaidTwice() {
    Bill bill = newBill();
    bill.markPaid();

    assertThat(bill.getStatus()).isEqualTo(BillStatus.PAID);
    assertThatThrownBy(bill::markPaid).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyPaidBillCanBeRefunded() {
    Bill bill = newBill();
    assertThatThrownBy(bill::markRefunded).isInstanceOf(IllegalStateException.class);

    bill.markPaid();
    bill.markRefunded();

    assertThat(bill.getStatus()).isEqualTo(BillStatus.REFUNDED);
    assertThatThrownBy(bill::markRefunded).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyUnpaidBillCanBeVoided() {
    Bill bill = newBill();
    bill.markVoided();

    assertThat(bill.getStatus()).isEqualTo(BillStatus.VOID);
    assertThatThrownBy(bill::markVoided).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refundedBillCannotBePaidAgain() {
    Bill bill = newBill();
    bill.markPaid();
    bill.markRefunded();

    assertThatThrownBy(bill::markPaid).isInstanceOf(IllegalStateException.class);
  }

  private static Bill newBill() {
    return Bill.create(PET_ID, RECORD_ID, null, CREATED_BY, List.of(line("50.00", 1)));
  }

  private static BillLine line(String unitPrice, int quantity) {
    return new BillLine(UUID.randomUUID(), "诊查费", new BigDecimal(unitPrice), quantity);
  }
}
