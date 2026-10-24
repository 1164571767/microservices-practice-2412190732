package com.zjgsu.sfy.reporting.service.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportPeriodTest {

  @Test
  void acceptsInclusiveRangeWithinLimit() {
    ReportPeriod period =
        ReportPeriod.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 31);

    assertThat(period.from()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(period.to()).isEqualTo(LocalDate.of(2026, 10, 31));
  }

  @Test
  void rejectsReversedRange() {
    assertThatThrownBy(
            () -> ReportPeriod.of(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), 31))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void rejectsRangeLongerThanLimit() {
    assertThatThrownBy(
            () -> ReportPeriod.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 100))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }
}
