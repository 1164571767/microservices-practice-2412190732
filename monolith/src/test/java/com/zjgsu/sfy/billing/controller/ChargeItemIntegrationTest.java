package com.zjgsu.sfy.billing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.billing.support.BillingIntegrationTest;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ChargeItemIntegrationTest extends BillingIntegrationTest {

  @Test
  void adminCreatesAndReadsChargeItem() throws Exception {
    User admin = seedAdmin();
    String name = uniqueChargeItemName();

    String body =
        mockMvc
            .perform(
                post("/api/billing/charge-items")
                    .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(chargeItemBody(name, "50.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.amount").value(50.00))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String chargeItemId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(
            get("/api/billing/charge-items/" + chargeItemId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(name))
        .andExpect(jsonPath("$.amount").value(50.00));
  }

  @Test
  void duplicateNameIsRejected() throws Exception {
    User admin = seedAdmin();
    String name = uniqueChargeItemName();
    seedChargeItem(name, "50.00");

    mockMvc
        .perform(
            post("/api/billing/charge-items")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeItemBody(name, "20.00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHARGE_ITEM_ALREADY_EXISTS"));
  }

  @Test
  void nonAdminCannotMutateChargeItems() throws Exception {
    User staff = seedStaff();
    User doctor = seedUser(Set.of(Role.DOCTOR));
    String body = chargeItemBody(uniqueChargeItemName(), "50.00");

    mockMvc
        .perform(
            post("/api/billing/charge-items")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

    mockMvc
        .perform(
            post("/api/billing/charge-items")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void adminUpdatesAmountAndDeactivates() throws Exception {
    User admin = seedAdmin();
    String original = uniqueChargeItemName();
    String body =
        mockMvc
            .perform(
                post("/api/billing/charge-items")
                    .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(chargeItemBody(original, "50.00")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String chargeItemId = JsonPath.read(body, "$.id");
    String renamed = uniqueChargeItemName();

    mockMvc
        .perform(
            put("/api/billing/charge-items/" + chargeItemId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateChargeItemBody(renamed, "30.00", false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(renamed))
        .andExpect(jsonPath("$.amount").value(30.00))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void updateToExistingNameIsRejected() throws Exception {
    User admin = seedAdmin();
    String existing = uniqueChargeItemName();
    seedChargeItem(existing, "50.00");
    var item = seedChargeItem("30.00");

    mockMvc
        .perform(
            put("/api/billing/charge-items/" + item.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateChargeItemBody(existing, "30.00", true)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHARGE_ITEM_ALREADY_EXISTS"));
  }

  @Test
  void invalidAmountsAreRejected() throws Exception {
    User admin = seedAdmin();
    String token = bearer(admin);

    mockMvc
        .perform(
            post("/api/billing/charge-items")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeItemBody(uniqueChargeItemName(), "-0.01")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/billing/charge-items")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeItemBody(uniqueChargeItemName(), "1.234")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/billing/charge-items")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeItemBody("  ", "10.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void listIsSearchableAndPaginated() throws Exception {
    User admin = seedAdmin();
    String prefix = uniqueChargeItemName();
    for (int i = 1; i <= 3; i++) {
      seedChargeItem(prefix + "-" + i, "10.00");
    }

    mockMvc
        .perform(
            get("/api/billing/charge-items")
                .param("query", prefix)
                .param("page", "0")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void staffCanReadButOwnerCannot() throws Exception {
    User staff = seedStaff();
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(get("/api/billing/charge-items").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/billing/charge-items").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void unknownChargeItemIsNotFound() throws Exception {
    User admin = seedAdmin();

    mockMvc
        .perform(
            get("/api/billing/charge-items/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }
}
