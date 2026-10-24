package com.zjgsu.sfy.identity.dto.req;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import com.zjgsu.sfy.identity.service.command.UpdateEmployeeCommand;
import jakarta.validation.constraints.*;
import java.util.Set;

public record UpdateEmployeeRequest(
    @NotNull @PositiveOrZero Long version,
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank @Size(max = 20) @Pattern(regexp = "^\\+?[0-9]{6,20}$") String phone,
    @NotEmpty Set<@NotNull Role> roles,
    @NotNull UserStatus status,
    @NotBlank @Size(max = 500) String reason) {
  public UpdateEmployeeCommand toCommand() {
    return new UpdateEmployeeCommand(version, displayName, phone, roles, status, reason);
  }
}
