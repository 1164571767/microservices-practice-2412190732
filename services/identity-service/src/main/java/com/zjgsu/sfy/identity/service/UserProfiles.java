package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.identity.entity.User;

final class UserProfiles {

  private UserProfiles() {}

  static UserProfile of(User user) {
    return new UserProfile(
        user.getId(),
        user.getUsername(),
        user.getPhone(),
        user.getDisplayName(),
        user.getRoles(),
        user.getStatus());
  }
}
