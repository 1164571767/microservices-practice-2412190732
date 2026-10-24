package com.zjgsu.sfy.reporting.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.reporting.common.ReportingProperties;
import com.zjgsu.sfy.reporting.common.RevenueKind;
import com.zjgsu.sfy.reporting.dto.resp.RevenueReportResponse;
import com.zjgsu.sfy.reporting.repository.RevenueFactRepository;
import com.zjgsu.sfy.reporting.service.result.ReportPeriod;
import com.zjgsu.sfy.reporting.util.Amounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 收入统计：按统计日聚合支付 / 退款事实，净收入 = 支付合计 − 退款合计。 */
@Service
public class RevenueReportService {

  private final RevenueFactRepository revenueFactRepository;
  private final ReportingProperties properties;

  public RevenueReportService(
      RevenueFactRepository revenueFactRepository, ReportingProperties properties) {
    this.revenueFactRepository = revenueFactRepository;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public RevenueReportResponse report(CurrentUser requester, LocalDate from, LocalDate to) {
    requireAdmin(requester);
    ReportPeriod period = ReportPeriod.of(from, to, properties.maxRangeDays());
    List<RevenueFactRepository.DailyRevenue> rows =
        revenueFactRepository.summarize(period.from(), period.to());

    Map<LocalDate, Totals> byDate = new TreeMap<>();
    Totals overall = Totals.empty();
    for (RevenueFactRepository.DailyRevenue row : rows) {
      byDate.merge(
          row.getStatDate(),
          Totals.single(row.getKind(), row.getAmount(), row.getEntries()),
          Totals::plus);
      overall = overall.plus(row.getKind(), row.getAmount(), row.getEntries());
    }
    List<RevenueReportResponse.DailyRevenue> daily =
        byDate.entrySet().stream()
            .map(
                entry ->
                    new RevenueReportResponse.DailyRevenue(
                        entry.getKey(),
                        entry.getValue().paid(),
                        entry.getValue().refunded(),
                        entry.getValue().net()))
            .toList();
    return new RevenueReportResponse(
        period.from(),
        period.to(),
        Amounts.CURRENCY,
        overall.paid(),
        overall.refunded(),
        overall.net(),
        overall.paidCount(),
        overall.refundedCount(),
        daily);
  }

  private record Totals(BigDecimal paid, BigDecimal refunded, long paidCount, long refundedCount) {

    static Totals empty() {
      return new Totals(Amounts.ZERO, Amounts.ZERO, 0, 0);
    }

    static Totals single(RevenueKind kind, BigDecimal amount, long count) {
      return empty().plus(kind, amount, count);
    }

    Totals plus(RevenueKind kind, BigDecimal amount, long count) {
      BigDecimal normalized = Amounts.normalize(amount);
      return kind == RevenueKind.PAID
          ? new Totals(paid.add(normalized), refunded, paidCount + count, refundedCount)
          : new Totals(paid, refunded.add(normalized), paidCount, refundedCount + count);
    }

    Totals plus(Totals other) {
      return new Totals(
          paid.add(other.paid),
          refunded.add(other.refunded),
          paidCount + other.paidCount,
          refundedCount + other.refundedCount);
    }

    BigDecimal net() {
      return Amounts.normalize(paid.subtract(refunded));
    }
  }

  private static void requireAdmin(CurrentUser requester) {
    if (!requester.isAdmin()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
