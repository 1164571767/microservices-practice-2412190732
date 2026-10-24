package com.zjgsu.sfy.common.query;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import java.util.Locale;

/**
 * 列表查询关键字到 JPQL LIKE 模式的转换。
 *
 * <p>用户输入是 LIKE 元字符（{@code %} / {@code _} / {@code !}）的不可信来源：不转义会让用户扩大匹配范围。 统一使用 {@code !}
 * 作为转义符，Repository 以 {@code like ... escape '!'} 配合；并对关键字长度设上限，避免无界扫描。
 */
public final class LikePatterns {

  public static final int MAX_QUERY_LENGTH = 100;

  private LikePatterns() {}

  /** 未提供关键字时返回 {@code %}（匹配全部）；否则返回已转义、大小写不敏感的包含匹配模式。 */
  public static String contains(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return "%";
    }
    String trimmed = rawQuery.trim();
    if (trimmed.length() > MAX_QUERY_LENGTH) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "查询关键字长度不能超过 " + MAX_QUERY_LENGTH);
    }
    String escaped =
        trimmed.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }
}
