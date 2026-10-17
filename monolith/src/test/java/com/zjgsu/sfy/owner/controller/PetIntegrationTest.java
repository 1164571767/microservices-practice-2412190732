package com.zjgsu.sfy.owner.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.owner.support.OwnerIntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class PetIntegrationTest extends OwnerIntegrationTest {

  @Test
  void ownerCreatesListsUpdatesAndDeletesPet() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    String token = bearer(owner);

    String created =
        mockMvc
            .perform(
                post("/api/owner/pets")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(petBody("旺财")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("旺财"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String petId = JsonPath.read(created, "$.id");

    mockMvc
        .perform(get("/api/owner/pets").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(get("/api/owner/pets/" + petId).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(petId));

    mockMvc
        .perform(
            put("/api/owner/pets/" + petId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(petBody("大旺")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("大旺"));

    mockMvc
        .perform(delete("/api/owner/pets/" + petId).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/owner/pets/" + petId).header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound());
  }

  @Test
  void creatingPetWithoutOwnerProfileIsNotFound() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            post("/api/owner/pets")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(petBody("旺财")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void createPetWithFutureBirthDateIsRejected() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name",
                "旺财",
                "species",
                "犬",
                "birthDate",
                LocalDate.now().plusDays(1).toString(),
                "weightKg",
                5.5));

    mockMvc
        .perform(
            post("/api/owner/pets")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void createPetWithNonPositiveWeightIsRejected() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name", "旺财",
                "species", "犬",
                "birthDate", "2020-01-01",
                "weightKg", 0));

    mockMvc
        .perform(
            post("/api/owner/pets")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void ownerCannotReadAnotherOwnersPet() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    Pet foreign = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));

    mockMvc
        .perform(
            get("/api/owner/pets/" + foreign.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void ownerCannotUpdateAnotherOwnersPet() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    Pet foreign = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));

    mockMvc
        .perform(
            put("/api/owner/pets/" + foreign.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(petBody("改名")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void staffCanReadAnotherOwnersPet() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    Pet foreign = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));

    mockMvc
        .perform(
            get("/api/owner/pets/" + foreign.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(foreign.getId().toString()));
  }

  @Test
  void getUnknownPetIsNotFound() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            get("/api/owner/pets/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }
}
