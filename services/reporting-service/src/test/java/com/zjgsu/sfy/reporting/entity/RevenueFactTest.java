package com.zjgsu.sfy.reporting.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zjgsu.sfy.reporting.common.RevenueKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevenueFactTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-20T01:00:00Z");
  private static final LocalDate STAT_DATE = LocalDate.of(2026, 10, 20);

  @Test
  void acceptsValidPaidFact() {
    RevenueFact fact =
        RevenueFact.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            RevenueKind.PAID,
            new BigDecimal("100.00"),
            "CNY",
            UUID.randomUUID(),
            OCCURRED_AT,
            STAT_DATE);

    assertThat(fact.getKind()).isEqualTo(RevenueKind.PAID);
    assertThat(fact.getAmount()).isEqualByComparingTo("100.00");
    assertThat(fact.getStatDate()).isEqualTo(STAT_DATE);
  }

  @Test
  void rejectsNegativeAmount() {
    assertThatThrownBy(
            () ->
                RevenueFact.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    RevenueKind.REFUNDED,
                    new BigDecimal("-1.00"),
                    "CNY",
                    UUID.randomUUID(),
                    OCCURRED_AT,
                    STAT_DATE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  @Test
  void rejectsMoreThanTwoDecimalPlaces() {
    assertThatThrownBy(
            () ->
                RevenueFact.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    RevenueKind.PAID,
                    new BigDecimal("1.005"),
                    "CNY",
                    UUID.randomUUID(),
                    OCCURRED_AT,
                    STAT_DATE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("decimal places");
  }

  @Test
  void rejectsNonCnyCurrency() {
    assertThatThrownBy(
            () ->
                RevenueFact.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    RevenueKind.PAID,
                    new BigDecimal("1.00"),
                    "USD",
                    UUID.randomUUID(),
                    OCCURRED_AT,
                    STAT_DATE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CNY");
  }
}
