package com.zjgsu.sfy.notification.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 通知文案的格式化工具：时间统一按 {@code Asia/Shanghai} 展示，金额固定 2 位小数，id 使用短前缀。 */
public final class NotificationTexts {

  private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(DISPLAY_ZONE);
  private static final int SHORT_ID_LENGTH = 8;

  private NotificationTexts() {}

  public static String formatInstant(Instant instant) {
    return DATE_TIME.format(instant);
  }

  public static String shortId(UUID id) {
    String value = id.toString();
    return value.length() <= SHORT_ID_LENGTH ? value : value.substring(0, SHORT_ID_LENGTH);
  }

  public static String money(BigDecimal amount, String currency) {
    return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
  }
}
