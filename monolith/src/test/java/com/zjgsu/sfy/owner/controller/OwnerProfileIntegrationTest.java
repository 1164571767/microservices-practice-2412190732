package com.zjgsu.sfy.owner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.owner.entity.Owner;
import com.zjgsu.sfy.owner.support.OwnerIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class OwnerProfileIntegrationTest extends OwnerIntegrationTest {

  @Test
  void putMeCreatesProfileThenGetMeReturnsIt() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    String token = bearer(owner);

    mockMvc
        .perform(
            put("/api/owner/me")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerBody("张三")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("张三"))
        .andExpect(jsonPath("$.userId").value(owner.getId().toString()));

    mockMvc
        .perform(get("/api/owner/me").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("张三"));
  }

  @Test
  void putMeTwiceUpdatesInsteadOfDuplicating() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    String token = bearer(owner);

    mockMvc
        .perform(
            put("/api/owner/me")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerBody("张三")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/owner/me")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerBody("李四")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("李四"));

    long profiles =
        ownerRepository.findAll().stream().filter(o -> o.belongsTo(owner.getId())).count();
    assertThat(profiles).isEqualTo(1);
  }

  @Test
  void getMeBeforeCreationReturnsNotFound() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(get("/api/owner/me").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void ownerCanReadOwnProfileById() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Owner profile = seedOwner(owner);

    mockMvc
        .perform(
            get("/api/owner/owners/" + profile.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(profile.getId().toString()));
  }

  @Test
  void ownerCannotReadAnotherOwnersProfile() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    Owner other = seedOwner(seedUser(Set.of(Role.OWNER)));

    mockMvc
        .perform(
            get("/api/owner/owners/" + other.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void staffCanReadAnotherOwnersProfile() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    Owner other = seedOwner(seedUser(Set.of(Role.OWNER)));

    mockMvc
        .perform(
            get("/api/owner/owners/" + other.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(other.getId().toString()));
  }

  @Test
  void putMeWithBlankNameIsRejected() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    String body = objectMapper.writeValueAsString(Map.of("name", " ", "phone", uniquePhone()));

    mockMvc
        .perform(
            put("/api/owner/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void anonymousIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/owner/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
  }
}
