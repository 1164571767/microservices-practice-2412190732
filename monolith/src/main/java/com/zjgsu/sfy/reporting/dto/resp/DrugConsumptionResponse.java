package com.zjgsu.sfy.reporting.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 药品消耗报表：按发药时药品快照聚合，分页只作用于药品行，汇总值覆盖整个查询区间。 */
@Schema(description = "药品消耗报表")
public record DrugConsumptionResponse(
    @Schema(description = "统计起始日（含）", example = "2026-10-01") LocalDate from,
    @Schema(description = "统计结束日（含）", example = "2026-10-31") LocalDate to,
    @Schema(description = "区间内消耗总数量，不随分页变化", example = "120") long totalQuantity,
    @Schema(description = "区间内发药单数，不随分页变化", example = "35") long totalDispenses,
    @Schema(description = "当前页的药品消耗行") List<DrugConsumptionLine> items,
    @Schema(description = "当前页码，从 0 开始", example = "0") int page,
    @Schema(description = "每页条数", example = "20") int size,
    @Schema(description = "药品行总数", example = "8") long totalElements,
    @Schema(description = "总页数", example = "1") int totalPages) {

  @Schema(description = "一种药品的消耗汇总")
  public record DrugConsumptionLine(
      @Schema(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d") UUID drugId,
      @Schema(description = "药品名称（发药时快照）", example = "阿莫西林") String drugName,
      @Schema(description = "规格（发药时快照）", example = "0.5g") String specification,
      @Schema(description = "单位（发药时快照）", example = "盒") String unit,
      @Schema(description = "消耗数量", example = "30") long quantity,
      @Schema(description = "涉及的发药单数", example = "12") long dispenseCount) {}
}
