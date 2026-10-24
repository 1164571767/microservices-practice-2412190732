package com.zjgsu.sfy.identity.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "账号状态：ACTIVE 正常可用 / DISABLED 已停用（不能登录）")
public enum UserStatus {
  ACTIVE,
  DISABLED
}
