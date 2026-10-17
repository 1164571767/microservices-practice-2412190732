package com.zjgsu.sfy.identity.dto.req;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.service.command.CreateUserCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
    @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点或连字符")
        String username,
    @NotBlank @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "手机号格式不正确") String phone,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 100) String displayName,
    @NotEmpty Set<Role> roles) {

  public CreateUserCommand toCommand() {
    return new CreateUserCommand(username, phone, password, displayName, roles);
  }
}
