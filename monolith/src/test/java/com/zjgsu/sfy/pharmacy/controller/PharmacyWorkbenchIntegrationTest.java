package com.zjgsu.sfy.pharmacy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.medical.PrescriptionIssued;
import com.zjgsu.sfy.pharmacy.repository.PrescriptionTaskRepository;
import com.zjgsu.sfy.pharmacy.support.PharmacyIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

class PharmacyWorkbenchIntegrationTest extends PharmacyIntegrationTest {
  @Autowired PrescriptionTaskRepository tasks;
  @Autowired org.springframework.context.ApplicationEventPublisher events;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired com.zjgsu.sfy.owner.repository.AllergyRepository allergies;
  private static final String ROOT = "/api/pharmacy/workbench/prescriptions";

  @Test
  void realIssueCreatesTaskAndDispenseMovesToHistoryWithoutLeakingDiagnosis() throws Exception {
    var doctor = seedDoctor();
    var pet = seedPetWithOwner();
    var pharmacist = seedPharmacist();
    pet.updateDetails(
        "药房" + UUID.randomUUID().toString().substring(0, 8),
        pet.getSpecies(),
        pet.getBirthDate(),
        pet.getWeightKg());
    pet = petRepository.saveAndFlush(pet);
    var appointment = seedAppointmentFor(pet, doctor);
    var record =
        medicalRecordRepository.saveAndFlush(
            com.zjgsu.sfy.medical.entity.MedicalRecord.create(
                appointment.getId(),
                pet.getId(),
                doctor.getId(),
                "私密症状",
                "私密诊断",
                "私密建议",
                java.time.Instant.now()));
    var drug = seedDrug("工作台药品", DEFAULT_SPEC, 5, 10);
    var issued =
        mockMvc
            .perform(
                post("/api/medical/records/" + record.getId() + "/prescriptions")
                    .header("Authorization", bearer(doctor))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(prescriptionBody(List.of(line(drug.getName(), 3, 5)))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(issued, "$.id"));
    assertThat(tasks.existsById(id)).isTrue();
    String auth = bearer(pharmacist);
    mockMvc
        .perform(get(ROOT).param("query", pet.getName()).header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.prescriptionId == '" + id + "')]").isNotEmpty());
    var review =
        mockMvc
            .perform(get(ROOT + "/" + id).header("Authorization", auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prescription.items[0].dosage").value("1片"))
            .andExpect(jsonPath("$.prescription.items[0].frequency").value("每日2次"))
            .andExpect(jsonPath("$.dispense").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(review).doesNotContain("私密诊断", "私密症状", "私密建议");
    mockMvc
        .perform(get(ROOT + "/" + id + "/dispense").header("Authorization", auth))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/pharmacy/prescriptions/" + id + "/dispense").header("Authorization", auth))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get(ROOT + "/" + id + "/dispense").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].stockAfter").value(7));
    mockMvc
        .perform(
            get(ROOT)
                .param("status", "DISPENSED")
                .param("query", pet.getName())
                .header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[?(@.prescriptionId == '" + id + "')].dispensed")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    mockMvc
        .perform(get(ROOT).param("query", pet.getName()).header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.prescriptionId == '" + id + "')]").isEmpty());
  }

  @Test
  void taskProjectionRollsBackWithIssuingTransaction() {
    var id = UUID.randomUUID();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              events.publishEvent(
                  new PrescriptionIssued(
                      id, UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now()));
              assertThat(tasks.existsById(id)).isTrue();
              tx.setRollbackOnly();
            });
    assertThat(tasks.existsById(id)).isFalse();
  }

  @Test
  void reviewOnlyIncludesCurrentAllergyCorrections() throws Exception {
    var pet = seedPetWithOwner();
    var doctor = seedDoctor();
    var pharmacist = seedPharmacist();
    var p = seedPrescriptionFor(doctor, pet, "药品", 1);
    var old =
        allergies.saveAndFlush(
            com.zjgsu.sfy.owner.entity.Allergy.create(
                pet.getId(), "旧提示", com.zjgsu.sfy.owner.common.AllergySeverity.MILD, null));
    var correction =
        com.zjgsu.sfy.owner.entity.Allergy.create(
            pet.getId(), "新提示", com.zjgsu.sfy.owner.common.AllergySeverity.SEVERE, "已核实");
    correction.corrects(old.getId(), "更正");
    allergies.saveAndFlush(correction);
    mockMvc
        .perform(get(ROOT + "/" + p.getId()).header("Authorization", bearer(pharmacist)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allergies.length()").value(1))
        .andExpect(jsonPath("$.allergies[0].allergen").value("新提示"));
  }

  @Test
  void permissionsAndValidationAreEnforcedOnAllNewEndpoints() throws Exception {
    for (var role : List.of(Role.STAFF, Role.DOCTOR, Role.OWNER)) {
      String auth = bearer(seedUser(Set.of(role)));
      for (var path :
          List.of(
              ROOT, ROOT + "/" + UUID.randomUUID(), ROOT + "/" + UUID.randomUUID() + "/dispense"))
        mockMvc.perform(get(path).header("Authorization", auth)).andExpect(status().isForbidden());
    }
    mockMvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
    String auth = bearer(seedPharmacist());
    mockMvc
        .perform(get(ROOT).param("status", "VOID").header("Authorization", auth))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(ROOT).param("query", "a".repeat(101)).header("Authorization", auth))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(ROOT + "/bad-id").header("Authorization", auth))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(ROOT + "/" + UUID.randomUUID()).header("Authorization", auth))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get(ROOT)
                .param("query", UUID.randomUUID().toString())
                .param("size", "1000")
                .param("page", "-1")
                .header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.page").value(0));
  }
}
