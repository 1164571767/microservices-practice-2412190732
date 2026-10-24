package com.zjgsu.sfy.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.billing.support.BillingIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

class BillingWorkbenchIntegrationTest extends BillingIntegrationTest {
  @Test
  void listsPatientContactsAndMinimalRecordIndexWithoutClinicalBody() throws Exception {
    var staff = seedStaff();
    var doctor = seedDoctor();
    var pet = seedPetWithOwner();
    var record = seedRecordFor(pet, doctor);
    var item = seedChargeItem("12.50");
    var bill =
        createBill(staff, billBody(record.getId(), null, List.of(line(item.getId(), 2))), null);
    var billId = JsonPath.<String>read(bill, "$.id");
    mockMvc
        .perform(
            get("/api/billing/workbench/bills")
                .param("petId", pet.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].bill.id").value(billId))
        .andExpect(jsonPath("$.items[0].patient.petName").value(pet.getName()));
    mockMvc
        .perform(
            get("/api/billing/workbench/records")
                .param("petId", pet.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].record.recordId").value(record.getId().toString()))
        .andExpect(jsonPath("$.items[0].existingBillId").value(billId))
        .andExpect(jsonPath("$.items[0].record.diagnosis").doesNotExist())
        .andExpect(jsonPath("$.items[0].record.symptoms").doesNotExist());
    mockMvc
        .perform(
            get("/api/billing/workbench/bills")
                .param("petId", pet.getId().toString())
                .param("status", "PAID")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void rejectsInvalidQueriesAndUnauthorizedRoles() throws Exception {
    var staff = seedStaff();
    for (var query : List.of("?page=-1", "?size=101", "?status=BOGUS", "?petId=bad")) {
      mockMvc
          .perform(
              get("/api/billing/workbench/bills" + query)
                  .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    for (var role : List.of(Role.DOCTOR, Role.OWNER, Role.PHARMACY)) {
      var user = seedUser(Set.of(role));
      mockMvc
          .perform(
              get("/api/billing/workbench/bills").header(HttpHeaders.AUTHORIZATION, bearer(user)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void onlyVoidReleasesBillingEligibility() throws Exception {
    var staff = seedStaff();
    var doctor = seedDoctor();
    var pet = seedPetWithOwner();
    var record = seedRecordFor(pet, doctor);
    var item = seedChargeItem("10.00");
    var body = billBody(record.getId(), null, List.of(line(item.getId(), 1)));
    var first = JsonPath.<String>read(createBill(staff, body, null), "$.id");
    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED"));
    mockMvc
        .perform(
            post("/api/billing/bills/" + first + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"开单有误\"}"))
        .andExpect(status().isOk());
    var second = JsonPath.<String>read(createBill(staff, body, null), "$.id");
    assertThat(second).isNotEqualTo(first);
    payBill(staff, UUID.fromString(second), null);
    mockMvc
        .perform(
            post("/api/billing/bills/" + second + "/refund")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"全额退款\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/billing/bills")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
  }
}
