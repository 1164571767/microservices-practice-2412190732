package com.zjgsu.sfy.common.idempotency;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import java.util.regex.Pattern;

/**
 * 幂等键校验：请求头来自客户端，默认不可信。
 *
 * <p>数据库列长度为 255；超长或含非法字符的键必须在入口被拒绝为 {@code VALIDATION_FAILED}， 而不是在唯一键写入时冒泡成 500。空白键视为未提供。
 */
public final class IdempotencyKeys {

  public static final int MAX_LENGTH = 255;
  private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_.:-]+$");

  private IdempotencyKeys() {}

  /** 返回 trim 后的合法键；未提供（null / 空白）返回 {@code null}。 */
  public static String normalize(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return null;
    }
    String key = rawKey.trim();
    if (key.length() > MAX_LENGTH || !ALLOWED.matcher(key).matches()) {
      throw new BusinessException(
          ErrorCode.VALIDATION_FAILED, "Idempotency-Key 须为 1-255 位字母、数字或 _.:- 字符");
    }
    return key;
  }
}
