package com.zjgsu.sfy.identity.dto.resp;

import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.identity.common.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

@Schema(description = "账号资料")
public record UserResponse(
    @Schema(description = "用户 ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b") UUID id,
    @Schema(description = "登录用户名", example = "alice.01") String username,
    @Schema(description = "手机号", example = "13800000000") String phone,
    @Schema(description = "显示名称", example = "张三") String displayName,
    @Schema(description = "角色列表：OWNER 宠物主人 / DOCTOR 医生 / STAFF 前台 / PHARMACY 药房 / ADMIN 管理员")
        Set<Role> roles,
    @Schema(description = "账号状态：ACTIVE 正常 / DISABLED 已停用", example = "ACTIVE") String status) {

  public static UserResponse from(UserProfile profile) {
    return new UserResponse(
        profile.id(),
        profile.username(),
        profile.phone(),
        profile.displayName(),
        profile.roles(),
        profile.status().name());
  }
}
