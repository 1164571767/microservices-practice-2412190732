package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.common.error.BusinessException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

public class DependencyAvailabilityFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (BusinessException ex) {
      if (ex.errorCode().status().value() != 503 || response.isCommitted()) throw ex;
      unavailable(response);
    } catch (DataAccessException ex) {
      if (response.isCommitted()) throw ex;
      unavailable(response);
    }
  }

  private void unavailable(HttpServletResponse response) throws IOException {
    response.setStatus(503);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\"身份依赖暂时不可用\"}");
  }
}
