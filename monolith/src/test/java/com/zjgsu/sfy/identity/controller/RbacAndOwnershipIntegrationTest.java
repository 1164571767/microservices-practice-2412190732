package com.zjgsu.sfy.identity.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.support.IdentityIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class RbacAndOwnershipIntegrationTest extends IdentityIntegrationTest {

  @Test
  void authenticatedUserCanReadOwnProfile() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(get("/api/identity/me").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(owner.getId().toString()))
        .andExpect(jsonPath("$.username").value(owner.getUsername()));
  }

  @Test
  void ownerReadingAnotherProfileIsForbidden() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User other = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            get("/api/identity/users/" + other.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void staffCanReadAnotherProfile() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    User other = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            get("/api/identity/users/" + other.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(staff)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(other.getId().toString()));
  }

  @Test
  void nonAdminCannotCreateUser() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));
    User staff = seedUser(Set.of(Role.STAFF));

    mockMvc
        .perform(
            post("/api/identity/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

    mockMvc
        .perform(
            post("/api/identity/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void adminCanCreateUserWithRoles() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN));

    mockMvc
        .perform(
            post("/api/identity/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles[0]").value("DOCTOR"));
  }

  private String bearer(User user) throws Exception {
    return "Bearer " + loginAccessToken(user.getUsername());
  }

  private String createUserBody() {
    return objectMapper.writeValueAsString(
        Map.of(
            "username", uniqueUsername(),
            "phone", uniquePhone(),
            "password", PASSWORD,
            "displayName", "新员工",
            "roles", Set.of("DOCTOR")));
  }
}
