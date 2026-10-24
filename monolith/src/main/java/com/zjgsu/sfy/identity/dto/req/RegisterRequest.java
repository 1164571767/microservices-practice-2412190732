package com.zjgsu.sfy.identity.dto.req;

import com.zjgsu.sfy.identity.service.command.RegisterCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "注册主人账号的请求体")
public record RegisterRequest(
    @Schema(description = "登录用户名，3–50 位，只能用字母、数字、下划线、点或连字符，不能与已有账号重复", example = "alice.01")
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点或连字符")
        String username,
    @Schema(description = "手机号，可带国家码 +，6–20 位数字，不能与已有账号重复", example = "13800000000")
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "手机号格式不正确")
        String phone,
    @Schema(description = "登录密码，8–72 位", example = "Str0ngPassw0rd!")
        @NotBlank
        @Size(min = 8, max = 72)
        String password,
    @Schema(description = "显示名称，最多 100 字，用于界面展示", example = "张三") @NotBlank @Size(max = 100)
        String displayName) {

  public RegisterCommand toCommand() {
    return new RegisterCommand(username, phone, password, displayName);
  }
}
