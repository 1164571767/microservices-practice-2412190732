package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import com.zjgsu.sfy.identity.repository.UserRepository;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
  public java.util.Map<UUID, UserProfile> findUsers(Collection<UUID> userIds) {
    if (userIds.isEmpty()) return java.util.Map.of();
    return userRepository.findByIdIn(userIds).stream()
        .map(UserProfiles::of)
        .collect(java.util.stream.Collectors.toUnmodifiableMap(UserProfile::id, u -> u));
  }

  @Override
  public Optional<UserProfile> findUser(UUID userId) {
    return userRepository.findById(userId).map(UserProfiles::of);
  }

  @Override
  public Optional<UserProfile> findUserByUsername(String username) {
    return userRepository
        .findByUsername(username.trim().toLowerCase(Locale.ROOT))
        .map(UserProfiles::of);
  }

  @Override
  public UserProfile getUser(UUID userId) {
    return userRepository
        .findById(userId)
        .map(UserProfiles::of)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Override
  public List<UUID> findActiveUserIdsInRoles(Collection<String> roleNames) {
    if (roleNames == null || roleNames.isEmpty()) {
      return List.of();
    }
    Set<Role> roles = EnumSet.noneOf(Role.class);
    for (String roleName : roleNames) {
      if (roleName == null || roleName.isBlank()) {
        continue;
      }
      try {
        roles.add(Role.valueOf(roleName.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        // 未知角色名忽略：调用方可能传入已下线或拼错的角色，不影响其它角色查询。
      }
    }
    if (roles.isEmpty()) {
      return List.of();
    }
    return userRepository.findIdsByStatusAndRoles(UserStatus.ACTIVE, roles);
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
