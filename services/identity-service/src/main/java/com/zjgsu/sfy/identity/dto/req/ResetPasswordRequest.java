package com.zjgsu.sfy.identity.dto.req;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
    @NotNull @PositiveOrZero Long version,
    @NotBlank @Size(min = 8, max = 72) String newPassword,
    @NotBlank @Size(max = 500) String reason) {
  @Override
  public String toString() {
    return "ResetPasswordRequest[REDACTED]";
  }
}
