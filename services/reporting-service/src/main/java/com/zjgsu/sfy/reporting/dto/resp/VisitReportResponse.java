package com.zjgsu.sfy.reporting.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 门诊量与医生工作量报表。{@code daily} 只包含有就诊记录的日期，按日期升序。 */
@Schema(description = "门诊量与医生工作量报表")
public record VisitReportResponse(
    @Schema(description = "统计起始日（含）", example = "2026-10-01") LocalDate from,
    @Schema(description = "统计结束日（含）", example = "2026-10-31") LocalDate to,
    @Schema(description = "区间内就诊总人次", example = "12") long totalVisits,
    @Schema(description = "按日趋势，只含有就诊记录的日期，日期升序") List<DailyVisit> daily,
    @Schema(description = "各医生接诊量") List<DoctorWorkload> doctors) {

  @Schema(description = "某一天的就诊量")
  public record DailyVisit(
      @Schema(description = "日期", example = "2026-10-01") LocalDate date,
      @Schema(description = "当天就诊人次", example = "3") long visits) {}

  /** 医生工作量；{@code doctorName} 解析失败时为 {@code null}，不影响统计结果。 */
  @Schema(description = "医生工作量")
  public record DoctorWorkload(
      @Schema(description = "医生（账号）ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
          UUID doctorId,
      @Schema(description = "医生姓名，解析不到时为空", example = "李医生") String doctorName,
      @Schema(description = "接诊人次", example = "7") long visits) {}
}
