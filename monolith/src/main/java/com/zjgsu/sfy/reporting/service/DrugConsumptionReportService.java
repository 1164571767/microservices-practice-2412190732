package com.zjgsu.sfy.reporting.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.reporting.common.ReportingProperties;
import com.zjgsu.sfy.reporting.dto.resp.DrugConsumptionResponse;
import com.zjgsu.sfy.reporting.repository.DrugConsumptionFactRepository;
import com.zjgsu.sfy.reporting.service.result.ReportPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 药品消耗统计：按发药明细快照聚合，汇总值覆盖整个查询区间，分页只作用于药品行。 */
@Service
public class DrugConsumptionReportService {

  private static final int MAX_PAGE_SIZE = 100;

  private final DrugConsumptionFactRepository drugConsumptionFactRepository;
  private final ReportingProperties properties;

  public DrugConsumptionReportService(
      DrugConsumptionFactRepository drugConsumptionFactRepository, ReportingProperties properties) {
    this.drugConsumptionFactRepository = drugConsumptionFactRepository;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public DrugConsumptionResponse report(
      CurrentUser requester, LocalDate from, LocalDate to, UUID drugId, int page, int size) {
    requireAdmin(requester);
    ReportPeriod period = ReportPeriod.of(from, to, properties.maxRangeDays());
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    List<DrugConsumptionFactRepository.DrugConsumption> rows =
        drugId == null
            ? drugConsumptionFactRepository.summarize(period.from(), period.to())
            : drugConsumptionFactRepository.summarizeByDrug(period.from(), period.to(), drugId);
    long totalQuantity =
        rows.stream().mapToLong(DrugConsumptionFactRepository.DrugConsumption::getQuantity).sum();
    long totalDispenses =
        drugId == null
            ? drugConsumptionFactRepository.countDispenses(period.from(), period.to())
            : drugConsumptionFactRepository.countDispensesByDrug(
                period.from(), period.to(), drugId);
    return new DrugConsumptionResponse(
        period.from(),
        period.to(),
        totalQuantity,
        totalDispenses,
        page(rows, safePage, safeSize),
        safePage,
        safeSize,
        rows.size(),
        rows.isEmpty() ? 0 : (int) Math.ceil((double) rows.size() / safeSize));
  }

  private static List<DrugConsumptionResponse.DrugConsumptionLine> page(
      List<DrugConsumptionFactRepository.DrugConsumption> rows, int page, int size) {
    long fromIndex = Math.min((long) page * size, rows.size());
    long toIndex = Math.min(fromIndex + size, rows.size());
    return rows.subList((int) fromIndex, (int) toIndex).stream()
        .map(
            row ->
                new DrugConsumptionResponse.DrugConsumptionLine(
                    row.getDrugId(),
                    row.getDrugName(),
                    row.getSpecification(),
                    row.getUnit(),
                    row.getQuantity(),
                    row.getDispenseCount()))
        .toList();
  }

  private static void requireAdmin(CurrentUser requester) {
    if (!requester.isAdmin()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }
}
