package com.zjgsu.sfy.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.billing.entity.Bill;
import com.zjgsu.sfy.billing.entity.BillItem;
import com.zjgsu.sfy.billing.entity.ChargeItem;
import com.zjgsu.sfy.billing.support.BillingIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.owner.entity.Pet;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class BillCreationIntegrationTest extends BillingIntegrationTest {

  @Test
  void createsBillWithServerComputedAmounts() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem consult = seedChargeItem("50.00");
    ChargeItem medicine = seedChargeItem("12.50");

    String response =
        createBill(
            staff,
            billBody(
                record.getId(), null, List.of(line(consult.getId(), 1), line(medicine.getId(), 2))),
            null);

    String billId = JsonPath.read(response, "$.id");
    assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("UNPAID");
    assertThat(JsonPath.<String>read(response, "$.currency")).isEqualTo("CNY");
    assertThat(JsonPath.<String>read(response, "$.petId")).isEqualTo(pet.getId().toString());
    assertThat(JsonPath.<String>read(response, "$.medicalRecordId"))
        .isEqualTo(record.getId().toString());
    assertThat(JsonPath.<Double>read(response, "$.totalAmount")).isEqualTo(75.00);
    assertThat(JsonPath.<List<Object>>read(response, "$.items")).hasSize(2);

    Bill bill = billOf(UUID.fromString(billId));
    assertThat(bill.getTotalAmount()).isEqualByComparingTo("75.00");
    List<BillItem> items = billItemRepository.findByBillIdOrderByCreatedAtAscIdAsc(bill.getId());
    assertThat(items).hasSize(2);
    assertThat(items)
        .extracting(BillItem::getAmount)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactlyInAnyOrder(new BigDecimal("50.00"), new BigDecimal("25.00"));
    assertThat(operationsOf(bill.getId())).hasSize(1);
    assertThat(operationsOf(bill.getId()).get(0).getAction().name()).isEqualTo("CREATED");
  }

  @Test
  void linksPrescriptionToBill() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    Prescription prescription = seedPrescription(record.getId(), doctor.getId(), "阿莫西林");
    ChargeItem item = seedChargeItem("20.00");

    String response =
        createBill(
            staff,
            billBody(record.getId(), prescription.getId(), List.of(line(item.getId(), 1))),
            null);

    assertThat(JsonPath.<String>read(response, "$.prescriptionId"))
        .isEqualTo(prescription.getId().toString());
    assertThat(JsonPath.<String>read(response, "$.medicalRecordId"))
        .isEqualTo(record.getId().toString());
  }

  @Test
  void rejectsPrescriptionFromAnotherRecord() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord first = seedRecordFor(pet, doctor);
    MedicalRecord second = seedRecordFor(pet, doctor);
    Prescription foreign = seedPrescription(second.getId(), doctor.getId(), "阿莫西林");
    ChargeItem item = seedChargeItem("20.00");

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(first.getId(), foreign.getId(), List.of(line(item.getId(), 1)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void rejectsInactiveChargeItem() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("20.00");
    item.update(item.getName(), item.getAmount(), false);
    chargeItemRepository.saveAndFlush(item);

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(record.getId(), null, List.of(line(item.getId(), 1)))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
  }

  @Test
  void rejectsUnknownMedicalRecord() throws Exception {
    User staff = seedStaff();
    ChargeItem item = seedChargeItem("20.00");

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(UUID.randomUUID(), null, List.of(line(item.getId(), 1)))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void rejectsUnknownChargeItem() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(record.getId(), null, List.of(line(UUID.randomUUID(), 1)))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void rejectsEmptyAndDuplicateLines() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("20.00");
    String token = bearer(staff);

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(record.getId(), null, List.of())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    billBody(
                        record.getId(),
                        null,
                        List.of(line(item.getId(), 1), line(item.getId(), 2)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void ownerCannotCreateBill() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("20.00");

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(record.getId(), null, List.of(line(item.getId(), 1)))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void idempotentReplayReturnsSameBill() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("20.00");
    String body = billBody(record.getId(), null, List.of(line(item.getId(), 1)));
    String key = "bill-create-" + UUID.randomUUID();

    String first = createBill(staff, body, key);
    String second = createBill(staff, body, key);

    assertThat(JsonPath.<String>read(second, "$.id"))
        .isEqualTo(JsonPath.<String>read(first, "$.id"));
    assertThat(billRepository.countByMedicalRecordId(record.getId())).isEqualTo(1);
  }

  @Test
  void sameKeyWithDifferentBodyConflicts() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("20.00");
    String key = "bill-create-conflict-" + UUID.randomUUID();

    createBill(staff, billBody(record.getId(), null, List.of(line(item.getId(), 1))), key);

    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(billBody(record.getId(), null, List.of(line(item.getId(), 2)))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void catalogPriceChangeDoesNotAlterExistingBill() throws Exception {
    User admin = seedAdmin();
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("20.00");
    String response =
        createBill(staff, billBody(record.getId(), null, List.of(line(item.getId(), 2))), null);
    String billId = JsonPath.read(response, "$.id");

    mockMvc
        .perform(
            put("/api/billing/charge-items/" + item.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateChargeItemBody(item.getName(), "99.00", true)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/billing/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].unitPrice").value(20.00))
        .andExpect(jsonPath("$.items[0].amount").value(40.00))
        .andExpect(jsonPath("$.totalAmount").value(40.00));
  }

  @Test
  void detailIncludesAuditTrail() throws Exception {
    User staff = seedStaff();
    User doctor = seedDoctor();
    Pet pet = seedPetWithOwner();
    MedicalRecord record = seedRecordFor(pet, doctor);
    ChargeItem item = seedChargeItem("20.00");
    String response =
        createBill(staff, billBody(record.getId(), null, List.of(line(item.getId(), 1))), null);
    String billId = JsonPath.read(response, "$.id");

    mockMvc
        .perform(
            get("/api/billing/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operations.length()").value(1))
        .andExpect(jsonPath("$.operations[0].action").value("CREATED"))
        .andExpect(jsonPath("$.operations[0].toStatus").value("UNPAID"))
        .andExpect(jsonPath("$.operations[0].operatorId").value(staff.getId().toString()))
        .andExpect(jsonPath("$.payment").doesNotExist());
  }
}
