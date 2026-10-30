package com.zjgsu.sfy.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元数据：代码优先契约的唯一入口（`/v3/api-docs`、`/swagger-ui.html`）。
 *
 * <p>默认对所有操作声明 Bearer JWT 安全要求；公开接口用 {@code @SecurityRequirements} 显式覆盖为空。 文档本身由 springdoc 从
 * Controller 与 DTO 注解生成，不在本类里维护路径清单，避免与实现漂移。
 */
@Configuration
public class OpenApiConfig {

  static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI petCareOpenApi(
      @Value("${spring.application.name:monolith}") String applicationName,
      @Value("${info.app.version:0.0.1-SNAPSHOT}") String version) {
    return new OpenAPI()
        .info(
            new Info()
                .title("PetCare API")
                .version(version)
                .description(
                    "宠医通（PetCare）宠物医院管理系统 REST 契约。"
                        + "每个接口都写明了用途和可调用角色，字段带示例值，可直接在 Swagger UI 里调试。"
                        + "认证方式：`Authorization: Bearer <accessToken>`（HS256 JWT），"
                        + "先调 `POST /api/auth/login` 拿 token，再点右上角 Authorize 填入即可调用受保护接口；"
                        + "错误响应统一为 `{\"code\":\"<稳定机器码>\",\"message\":\"<可读信息>\"}`，"
                        + "完整错误码见 docs/api/error-codes.md。"
                        + "应用："
                        + applicationName
                        + "。"))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("登录 `/api/auth/login` 获取的 access token。")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }
}
