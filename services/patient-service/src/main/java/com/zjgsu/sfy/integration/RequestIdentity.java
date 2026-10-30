package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.common.error.*;
import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.identity.common.Role;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class RequestIdentity {
  private RequestIdentity() {}

  public static CurrentUser current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt))
      throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED);
    Set<Role> roles = new HashSet<>();
    for (var authority : auth.getAuthorities())
      if (authority.getAuthority().startsWith("ROLE_"))
        roles.add(Role.valueOf(authority.getAuthority().substring(5)));
    return new CurrentUser(
        UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"), roles);
  }
}
