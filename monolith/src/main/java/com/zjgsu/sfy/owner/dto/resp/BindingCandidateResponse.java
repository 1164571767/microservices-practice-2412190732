package com.zjgsu.sfy.owner.dto.resp;

import com.zjgsu.sfy.identity.UserProfile;
import java.util.UUID;

public record BindingCandidateResponse(UUID id, String username, String displayName, String phone) {
  public static BindingCandidateResponse from(UserProfile u) {
    return new BindingCandidateResponse(u.id(), u.username(), u.displayName(), u.phone());
  }
}
