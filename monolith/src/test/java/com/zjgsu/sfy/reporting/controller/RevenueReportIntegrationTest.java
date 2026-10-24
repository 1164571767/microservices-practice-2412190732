package com.zjgsu.sfy.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.sfy.billing.BillPaid;
import com.zjgsu.sfy.billing.BillRefunded;
import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.reporting.common.RevenueKind;
import com.zjgsu.sfy.reporting.dto.resp.RevenueReportResponse;
import com.zjgsu.sfy.reporting.repository.RevenueFactRepository;
import com.zjgsu.sfy.reporting.support.ReportingIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class RevenueReportIntegrationTest extends ReportingIntegrationTest {

  @Autowired ApplicationEvents events;
  @Autowired ApplicationEventPublisher eventPublisher;
  @Autowired RevenueFactRepository revenueFactRepository;

  @Test
  void paidAndRefundedBillsAreProjectedIntoRevenue() throws Exception {
    User admin = seedAdmin();
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("50.00");
    UUID billId = createBillFor(staff, record.getId(), item, 2);
    LocalDate date = today();

    RevenueReportResponse before = revenueOf(admin, date, date);
    payBill(staff, billId);
    RevenueReportResponse paid = revenueOf(admin, date, date);

    assertThat(paid.paidAmount().subtract(before.paidAmount())).isEqualByComparingTo("100.00");
    assertThat(paid.paidCount()).isEqualTo(before.paidCount() + 1);
    assertThat(paid.netAmount().subtract(before.netAmount())).isEqualByComparingTo("100.00");
    assertThat(paid.daily()).anySatisfy(day -> assertThat(day.date()).isEqualTo(date));
    assertThat(revenueFactRepository.existsByBillIdAndKind(billId, RevenueKind.PAID)).isTrue();

    refundBill(staff, billId, "主人取消服务");
    RevenueReportResponse refunded = revenueOf(admin, date, date);

    assertThat(refunded.refundedAmount().subtract(before.refundedAmount()))
        .isEqualByComparingTo("100.00");
    assertThat(refunded.refundedCount()).isEqualTo(before.refundedCount() + 1);
    assertThat(refunded.netAmount()).isEqualByComparingTo(before.netAmount());
    assertThat(revenueFactRepository.existsByBillIdAndKind(billId, RevenueKind.REFUNDED)).isTrue();
    assertThat(events.stream(BillPaid.class)).hasSize(1);
    assertThat(events.stream(BillRefunded.class)).hasSize(1);
  }

  @Test
  void duplicateBillPaidDeliveryIsIgnored() throws Exception {
    User admin = seedAdmin();
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("30.00");
    UUID billId = createBillFor(staff, record.getId(), item, 1);
    payBill(staff, billId);
    BillPaid event = events.stream(BillPaid.class).findFirst().orElseThrow();
    LocalDate date = today();
    RevenueReportResponse before = revenueOf(admin, date, date);

    eventPublisher.publishEvent(event);

    RevenueReportResponse after = revenueOf(admin, date, date);
    assertThat(after.paidAmount()).isEqualByComparingTo(before.paidAmount());
    assertThat(after.paidCount()).isEqualTo(before.paidCount());
    assertThat(
            revenueFactRepository.findAll().stream()
                .filter(fact -> fact.getBillId().equals(billId))
                .filter(fact -> fact.getKind() == RevenueKind.PAID))
        .hasSize(1);
  }

  @Test
  void emptyRangeReturnsZeroRevenue() throws Exception {
    User admin = seedAdmin();

    RevenueReportResponse report =
        revenueOf(admin, LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 2));

    assertThat(report.currency()).isEqualTo("CNY");
    assertThat(report.paidAmount()).isEqualByComparingTo("0");
    assertThat(report.refundedAmount()).isEqualByComparingTo("0");
    assertThat(report.netAmount()).isEqualByComparingTo("0");
    assertThat(report.paidCount()).isZero();
    assertThat(report.refundedCount()).isZero();
    assertThat(report.daily()).isEmpty();
  }
}
