package com.zjgsu.sfy.identity;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import java.util.Set;
import java.util.UUID;

/** 对其他模块暴露的用户只读投影。角色类型不外泄，其他模块只能调用语义谓词。 */
public record UserProfile(
    UUID id,
    String username,
    String phone,
    String displayName,
    Set<Role> roles,
    UserStatus status) {

  public UserProfile {
    roles = Set.copyOf(roles);
  }

  public boolean isActiveOwner() {
    return roles.contains(Role.OWNER) && status == UserStatus.ACTIVE;
  }

  public boolean isDoctor() {
    return roles.contains(Role.DOCTOR);
  }

  public boolean isActiveDoctor() {
    return isDoctor() && status == UserStatus.ACTIVE;
  }
}
