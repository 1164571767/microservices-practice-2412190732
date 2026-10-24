package com.zjgsu.sfy.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.support.IdentityIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthFlowIntegrationTest extends IdentityIntegrationTest {

  @Test
  void registerPersistsUserWithBcryptHashAndReturnsProfile() throws Exception {
    String username = uniqueUsername();
    String phone = uniquePhone();

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username, phone, PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.roles[0]").value("OWNER"))
        .andExpect(jsonPath("$.password").doesNotExist());

    User stored = userRepository.findByUsername(username).orElseThrow();
    assertThat(stored.getPasswordHash()).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
  }

  @Test
  void registerWithDuplicateUsernameReturnsConflict() throws Exception {
    String username = seedUser(Set.of(Role.OWNER)).getUsername();

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username, uniquePhone(), PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
  }

  @Test
  void registerWithDuplicatePhoneReturnsConflict() throws Exception {
    String phone = seedUser(Set.of(Role.OWNER)).getPhone();

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueUsername(), phone, PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
  }

  @Test
  void registerWithWeakPasswordReturnsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueUsername(), uniquePhone(), "short")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void registerWithInvalidPhoneReturnsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueUsername(), "not-a-phone", PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void loginWithCorrectCredentialsReturnsTokenPair() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(user.getUsername(), PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(org.hamcrest.Matchers.greaterThan(0)));
  }

  @Test
  void loginWithWrongPasswordReturnsInvalidCredentials() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(user.getUsername(), "WrongPassword123")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void loginWithUnknownUsernameReturnsInvalidCredentials() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(uniqueUsername(), PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void protectedEndpointWithoutTokenReturnsUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/identity/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void protectedEndpointWithMalformedTokenReturnsUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/identity/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void refreshRotatesTokenAndRejectsReuseOfOldOne() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    String oldRefresh = loginRefreshToken(user.getUsername());

    String body =
        refresh(oldRefresh)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefresh = JsonPath.read(body, "$.refreshToken");
    assertThat(newRefresh).isNotEqualTo(oldRefresh);

    refresh(oldRefresh)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    // 检测到旧 token 重放后撤销整个 token family：攻击者若先用过轮换出的新 token，也不能继续会话。
    refresh(newRefresh)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    // 受害者重新登录可恢复。
    login(user.getUsername(), PASSWORD).andExpect(status().isOk());
  }

  @Test
  void logoutDoesNotRevokeAnotherUsersRefreshToken() throws Exception {
    User first = seedUser(Set.of(Role.OWNER));
    User second = seedUser(Set.of(Role.OWNER));
    String firstAccess = loginAccessToken(first.getUsername());
    String secondRefresh = loginRefreshToken(second.getUsername());

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", secondRefresh))))
        .andExpect(status().isNoContent());

    // 拿到他人 refresh token 也不能强制其下线；不泄露 token 是否存在。
    refresh(secondRefresh).andExpect(status().isOk());
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    User user = seedUser(Set.of(Role.OWNER));
    String accessToken = loginAccessToken(user.getUsername());
    String refreshToken = loginRefreshToken(user.getUsername());

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
        .andExpect(status().isNoContent());

    refresh(refreshToken).andExpect(status().isUnauthorized());
  }

  private String registerBody(String username, String phone, String password) {
    return objectMapper.writeValueAsString(
        Map.of(
            "username", username,
            "phone", phone,
            "password", password,
            "displayName", "注册用户"));
  }

  private String loginBody(String username, String password) {
    return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
  }
}
