package com.zjgsu.sfy.identity.dto.resp;

import com.zjgsu.sfy.identity.entity.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EmployeeResponse(
    UUID id,
    String username,
    String phone,
    String displayName,
    Set<String> roles,
    String status,
    long version,
    Instant createdAt) {
  public static EmployeeResponse from(User user) {
    return new EmployeeResponse(
        user.getId(),
        user.getUsername(),
        user.getPhone(),
        user.getDisplayName(),
        user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
        user.getStatus().name(),
        user.getVersion(),
        user.getCreatedAt());
  }
}
