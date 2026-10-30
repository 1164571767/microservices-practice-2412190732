package com.zjgsu.sfy.identity.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;

final class PasswordPolicy {
  private PasswordPolicy() {}

  static void requireEncodable(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码最多 72 个 UTF-8 字节，请缩短后重试");
  }
}
