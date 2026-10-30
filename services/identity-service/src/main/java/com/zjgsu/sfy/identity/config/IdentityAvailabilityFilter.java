package com.zjgsu.sfy.identity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Database-backed session validation fails closed while distinguishing outages from expired
 * credentials.
 */
public class IdentityAvailabilityFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (DataAccessException exception) {
      if (response.isCommitted()) throw exception;
      response.setStatus(503);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response
          .getWriter()
          .write("{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\"身份服务暂时不可用，请稍后重试\"}");
    }
  }
}
