package com.zjgsu.sfy.reporting.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收入报表：支付与退款分开累计，净收入 = 支付合计 − 退款合计。 */
@Schema(description = "收入报表")
public record RevenueReportResponse(
    @Schema(description = "统计起始日（含）", example = "2026-10-01") LocalDate from,
    @Schema(description = "统计结束日（含）", example = "2026-10-31") LocalDate to,
    @Schema(description = "币种，固定 CNY", example = "CNY") String currency,
    @Schema(description = "支付金额合计，单位元", example = "1500.00") BigDecimal paidAmount,
    @Schema(description = "退款金额合计，单位元", example = "200.00") BigDecimal refundedAmount,
    @Schema(description = "净收入 = 支付合计 − 退款合计，单位元", example = "1300.00") BigDecimal netAmount,
    @Schema(description = "支付笔数", example = "20") long paidCount,
    @Schema(description = "退款笔数", example = "3") long refundedCount,
    @Schema(description = "按日明细") List<DailyRevenue> daily) {

  @Schema(description = "某一天的收支")
  public record DailyRevenue(
      @Schema(description = "日期", example = "2026-10-01") LocalDate date,
      @Schema(description = "当日支付金额，单位元", example = "300.00") BigDecimal paidAmount,
      @Schema(description = "当日退款金额，单位元", example = "50.00") BigDecimal refundedAmount,
      @Schema(description = "当日净收入，单位元", example = "250.00") BigDecimal netAmount) {}
}
