package com.zjgsu.sfy.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.billing.support.BillingIntegrationTest;
import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.owner.OwnerApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class BillingDependencyFailureTest extends BillingIntegrationTest {
  @MockitoSpyBean MedicalApi medical;
  @MockitoSpyBean OwnerApi owner;

  @Test
  void medicalFailureRollsBackBillAndAudit() throws Exception {
    var staff = seedStaff();
    var doctor = seedDoctor();
    var pet = seedPetWithOwner();
    var record = seedRecordFor(pet, doctor);
    var item = seedChargeItem("20.00");
    MedicalApi target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(medical);
    doThrow(new IllegalStateException("injected medical failure"))
        .when(target)
        .findRecord(record.getId());
    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(record.getId(), null, List.of(line(item.getId(), 1)))))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    assertThat(billRepository.countByMedicalRecordId(record.getId())).isZero();
  }

  @Test
  void contactFailureIsAnErrorNotAnEmptyBillList() throws Exception {
    var staff = seedStaff();
    var doctor = seedDoctor();
    var pet = seedPetWithOwner();
    var record = seedRecordFor(pet, doctor);
    var item = seedChargeItem("20.00");
    createBill(staff, billBody(record.getId(), null, List.of(line(item.getId(), 1))), null);
    OwnerApi target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(owner);
    doThrow(new IllegalStateException("injected contact failure"))
        .when(target)
        .patientContacts(anyCollection());
    mockMvc
        .perform(
            get("/api/billing/workbench/bills")
                .param("petId", pet.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.items").doesNotExist());
    assertThat(billRepository.countByMedicalRecordId(record.getId())).isEqualTo(1);
  }
}
