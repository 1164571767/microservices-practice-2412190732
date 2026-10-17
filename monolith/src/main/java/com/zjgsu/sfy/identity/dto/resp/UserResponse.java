package com.zjgsu.sfy.identity.dto.resp;

import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.identity.common.Role;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
    UUID id, String username, String phone, String displayName, Set<Role> roles, String status) {

  public static UserResponse from(UserProfile profile) {
    return new UserResponse(
        profile.id(),
        profile.username(),
        profile.phone(),
        profile.displayName(),
        profile.roles(),
        profile.status().name());
  }
}
