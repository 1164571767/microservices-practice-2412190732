package com.zjgsu.sfy.identity.dto.req;

import com.zjgsu.sfy.identity.service.command.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "登录请求体")
public record LoginRequest(
    @Schema(description = "登录用户名", example = "alice.01") @NotBlank @Size(max = 50) String username,
    @Schema(description = "登录密码", example = "Str0ngPassw0rd!") @NotBlank @Size(max = 72)
        String password) {

  public LoginCommand toCommand() {
    return new LoginCommand(username, password);
  }
}
