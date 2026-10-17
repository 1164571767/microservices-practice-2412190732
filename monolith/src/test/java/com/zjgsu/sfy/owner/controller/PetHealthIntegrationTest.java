package com.zjgsu.sfy.owner.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Owner;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.owner.support.OwnerIntegrationTest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class PetHealthIntegrationTest extends OwnerIntegrationTest {

  @Test
  void ownerAddsAndListsVaccinations() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    String token = bearer(owner);

    mockMvc
        .perform(
            post("/api/owner/pets/" + pet.getId() + "/vaccinations")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("vaccineName", "狂犬疫苗", "administeredOn", "2026-01-01"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vaccineName").value("狂犬疫苗"));

    mockMvc
        .perform(
            get("/api/owner/pets/" + pet.getId() + "/vaccinations")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].vaccineName").value("狂犬疫苗"));
  }

  @Test
  void ownerAddsAndListsAllergies() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));
    String token = bearer(owner);

    mockMvc
        .perform(
            post("/api/owner/pets/" + pet.getId() + "/allergies")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("allergen", "花粉", "severity", "MODERATE"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.severity").value("MODERATE"));

    mockMvc
        .perform(
            get("/api/owner/pets/" + pet.getId() + "/allergies")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].allergen").value("花粉"));
  }

  @Test
  void otherOwnerCannotAddVaccinationToForeignPet() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    seedOwner(owner);
    Pet foreign = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));

    mockMvc
        .perform(
            post("/api/owner/pets/" + foreign.getId() + "/vaccinations")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("vaccineName", "狂犬疫苗", "administeredOn", "2026-01-01"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void staffCanListVaccinationsOfAnotherOwnersPet() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    Pet foreign = seedPet(seedOwner(seedUser(Set.of(Role.OWNER))));

    mockMvc
        .perform(
            get("/api/owner/pets/" + foreign.getId() + "/vaccinations")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void invalidAllergySeverityIsRejected() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Pet pet = seedPet(seedOwner(owner));

    mockMvc
        .perform(
            post("/api/owner/pets/" + pet.getId() + "/allergies")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("allergen", "花粉", "severity", "CRITICAL"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void addVaccinationToUnknownPetIsNotFound() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            post("/api/owner/pets/" + UUID.randomUUID() + "/vaccinations")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("vaccineName", "狂犬疫苗", "administeredOn", "2026-01-01"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void vaccinationWithFutureDateIsRejected() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Owner profile = seedOwner(owner);
    Pet pet = seedPet(profile);

    mockMvc
        .perform(
            post("/api/owner/pets/" + pet.getId() + "/vaccinations")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("vaccineName", "狂犬疫苗", "administeredOn", "2999-01-01"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
