package com.zjgsu.sfy.integration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class InternalSecurityConfig {
  @Bean
  @Order(0)
  SecurityFilterChain internalSecurity(HttpSecurity http, @Value("${services.token}") String token)
      throws Exception {
    return http.securityMatcher("/internal/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new TokenFilter(token), AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("SERVICE"))
        .build();
  }

  static final class TokenFilter extends OncePerRequestFilter {
    private final byte[] expected;

    TokenFilter(String token) {
      expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      String supplied = request.getHeader("X-Service-Token");
      if (supplied == null
          || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
        response.setStatus(401);
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                "{\"code\":\"AUTH_UNAUTHENTICATED\",\"message\":\"Invalid service credentials\"}");
        return;
      }
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(
          new UsernamePasswordAuthenticationToken(
              "service", null, List.of(new SimpleGrantedAuthority("SERVICE"))));
      SecurityContextHolder.setContext(context);
      try {
        chain.doFilter(request, response);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }
  }
}
