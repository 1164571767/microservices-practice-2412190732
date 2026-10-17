package com.zjgsu.sfy.identity.dto.req;

import com.zjgsu.sfy.identity.service.command.RegisterCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点或连字符")
        String username,
    @NotBlank @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "手机号格式不正确") String phone,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 100) String displayName) {

  public RegisterCommand toCommand() {
    return new RegisterCommand(username, phone, password, displayName);
  }
}
