package com.zjgsu.sfy.pharmacy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.owner.OwnerApi;
import com.zjgsu.sfy.pharmacy.support.PharmacyIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

class PharmacyDependencyFailureTest extends PharmacyIntegrationTest {
  @MockitoSpyBean MedicalApi medical;
  @MockitoSpyBean OwnerApi owner;
  @MockitoSpyBean com.zjgsu.sfy.notification.service.NotificationWriter notification;
  @MockitoSpyBean com.zjgsu.sfy.reporting.service.ReportFactInserter reporting;

  @Test
  void downstreamNotificationAndReportingFailuresDoNotChangeConfirmedDispense() throws Exception {
    var drug = seedDrug("下游故障药品", DEFAULT_SPEC, 8, 10);
    var p = seedPrescriptionFor(seedDoctor(), seedPetWithOwner(), drug.getName(), 3);
    com.zjgsu.sfy.notification.service.NotificationWriter n =
        AopTestUtils.getUltimateTargetObject(notification);
    com.zjgsu.sfy.reporting.service.ReportFactInserter r =
        AopTestUtils.getUltimateTargetObject(reporting);
    doThrow(new IllegalStateException("injected notification failure"))
        .when(n)
        .createIfAbsent(any(), any(), any(), anyString(), anyString(), anyString());
    doThrow(new IllegalStateException("injected report failure"))
        .when(r)
        .insertDrugConsumption(any());
    mockMvc
        .perform(
            post("/api/pharmacy/prescriptions/" + p.getId() + "/dispense")
                .header("Authorization", bearer(seedPharmacist())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].stockAfter").value(7));
    verify(n, atLeastOnce())
        .createIfAbsent(any(), any(), any(), anyString(), anyString(), anyString());
    verify(r).insertDrugConsumption(any());
    assertThat(stockOf(drug.getId())).isEqualTo(7);
    assertThat(dispenseCountFor(p.getId())).isEqualTo(1);
  }

  @Test
  void allergyDependencyFailureCannotMasqueradeAsNoAllergies() throws Exception {
    var pet = seedPetWithOwner();
    var p = seedPrescriptionFor(seedDoctor(), pet, "药品", 1);
    OwnerApi target = AopTestUtils.getUltimateTargetObject(owner);
    doThrow(new IllegalStateException("injected allergy failure"))
        .when(target)
        .pharmacyAllergies(pet.getId());
    mockMvc
        .perform(
            get("/api/pharmacy/workbench/prescriptions/" + p.getId())
                .header("Authorization", bearer(seedPharmacist())))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.allergies").doesNotExist());
    assertThat(dispenseCountFor(p.getId())).isZero();
  }

  @Test
  void medicalFailureLeavesStockAndDispenseUntouched() throws Exception {
    var drug = seedDrug("故障药品", DEFAULT_SPEC, 5, 10);
    var p = seedPrescriptionFor(seedDoctor(), seedPetWithOwner(), drug.getName(), 3);
    MedicalApi target = AopTestUtils.getUltimateTargetObject(medical);
    doThrow(new IllegalStateException("injected medical failure"))
        .when(target)
        .findPrescription(p.getId());
    mockMvc
        .perform(
            post("/api/pharmacy/prescriptions/" + p.getId() + "/dispense")
                .header("Authorization", bearer(seedPharmacist())))
        .andExpect(status().isInternalServerError());
    assertThat(stockOf(drug.getId())).isEqualTo(10);
    assertThat(dispenseCountFor(p.getId())).isZero();
  }

  @Test
  void contactFailureReturnsErrorNotEmptyList() throws Exception {
    OwnerApi target = AopTestUtils.getUltimateTargetObject(owner);
    doThrow(new IllegalStateException("injected contact failure"))
        .when(target)
        .patientContacts(anyCollection());
    mockMvc
        .perform(
            get("/api/pharmacy/workbench/prescriptions")
                .header("Authorization", bearer(seedPharmacist())))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.items").doesNotExist());
  }
}
