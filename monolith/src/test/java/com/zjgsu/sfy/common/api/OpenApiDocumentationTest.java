package com.zjgsu.sfy.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T-36：OpenAPI 契约与实现同步校验。
 *
 * <p>不维护手写路径清单：从 {@link RequestMappingHandlerMapping} 枚举真实 Handler 方法， 与 `/v3/api-docs`
 * 双向比对；任何一侧新增或 删除接口都会让测试失败。另校验公开接口不继承全局 Bearer 安全要求。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

  private static final String API_DOCS = "/v3/api-docs";
  private static final List<String> FRAMEWORK_PATH_PREFIXES =
      List.of("/v3/api-docs", "/swagger-ui", "/swagger-ui.html", "/error", "/test/");

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMapping;

  @Test
  void apiDocsAreServedWithMetadataAndBearerScheme() throws Exception {
    JsonNode docs = fetchDocs();

    assertThat(docs.path("openapi").asString()).startsWith("3.");
    assertThat(docs.path("info").path("title").asString()).isEqualTo("PetCare API");
    assertThat(docs.path("info").path("version").asString()).isNotBlank();

    JsonNode scheme = docs.path("components").path("securitySchemes").path("bearerAuth");
    assertThat(scheme.path("type").asString()).isEqualTo("http");
    assertThat(scheme.path("scheme").asString()).isEqualTo("bearer");
    assertThat(scheme.path("bearerFormat").asString()).isEqualTo("JWT");
    assertThat(docs.path("security").toString()).contains("bearerAuth");
  }

  @Test
  void everyHandlerMappingEndpointIsDocumented() throws Exception {
    Map<String, JsonNode> paths = documentedPaths(fetchDocs());
    List<String> missing = new ArrayList<>();

    for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
      RequestMappingInfo info = entry.getKey();
      for (String path : patternsOf(info)) {
        if (isFrameworkPath(path)) {
          continue;
        }
        for (RequestMethod method : methodsOf(info)) {
          JsonNode operations = paths.get(path);
          if (operations == null || !operations.has(method.name().toLowerCase(Locale.ROOT))) {
            missing.add(method.name() + " " + path);
          }
        }
      }
    }

    assertThat(missing).as("Handler 已实现但未出现在 /v3/api-docs 的接口").isEmpty();
  }

  @Test
  void everyDocumentedOperationHasAHandler() throws Exception {
    Map<String, JsonNode> paths = documentedPaths(fetchDocs());
    Set<String> handlerOperations = new LinkedHashSet<>();

    for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
      RequestMappingInfo info = entry.getKey();
      for (String path : patternsOf(info)) {
        if (isFrameworkPath(path)) {
          continue;
        }
        for (RequestMethod method : methodsOf(info)) {
          handlerOperations.add(method.name() + " " + path);
        }
      }
    }

    List<String> stale = new ArrayList<>();
    for (Map.Entry<String, JsonNode> pathEntry : paths.entrySet()) {
      if (isFrameworkPath(pathEntry.getKey())) {
        continue;
      }
      for (Map.Entry<String, JsonNode> operation : pathEntry.getValue().properties()) {
        String operationKey =
            operation.getKey().toUpperCase(Locale.ROOT) + " " + pathEntry.getKey();
        if (!handlerOperations.contains(operationKey)) {
          stale.add(operationKey);
        }
      }
    }

    assertThat(stale).as("OpenAPI 文档中存在但 Handler 已不存在的接口").isEmpty();
  }

  @Test
  void publicEndpointsOverrideGlobalSecurityRequirement() throws Exception {
    JsonNode paths = fetchDocs().path("paths");

    for (String[] operation :
        List.of(
            new String[] {"/api/auth/register", "post"},
            new String[] {"/api/auth/login", "post"},
            new String[] {"/api/auth/refresh", "post"})) {
      JsonNode security = paths.path(operation[0]).path(operation[1]).path("security");
      assertThat(security.isArray())
          .as("%s %s 必须显式声明空 security（公开接口）", operation[1], operation[0])
          .isTrue();
      assertThat(security.isEmpty()).as("%s %s security 应为空", operation[1], operation[0]).isTrue();
    }
  }

  @Test
  void retiredConnectivityEndpointIsNotDocumentedOrMapped() throws Exception {
    String retiredPath = "/api/system/status";
    assertThat(fetchDocs().path("paths").has(retiredPath)).isFalse();
    assertThat(handlerMapping.getHandlerMethods().keySet())
        .noneMatch(mapping -> patternsOf(mapping).contains(retiredPath));
  }

  @Test
  void protectedEndpointsInheritOrDeclareBearerAuth() throws Exception {
    JsonNode docs = fetchDocs();
    JsonNode paths = docs.path("paths");

    for (String[] operation :
        List.of(
            new String[] {"/api/identity/me", "get"},
            new String[] {"/api/appointment/appointments", "post"},
            new String[] {"/api/pharmacy/prescriptions/{prescriptionId}/dispense", "post"},
            new String[] {"/api/billing/bills/{billId}/pay", "post"},
            new String[] {"/api/reporting/revenue", "get"})) {
      JsonNode security = paths.path(operation[0]).path(operation[1]).path("security");
      boolean protectedByOperation =
          security.isArray() && security.toString().contains("bearerAuth");
      boolean inheritsGlobal =
          security.isMissingNode() && docs.path("security").toString().contains("bearerAuth");
      assertThat(protectedByOperation || inheritsGlobal)
          .as("%s %s 必须要求 Bearer 认证", operation[1], operation[0])
          .isTrue();
    }
  }

  private JsonNode fetchDocs() throws Exception {
    String body =
        mockMvc
            .perform(get(API_DOCS).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private static Map<String, JsonNode> documentedPaths(JsonNode docs) {
    Map<String, JsonNode> paths = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : docs.path("paths").properties()) {
      paths.put(entry.getKey(), entry.getValue());
    }
    return paths;
  }

  private static Set<RequestMethod> methodsOf(RequestMappingInfo info) {
    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
    return methods.isEmpty() ? Set.of(RequestMethod.GET) : methods;
  }

  private static Set<String> patternsOf(RequestMappingInfo info) {
    var condition = info.getPathPatternsCondition();
    if (condition == null) {
      throw new IllegalStateException("Handler mapping uses legacy path patterns: " + info);
    }
    return condition.getPatternValues();
  }

  private static boolean isFrameworkPath(String path) {
    return FRAMEWORK_PATH_PREFIXES.stream().anyMatch(path::startsWith);
  }
}
