package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.repository.UserRepository;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class IdentityApiImpl implements IdentityApi {

  private static final String ROLE_PREFIX = "ROLE_";

  private final UserRepository userRepository;

  public IdentityApiImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public Optional<UserProfile> findUser(UUID userId) {
    return userRepository.findById(userId).map(UserProfiles::of);
  }

  @Override
  public UserProfile getUser(UUID userId) {
    return userRepository
        .findById(userId)
        .map(UserProfiles::of)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Override
  public CurrentUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED);
    }
    return new CurrentUser(
        UUID.fromString(jwt.getSubject()),
        jwt.getClaimAsString("username"),
        rolesOf(authentication));
  }

  private static Set<Role> rolesOf(Authentication authentication) {
    Set<Role> roles = EnumSet.noneOf(Role.class);
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      String name = authority.getAuthority();
      if (name.startsWith(ROLE_PREFIX)) {
        roles.add(Role.valueOf(name.substring(ROLE_PREFIX.length())));
      }
    }
    return roles;
  }
}
