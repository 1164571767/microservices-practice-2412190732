package com.zjgsu.sfy.pharmacy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.pharmacy.support.PharmacyIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DrugCatalogIntegrationTest extends PharmacyIntegrationTest {

  private String createDrug(User pharmacist, String name, String specification) throws Exception {
    return mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(drugBody(name, specification, "盒", 3, 10)))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void pharmacistCreatesDrugAndInitialInventory() throws Exception {
    User pharmacist = seedPharmacist();
    String name = uniqueDrugName();

    String body =
        mockMvc
            .perform(
                post("/api/pharmacy/drugs")
                    .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(drugBody(name, "0.5g", "盒", 3, 10)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.specification").value("0.5g"))
            .andExpect(jsonPath("$.unit").value("盒"))
            .andExpect(jsonPath("$.lowStockThreshold").value(3))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String drugId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(
            get("/api/pharmacy/inventory/" + drugId)
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(10))
        .andExpect(jsonPath("$.lowStock").value(false));
  }

  @Test
  void adminCanCreateDrug() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN));

    mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(drugBody(uniqueDrugName(), "0.5g", "盒", 1, 2)))
        .andExpect(status().isCreated());
  }

  @Test
  void duplicateNameAndSpecificationIsRejected() throws Exception {
    User pharmacist = seedPharmacist();
    String name = uniqueDrugName();
    createDrug(pharmacist, name, "0.5g");

    mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(drugBody(name, "0.5g", "瓶", 1, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DRUG_ALREADY_EXISTS"));
  }

  @Test
  void updateChangesCatalogAndDeactivatesDrug() throws Exception {
    User pharmacist = seedPharmacist();
    String body = createDrug(pharmacist, uniqueDrugName(), "0.5g");
    String drugId = JsonPath.read(body, "$.id");
    String newName = uniqueDrugName();

    mockMvc
        .perform(
            put("/api/pharmacy/drugs/" + drugId)
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateDrugBody(newName, "1g", "瓶", 8, false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(newName))
        .andExpect(jsonPath("$.specification").value("1g"))
        .andExpect(jsonPath("$.unit").value("瓶"))
        .andExpect(jsonPath("$.lowStockThreshold").value(8))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void updateToExistingNameAndSpecificationIsRejected() throws Exception {
    User pharmacist = seedPharmacist();
    String existingName = uniqueDrugName();
    createDrug(pharmacist, existingName, "0.5g");
    String body = createDrug(pharmacist, uniqueDrugName(), "0.5g");
    String drugId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(
            put("/api/pharmacy/drugs/" + drugId)
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateDrugBody(existingName, "0.5g", "盒", 1, true)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DRUG_ALREADY_EXISTS"));
  }

  @Test
  void catalogIsSearchableAndPaginated() throws Exception {
    User pharmacist = seedPharmacist();
    String prefix = uniqueDrugName();
    for (int i = 1; i <= 3; i++) {
      createDrug(pharmacist, prefix + "-" + i, "0.5g");
    }

    mockMvc
        .perform(
            get("/api/pharmacy/drugs")
                .param("query", prefix)
                .param("page", "0")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void unknownDrugIsNotFound() throws Exception {
    User pharmacist = seedPharmacist();

    mockMvc
        .perform(
            get("/api/pharmacy/drugs/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(pharmacist)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void invalidCatalogRequestIsRejected() throws Exception {
    User pharmacist = seedPharmacist();
    String token = bearer(pharmacist);

    mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(drugBody(" ", "0.5g", "盒", 1, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(drugBody(uniqueDrugName(), "0.5g", "盒", -1, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(drugBody(uniqueDrugName(), "0.5g", "盒", 1, -1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void nonPharmacyRolesCannotMutateCatalog() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User doctor = seedUser(Set.of(Role.DOCTOR));
    String body = drugBody(uniqueDrugName(), "0.5g", "盒", 1, 1);

    mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

    mockMvc
        .perform(
            post("/api/pharmacy/drugs")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }
}
