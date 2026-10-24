package com.zjgsu.sfy.reporting.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.reporting.dto.resp.DrugConsumptionResponse;
import com.zjgsu.sfy.reporting.dto.resp.RevenueReportResponse;
import com.zjgsu.sfy.reporting.dto.resp.VisitReportResponse;
import com.zjgsu.sfy.reporting.service.DrugConsumptionReportService;
import com.zjgsu.sfy.reporting.service.RevenueReportService;
import com.zjgsu.sfy.reporting.service.VisitReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统计报表查询接口；统计口径与权限矩阵见 docs/api/reporting.md。 */
@RestController
@RequestMapping("/api/reporting")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "统计报表", description = "门诊量、收入与药品消耗统计；仅管理员可查，只有读接口")
public class ReportingController {

  private final VisitReportService visitReportService;
  private final RevenueReportService revenueReportService;
  private final DrugConsumptionReportService drugConsumptionReportService;
  private final IdentityApi identityApi;

  public ReportingController(
      VisitReportService visitReportService,
      RevenueReportService revenueReportService,
      DrugConsumptionReportService drugConsumptionReportService,
      IdentityApi identityApi) {
    this.visitReportService = visitReportService;
    this.revenueReportService = revenueReportService;
    this.drugConsumptionReportService = drugConsumptionReportService;
    this.identityApi = identityApi;
  }

  @GetMapping("/visits")
  @Operation(summary = "门诊量与医生工作量", description = "仅管理员。统计区间内的就诊人次、按日趋势和各医生接诊量；区间含首尾，跨度不能超过 366 天。")
  public VisitReportResponse visits(
      @Parameter(description = "统计起始日（含），格式 yyyy-MM-dd", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "统计结束日（含），格式 yyyy-MM-dd", example = "2026-10-31")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(description = "只看某位医生，可空", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
          @RequestParam(required = false)
          UUID doctorId) {
    return visitReportService.report(identityApi.currentUser(), from, to, doctorId);
  }

  @GetMapping("/revenue")
  @Operation(
      summary = "收入统计",
      description = "仅管理员。统计区间内的支付金额、退款金额和净收入（净收入 = 支付 − 退款），并给出按日明细；区间含首尾。")
  public RevenueReportResponse revenue(
      @Parameter(description = "统计起始日（含），格式 yyyy-MM-dd", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "统计结束日（含），格式 yyyy-MM-dd", example = "2026-10-31")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return revenueReportService.report(identityApi.currentUser(), from, to);
  }

  @GetMapping("/drug-consumption")
  @Operation(summary = "药品消耗统计", description = "仅管理员。按发药记录统计各药品的消耗数量；汇总值覆盖整个查询区间，分页只作用于药品行。")
  public DrugConsumptionResponse drugConsumption(
      @Parameter(description = "统计起始日（含），格式 yyyy-MM-dd", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "统计结束日（含），格式 yyyy-MM-dd", example = "2026-10-31")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(description = "只看某种药品，可空", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d")
          @RequestParam(required = false)
          UUID drugId,
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size) {
    return drugConsumptionReportService.report(
        identityApi.currentUser(), from, to, drugId, page, size);
  }
}
