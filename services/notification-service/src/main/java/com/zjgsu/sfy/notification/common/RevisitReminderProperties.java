package com.zjgsu.sfy.notification.common;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 复诊提醒定时任务配置（模块私有）。
 *
 * <p>提醒按「就诊日 + delayDays」计算：每天扫描 {@code delayDays} 天前就诊的病历，为主人生成一次复诊提醒； 同一病历对同一接收人只会生成一条（{@code
 * dedupe_key} 唯一键）。
 */
@ConfigurationProperties(prefix = "petcare.notification.revisit-reminder")
public record RevisitReminderProperties(boolean enabled, int delayDays, String cron, String zone) {

  private static final int MIN_DELAY_DAYS = 1;
  private static final int MAX_DELAY_DAYS = 365;
  private static final String DEFAULT_CRON = "0 0 9 * * *";
  private static final String DEFAULT_ZONE = "Asia/Shanghai";

  public RevisitReminderProperties {
    if (delayDays < MIN_DELAY_DAYS || delayDays > MAX_DELAY_DAYS) {
      throw new IllegalStateException(
          "petcare.notification.revisit-reminder.delay-days must be within ["
              + MIN_DELAY_DAYS
              + ", "
              + MAX_DELAY_DAYS
              + "]");
    }
    cron = (cron == null || cron.isBlank()) ? DEFAULT_CRON : cron.trim();
    zone = (zone == null || zone.isBlank()) ? DEFAULT_ZONE : zone.trim();
    // 非法时区在启动期快速失败，避免定时任务静默不执行。
    ZoneId.of(zone);
  }

  public ZoneId zoneId() {
    return ZoneId.of(zone);
  }
}
