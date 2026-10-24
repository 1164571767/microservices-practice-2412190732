package com.zjgsu.sfy.common.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.support.IdentityIntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 安全复查修复：Actuator 指标含完整 URI 与业务计数，只允许 ADMIN 读取。 */
class ActuatorSecurityIntegrationTest extends IdentityIntegrationTest {

  @Test
  void retiredConnectivityEndpointIsNotPublicAndAuthenticatedRequestsGetNotFound()
      throws Exception {
    mockMvc.perform(get("/api/system/status")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/system/status").with(jwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void anonymousCannotReadMetrics() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void ownerCannotReadMetrics() throws Exception {
    User owner = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            get("/actuator/metrics")
                .header(
                    HttpHeaders.AUTHORIZATION, "Bearer " + loginAccessToken(owner.getUsername())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void adminCanReadMetrics() throws Exception {
    User admin = seedUser(Set.of(Role.ADMIN));

    mockMvc
        .perform(
            get("/actuator/metrics")
                .header(
                    HttpHeaders.AUTHORIZATION, "Bearer " + loginAccessToken(admin.getUsername())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names").isArray());
  }
}
