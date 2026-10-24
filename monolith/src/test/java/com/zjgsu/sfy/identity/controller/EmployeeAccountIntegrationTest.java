package com.zjgsu.sfy.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.repository.AccountAuditRepository;
import com.zjgsu.sfy.identity.support.IdentityIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class EmployeeAccountIntegrationTest extends IdentityIntegrationTest {
  @Autowired AccountAuditRepository audits;
  private static final String NEW_PASSWORD = "NewStr0ngPassword!";

  @Test
  void missingVersionsAreRejectedBeforeAnyWrite() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN)), staff = seedUser(Set.of(Role.STAFF));
    String token = loginAccessToken(admin.getUsername());
    mockMvc
        .perform(
            post("/api/identity/employees/" + staff.getId() + "/password-reset")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("newPassword", NEW_PASSWORD, "reason", "缺失版本"))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/api/identity/employees/" + staff.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "displayName",
                            "修改",
                            "phone",
                            staff.getPhone(),
                            "roles",
                            Set.of("STAFF"),
                            "status",
                            "ACTIVE",
                            "reason",
                            "缺失版本"))))
        .andExpect(status().isBadRequest());
    assertThat(userRepository.findById(staff.getId()).orElseThrow().getVersion())
        .isEqualTo(staff.getVersion());
  }

  @Test
  void updatesRejectDuplicatePhoneStaleVersionAndOwnerRoleChanges() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN)), staff = seedUser(Set.of(Role.STAFF));
    String token = loginAccessToken(admin.getUsername()),
        path = "/api/identity/employees/" + staff.getId();
    var duplicate = new java.util.HashMap<>(updateBody(staff, "ACTIVE", Set.of("STAFF")));
    duplicate.put("phone", admin.getPhone());
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        updateBody(staff, "ACTIVE", Set.of("STAFF", "OWNER")))))
        .andExpect(status().isBadRequest());
    var body = updateBody(staff, "ACTIVE", Set.of("STAFF"));
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
  }

  private Map<String, Object> updateBody(User user, String status, Set<String> roles) {
    return Map.of(
        "version",
        user.getVersion(),
        "displayName",
        "维护后的员工",
        "phone",
        user.getPhone(),
        "roles",
        roles,
        "status",
        status,
        "reason",
        "员工职责调整");
  }

  @Test
  void employeeSearchFiltersAndRejectsOwnerScope() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN)),
        staff = seedUser(Set.of(Role.STAFF)),
        owner = seedUser(Set.of(Role.OWNER));
    String token = loginAccessToken(admin.getUsername());
    mockMvc
        .perform(
            get("/api/identity/employees")
                .header("Authorization", "Bearer " + token)
                .param("query", staff.getUsername())
                .param("role", "STAFF")
                .param("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(staff.getId().toString()))
        .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
    mockMvc
        .perform(
            get("/api/identity/employees")
                .header("Authorization", "Bearer " + token)
                .param("query", owner.getUsername()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(
            get("/api/identity/employees")
                .header("Authorization", "Bearer " + token)
                .param("role", "OWNER"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void passwordChangeVerifiesOldPasswordAndRevokesEverySession() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    String tokens =
        login(staff.getUsername(), PASSWORD).andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(tokens, "$.accessToken"),
        refresh = JsonPath.read(tokens, "$.refreshToken");
    String second = loginAccessToken(staff.getUsername());
    mockMvc
        .perform(
            post("/api/identity/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("oldPassword", "wrong-password", "newPassword", NEW_PASSWORD))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/identity/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("oldPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
        .andExpect(status().isNoContent());
    for (String old : new String[] {token, second})
      mockMvc
          .perform(get("/api/identity/me").header("Authorization", "Bearer " + old))
          .andExpect(status().isUnauthorized());
    refresh(refresh).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("username", staff.getUsername(), "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    login(staff.getUsername(), NEW_PASSWORD);
    var history =
        audits.findByTargetUserId(
            staff.getId(), org.springframework.data.domain.Pageable.unpaged());
    assertThat(history.getContent()).hasSize(1);
    assertThat(history.getContent().getFirst().getAfterSummary()).isNull();
    assertThat(
            passwordEncoder.matches(
                NEW_PASSWORD,
                userRepository.findById(staff.getId()).orElseThrow().getPasswordHash()))
        .isTrue();
  }

  @Test
  void resetRequiresAdminReasonAndVersionAndRevokesTargetOnly() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN)), staff = seedUser(Set.of(Role.DOCTOR));
    String token = loginAccessToken(admin.getUsername()),
        target = loginAccessToken(staff.getUsername());
    var body =
        Map.of(
            "version", staff.getVersion(), "newPassword", NEW_PASSWORD, "reason", "员工忘记密码，已核实身份");
    mockMvc
        .perform(
            post("/api/identity/employees/" + staff.getId() + "/password-reset")
                .header("Authorization", "Bearer " + target)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/identity/employees/" + staff.getId() + "/password-reset")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/identity/me").header("Authorization", "Bearer " + target))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/identity/employees/" + staff.getId() + "/password-reset")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
    login(staff.getUsername(), NEW_PASSWORD);
    mockMvc
        .perform(
            get("/api/identity/employees/" + staff.getId() + "/operations")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("RESET_PASSWORD"))
        .andExpect(jsonPath("$.items[0].reason").value(body.get("reason")))
        .andExpect(jsonPath("$.items[0].actorId").value(admin.getId().toString()));
  }

  @Test
  void roleChangesDisableAndEnableInvalidateStaleTokens() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN)), staff = seedUser(Set.of(Role.STAFF));
    String token = loginAccessToken(admin.getUsername()),
        old = loginAccessToken(staff.getUsername());
    String path = "/api/identity/employees/" + staff.getId();
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        updateBody(staff, "ACTIVE", Set.of("PHARMACY")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("PHARMACY"));
    mockMvc
        .perform(get("/api/identity/me").header("Authorization", "Bearer " + old))
        .andExpect(status().isUnauthorized());
    String next = loginAccessToken(staff.getUsername());
    staff = userRepository.findById(staff.getId()).orElseThrow();
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        updateBody(staff, "DISABLED", Set.of("PHARMACY")))))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/identity/me").header("Authorization", "Bearer " + next))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("username", staff.getUsername(), "password", PASSWORD))))
        .andExpect(status().isForbidden());
    staff = userRepository.findById(staff.getId()).orElseThrow();
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        updateBody(staff, "ACTIVE", Set.of("PHARMACY")))))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/identity/me").header("Authorization", "Bearer " + old))
        .andExpect(status().isUnauthorized());
    loginAccessToken(staff.getUsername());
  }

  @Test
  void selfProtectionOwnerIsolationValidationAndConflict() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN)),
        owner = seedUser(Set.of(Role.OWNER)),
        staff = seedUser(Set.of(Role.STAFF));
    String token = loginAccessToken(admin.getUsername());
    mockMvc
        .perform(
            put("/api/identity/employees/" + admin.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        updateBody(admin, "DISABLED", Set.of("ADMIN")))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            put("/api/identity/employees/" + owner.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(updateBody(owner, "ACTIVE", Set.of("STAFF")))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/identity/employees/" + staff.getId() + "/password-reset")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("version", 0, "newPassword", NEW_PASSWORD, "reason", " "))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/identity/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("oldPassword", PASSWORD, "newPassword", PASSWORD))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/identity/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("oldPassword", PASSWORD, "newPassword", "密".repeat(30)))))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/identity/employees")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/identity/employees")
                .header("Authorization", "Bearer " + loginAccessToken(staff.getUsername())))
        .andExpect(status().isForbidden());
  }
}
