package com.zjgsu.sfy.pharmacy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.pharmacy.LowStockDetected;
import com.zjgsu.sfy.pharmacy.PrescriptionDispensed;
import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.pharmacy.support.PharmacyIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

@RecordApplicationEvents
class DispenseIntegrationTest extends PharmacyIntegrationTest {

  @Autowired ApplicationEvents events;

  private ResultActions dispense(User pharmacist, UUID prescriptionId, String idempotencyKey)
      throws Exception {
    var request =
        post("/api/pharmacy/prescriptions/" + prescriptionId + "/dispense")
            .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist));
    if (idempotencyKey != null) {
      request = request.header("Idempotency-Key", idempotencyKey);
    }
    return mockMvc.perform(request);
  }

  @Test
  void dispenseDeductsStockAndReturnsSnapshot() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    seedAppointmentFor(pet, doctor);
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 2, 10);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);

    String body =
        dispense(pharmacist, prescription.getId(), null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.prescriptionId").value(prescription.getId().toString()))
            .andExpect(jsonPath("$.doctorId").value(doctor.getId().toString()))
            .andExpect(jsonPath("$.petId").value(pet.getId().toString()))
            .andExpect(jsonPath("$.dispensedBy").value(pharmacist.getId().toString()))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].drugId").value(drug.getId().toString()))
            .andExpect(jsonPath("$.items[0].drugName").value(drug.getName()))
            .andExpect(jsonPath("$.items[0].quantity").value(3))
            .andExpect(jsonPath("$.items[0].stockAfter").value(7))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<String>read(body, "$.id")).isNotBlank();
    assertThat(stockOf(drug.getId())).isEqualTo(7);
    assertThat(dispenseCountFor(prescription.getId())).isEqualTo(1);
    assertThat(events.stream(PrescriptionDispensed.class).count()).isEqualTo(1);
    PrescriptionDispensed event =
        events.stream(PrescriptionDispensed.class).findFirst().orElseThrow();
    assertThat(event.items()).hasSize(1);
    assertThat(event.items().getFirst().drugId()).isEqualTo(drug.getId());
    assertThat(event.items().getFirst().quantity()).isEqualTo(3);
  }

  @Test
  void repeatedDispenseReplaysSameRecordWithoutDeductingAgain() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 2, 10);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);

    String firstId =
        JsonPath.read(
            dispense(pharmacist, prescription.getId(), null)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    String secondId =
        JsonPath.read(
            dispense(pharmacist, prescription.getId(), null)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    assertThat(secondId).isEqualTo(firstId);
    assertThat(stockOf(drug.getId())).isEqualTo(7);
    assertThat(dispenseCountFor(prescription.getId())).isEqualTo(1);
    assertThat(events.stream(PrescriptionDispensed.class).count()).isEqualTo(1);
  }

  @Test
  void insufficientStockIsRejectedWithoutSideEffects() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 1, 2);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);

    dispense(pharmacist, prescription.getId(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    assertThat(stockOf(drug.getId())).isEqualTo(2);
    assertThat(dispenseCountFor(prescription.getId())).isZero();
  }

  @Test
  void unknownDrugInCatalogIsNotFound() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Prescription prescription = seedPrescriptionFor(doctor, pet, uniqueDrugName(), 3);

    dispense(pharmacist, prescription.getId(), null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

    assertThat(dispenseCountFor(prescription.getId())).isZero();
  }

  @Test
  void specificationMismatchIsNotFound() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", "1g", 1, 10);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), "0.5g", 3);

    dispense(pharmacist, prescription.getId(), null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void inactiveDrugIsRejected() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, "盒", 1, 10, false);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);

    dispense(pharmacist, prescription.getId(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));

    assertThat(stockOf(drug.getId())).isEqualTo(10);
  }

  @Test
  void ownerAndDoctorCannotDispense() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 1, 10);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);
    User owner = seedUser(Set.of(Role.OWNER));

    dispense(owner, prescription.getId(), null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

    dispense(doctor, prescription.getId(), null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void unknownPrescriptionIsNotFound() throws Exception {
    User pharmacist = seedPharmacist();

    dispense(pharmacist, UUID.randomUUID(), null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void sameIdempotencyKeyForDifferentPrescriptionConflictsAndRollsBack() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 1, 10);
    Prescription first = seedPrescriptionFor(doctor, pet, drug.getName(), 3);
    Prescription second = seedPrescriptionFor(doctor, pet, drug.getName(), 3);
    String key = "dispense-key-" + UUID.randomUUID();

    dispense(pharmacist, first.getId(), key).andExpect(status().isCreated());
    assertThat(stockOf(drug.getId())).isEqualTo(7);

    dispense(pharmacist, second.getId(), key)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    assertThat(stockOf(drug.getId())).isEqualTo(7);
    assertThat(dispenseCountFor(first.getId())).isEqualTo(1);
    assertThat(dispenseCountFor(second.getId())).isZero();
  }

  @Test
  void multipleLinesForSameDrugAreAggregated() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 1, 10);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);
    prescriptionItemRepository.saveAndFlush(
        PrescriptionItem.create(
            prescription.getId(), drug.getName(), DEFAULT_SPEC, "1片", "每日2次", 2, 3, null));

    dispense(pharmacist, prescription.getId(), null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].quantity").value(5))
        .andExpect(jsonPath("$.items[0].stockAfter").value(5));

    assertThat(stockOf(drug.getId())).isEqualTo(5);
  }

  @Test
  void lowStockAfterDispenseIsPublishedOnce() throws Exception {
    User pharmacist = seedPharmacist();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    Drug drug = seedDrug("阿莫西林", DEFAULT_SPEC, 2, 3);
    Prescription prescription = seedPrescriptionFor(doctor, pet, drug.getName(), 3);

    dispense(pharmacist, prescription.getId(), null).andExpect(status().isCreated());
    assertThat(stockOf(drug.getId())).isZero();
    assertThat(events.stream(LowStockDetected.class).count()).isEqualTo(1);

    dispense(pharmacist, prescription.getId(), null).andExpect(status().isCreated());
    assertThat(events.stream(LowStockDetected.class).count()).isEqualTo(1);
  }
}
