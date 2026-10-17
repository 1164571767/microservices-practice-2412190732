package com.zjgsu.sfy.identity.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** 连接真实 MySQL 的集成测试基类；数据用唯一用户名隔离，不弱化安全校验。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IdentityIntegrationTest {

  protected static final String PASSWORD = "Str0ngPassw0rd!";
  private static final SecureRandom RANDOM = new SecureRandom();

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected UserRepository userRepository;
  @Autowired protected PasswordEncoder passwordEncoder;

  protected String uniqueUsername() {
    return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  protected String uniquePhone() {
    StringBuilder phone = new StringBuilder("1");
    for (int i = 0; i < 10; i++) {
      phone.append(RANDOM.nextInt(10));
    }
    return phone.toString();
  }

  protected User seedUser(Set<Role> roles) {
    User user =
        User.create(
            uniqueUsername(), uniquePhone(), passwordEncoder.encode(PASSWORD), "测试用户", roles);
    return userRepository.saveAndFlush(user);
  }

  protected ResultActions login(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("username", username, "password", password))))
        .andExpect(status().isOk());
  }

  protected String loginAccessToken(String username) throws Exception {
    return readToken(login(username, PASSWORD), "$.accessToken");
  }

  protected String loginRefreshToken(String username) throws Exception {
    return readToken(login(username, PASSWORD), "$.refreshToken");
  }

  protected ResultActions refresh(String refreshToken) throws Exception {
    return mockMvc.perform(
        post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
  }

  private String readToken(ResultActions actions, String path) throws Exception {
    String body = actions.andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, path);
  }
}
