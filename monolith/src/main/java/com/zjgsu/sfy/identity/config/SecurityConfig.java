package com.zjgsu.sfy.identity.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/** 无状态 JWT 资源服务器配置。对外输入默认不可信，除显式放行的公开接口外一律要求认证； 权限既在服务端由 JWT 校验，也在业务层校验资源 ownership。 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String ROLE_PREFIX = "ROLE_";
  private static final String[] OPENAPI_PATHS = {
    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      @Value("${petcare.openapi.public:false}") boolean openApiPublic)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .addFilterBefore(
            new IdentityAvailabilityFilter(),
            org.springframework.security.oauth2.server.resource.web.authentication
                .BearerTokenAuthenticationFilter.class)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(
                      HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh")
                  .permitAll()
                  .requestMatchers(
                      HttpMethod.GET,
                      "/actuator/health",
                      "/actuator/health/**",
                      "/actuator/info",
                      "/error")
                  .permitAll();
              // 文档匿名访问由 OPENAPI_PUBLIC 显式开启（默认 false）；prod 默认关闭文档（见 application-prod.yml）。
              if (openApiPublic) {
                auth.requestMatchers(HttpMethod.GET, OPENAPI_PATHS).permitAll();
              }
              // Actuator 指标包含完整 URI 与业务计数，仅管理员可读（暴露面收敛）。
              auth.requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN");
              auth.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  /** 将 JWT 的 roles 声明映射为 ROLE_* 权限，供 hasRole / hasAnyRole 使用。 */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          List<String> roles = jwt.getClaimAsStringList("roles");
          if (roles == null) {
            return List.of();
          }
          return roles.stream()
              .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
              .toList();
        });
    return converter;
  }
}
