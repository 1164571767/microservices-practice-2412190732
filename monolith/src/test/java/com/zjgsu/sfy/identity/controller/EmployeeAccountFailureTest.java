package com.zjgsu.sfy.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zjgsu.sfy.identity.common.*;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.repository.*;
import com.zjgsu.sfy.identity.support.IdentityIntegrationTest;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class EmployeeAccountFailureTest extends IdentityIntegrationTest {
  @MockitoSpyBean AccountAuditRepository audits;
  @MockitoSpyBean UserRepository users;

  @Test
  void auditStorageFailureRollsBackPasswordAndSessionRevocation() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN)), staff = seedUser(Set.of(Role.STAFF));
    String token = loginAccessToken(admin.getUsername()),
        target = loginAccessToken(staff.getUsername()),
        refresh = loginRefreshToken(staff.getUsername());
    doThrow(new DataAccessResourceFailureException("injected audit storage failure"))
        .when(audits)
        .save(any());
    mockMvc
        .perform(
            post("/api/identity/employees/" + staff.getId() + "/password-reset")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "version",
                            staff.getVersion(),
                            "newPassword",
                            "NewPassword123!",
                            "reason",
                            "故障注入"))))
        .andExpect(status().is5xxServerError());
    assertThat(
            passwordEncoder.matches(
                PASSWORD, userRepository.findById(staff.getId()).orElseThrow().getPasswordHash()))
        .isTrue();
    mockMvc
        .perform(get("/api/identity/me").header("Authorization", "Bearer " + target))
        .andExpect(status().isOk());
    refresh(refresh).andExpect(status().isOk());
  }

  @Test
  void sessionLookupOutageReturns503WithoutExposingDatabaseMessage() throws Exception {
    User staff = seedUser(Set.of(Role.STAFF));
    String token = loginAccessToken(staff.getUsername());
    doThrow(new DataAccessResourceFailureException("injected database host failure"))
        .when(users)
        .findById(staff.getId());
    mockMvc
        .perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database host"))));
  }

  @Test
  void lastActiveAdministratorCannotBeRemoved() throws Exception {
    User actor = seedUser(Set.of(Role.ADMIN)), target = seedUser(Set.of(Role.ADMIN));
    String token = loginAccessToken(actor.getUsername());
    doReturn(1L).when(users).countByStatusAndRole(UserStatus.ACTIVE, Role.ADMIN);
    mockMvc
        .perform(
            put("/api/identity/employees/" + target.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "version",
                            target.getVersion(),
                            "displayName",
                            target.getDisplayName(),
                            "phone",
                            target.getPhone(),
                            "roles",
                            Set.of("STAFF"),
                            "status",
                            "ACTIVE",
                            "reason",
                            "测试末位管理员保护"))))
        .andExpect(status().isConflict());
    assertThat(userRepository.findById(target.getId()).orElseThrow().getRoles())
        .contains(Role.ADMIN);
  }

  @Test
  void concurrentResetsAcceptOnlyOneExpectedVersion() throws Exception {
    User actor = seedUser(Set.of(Role.ADMIN)), target = seedUser(Set.of(Role.STAFF));
    String token = loginAccessToken(actor.getUsername());
    var barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Integer> reset =
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return mockMvc
                .perform(
                    post("/api/identity/employees/" + target.getId() + "/password-reset")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of(
                                    "version",
                                    target.getVersion(),
                                    "newPassword",
                                    "NewPassword123!",
                                    "reason",
                                    "并发重置"))))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = executor.submit(reset);
      var second = executor.submit(reset);
      assertThat(
              java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(204, 409);
      assertThat(
              audits
                  .findByTargetUserId(
                      target.getId(), org.springframework.data.domain.Pageable.unpaged())
                  .getTotalElements())
          .isEqualTo(1);
    }
  }
}
