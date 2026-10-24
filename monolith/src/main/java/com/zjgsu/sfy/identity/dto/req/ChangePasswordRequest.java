package com.zjgsu.sfy.identity.dto.req;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
    @NotBlank @Size(max = 72) String oldPassword,
    @NotBlank @Size(min = 8, max = 72) String newPassword) {
  @Override
  public String toString() {
    return "ChangePasswordRequest[REDACTED]";
  }
}
