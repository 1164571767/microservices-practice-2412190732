package com.zjgsu.sfy.reporting.common;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统计报表配置（模块私有）。
 *
 * <p>统计日按 {@code zone} 的本地日期切分；查询区间含首尾两天，跨度上限防止无界聚合。
 */
@ConfigurationProperties(prefix = "petcare.reporting")
public record ReportingProperties(String zone, int maxRangeDays) {

  private static final String DEFAULT_ZONE = "Asia/Shanghai";
  private static final int DEFAULT_MAX_RANGE_DAYS = 366;
  private static final int MAX_ALLOWED_RANGE_DAYS = 3660;

  public ReportingProperties {
    zone = (zone == null || zone.isBlank()) ? DEFAULT_ZONE : zone.trim();
    // 非法时区在启动期快速失败，避免统计日与查询口径静默漂移。
    ZoneId.of(zone);
    if (maxRangeDays <= 0 || maxRangeDays > MAX_ALLOWED_RANGE_DAYS) {
      maxRangeDays = DEFAULT_MAX_RANGE_DAYS;
    }
  }

  public ZoneId zoneId() {
    return ZoneId.of(zone);
  }
}
