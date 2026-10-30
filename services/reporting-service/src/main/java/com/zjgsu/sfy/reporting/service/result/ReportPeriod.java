package com.zjgsu.sfy.reporting.service.result;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 统计区间（含首尾）；统一校验顺序与跨度上限，避免各报表口径不一致。 */
public record ReportPeriod(LocalDate from, LocalDate to) {

  public static ReportPeriod of(LocalDate from, LocalDate to, int maxRangeDays) {
    if (from == null || to == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "统计起止日期不能为空");
    }
    if (from.isAfter(to)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "统计开始日期不能晚于结束日期");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    if (days > maxRangeDays) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "统计区间不能超过 " + maxRangeDays + " 天");
    }
    return new ReportPeriod(from, to);
  }
}
