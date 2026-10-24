package com.zjgsu.sfy.identity.dto.req;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.service.command.CreateUserCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "管理员开通员工账号的请求体")
public record CreateUserRequest(
    @Schema(description = "登录用户名，3–50 位，只能用字母、数字、下划线、点或连字符，不能与已有账号重复", example = "dr.wang")
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点或连字符")
        String username,
    @Schema(description = "手机号，可带国家码 +，6–20 位数字，不能与已有账号重复", example = "13900000000")
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "手机号格式不正确")
        String phone,
    @Schema(description = "初始密码，8–72 位", example = "Str0ngPassw0rd!")
        @NotBlank
        @Size(min = 8, max = 72)
        String password,
    @Schema(description = "显示名称，最多 100 字", example = "王医生") @NotBlank @Size(max = 100)
        String displayName,
    @Schema(description = "角色集合，至少一个：OWNER 宠物主人 / DOCTOR 医生 / STAFF 前台 / PHARMACY 药房 / ADMIN 管理员")
        @NotEmpty
        Set<Role> roles) {

  public CreateUserCommand toCommand() {
    return new CreateUserCommand(username, phone, password, displayName, roles);
  }
}
