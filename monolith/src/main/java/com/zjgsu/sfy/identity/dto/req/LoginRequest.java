package com.zjgsu.sfy.identity.dto.req;

import com.zjgsu.sfy.identity.service.command.LoginCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 72) String password) {

  public LoginCommand toCommand() {
    return new LoginCommand(username, password);
  }
}
