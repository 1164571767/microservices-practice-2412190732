package com.zjgsu.sfy.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.medical.MedicalRecordCreated;
import com.zjgsu.sfy.reporting.repository.RevenueFactRepository;
import com.zjgsu.sfy.reporting.repository.VisitFactRepository;
import com.zjgsu.sfy.reporting.service.ReportFactInserter;
import com.zjgsu.sfy.reporting.support.ReportingIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

class ReportingDependencyFailureTest extends ReportingIntegrationTest {
  @MockitoSpyBean IdentityApi identity;
  @MockitoSpyBean VisitFactRepository visitFacts;
  @MockitoSpyBean ReportFactInserter inserter;
  @Autowired RevenueFactRepository revenueFacts;
  @Autowired ApplicationEventPublisher publisher;

  @Test
  void identityLookupFailureDoesNotMasqueradeAsEmptyVisitReport() throws Exception {
    var admin = seedAdmin();
    var doctor = seedDoctor();
    publisher.publishEvent(
        new MedicalRecordCreated(
            UUID.randomUUID(), UUID.randomUUID(), doctor.getId(), Instant.now(), Instant.now()));
    IdentityApi target = AopTestUtils.getUltimateTargetObject(identity);
    doThrow(new IllegalStateException("injected identity failure"))
        .when(target)
        .findUser(doctor.getId());
    mockMvc
        .perform(
            get("/api/reporting/visits?from="
                    + today()
                    + "&to="
                    + today()
                    + "&doctorId="
                    + doctor.getId())
                .header("Authorization", bearer(admin)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.totalVisits").doesNotExist());
    // Independent report types remain readable when doctor-name lookup fails.
    revenueOf(admin, today(), today());
  }

  @Test
  void aggregationFailureReturnsErrorInsteadOfZero() throws Exception {
    var admin = seedAdmin();
    doThrow(new IllegalStateException("injected reporting storage failure"))
        .when(visitFacts)
        .countDaily(any(), any());
    mockMvc
        .perform(
            get("/api/reporting/visits?from=" + today() + "&to=" + today())
                .header("Authorization", bearer(admin)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.daily").doesNotExist());
  }

  @Test
  void failedRevenueProjectionCannotUndoPaymentOrRefund() throws Exception {
    var staff = seedStaff();
    var record = seedRecordFor(seedPetWithOwner(), seedDoctor());
    var billId = createBillFor(staff, record.getId(), seedChargeItem("83.25"), 1);
    ReportFactInserter target = AopTestUtils.getUltimateTargetObject(inserter);
    doThrow(new IllegalStateException("injected reporting write failure"))
        .when(target)
        .insertRevenue(any());
    payBill(staff, billId);
    mockMvc
        .perform(get("/api/billing/bills/" + billId).header("Authorization", bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
    refundBill(staff, billId, "报表投影失败隔离验证");
    mockMvc
        .perform(get("/api/billing/bills/" + billId).header("Authorization", bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REFUNDED"));
    assertThat(
            revenueFacts.existsByBillIdAndKind(
                billId, com.zjgsu.sfy.reporting.common.RevenueKind.PAID))
        .isFalse();
    assertThat(
            revenueFacts.existsByBillIdAndKind(
                billId, com.zjgsu.sfy.reporting.common.RevenueKind.REFUNDED))
        .isFalse();
  }
}
