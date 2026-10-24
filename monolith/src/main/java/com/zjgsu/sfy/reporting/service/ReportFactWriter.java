package com.zjgsu.sfy.reporting.service;

import com.zjgsu.sfy.reporting.common.RevenueKind;
import com.zjgsu.sfy.reporting.entity.DrugConsumptionFact;
import com.zjgsu.sfy.reporting.entity.RevenueFact;
import com.zjgsu.sfy.reporting.entity.VisitFact;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 幂等写入统计事实。
 *
 * <p>同一事件重复投递（重试 / 并发消费）由事实表唯一键兜底：唯一键冲突视为已消费并忽略。统计投影失败只记录日志与 metric， 不向发布方冒泡，避免统计问题回滚已提交的业务事务。
 */
@Service
public class ReportFactWriter {

  static final String METRIC_NAME = "petcare.reporting.facts";

  private static final Logger log = LoggerFactory.getLogger(ReportFactWriter.class);

  private final ReportFactInserter inserter;
  private final MeterRegistry meterRegistry;

  public ReportFactWriter(ReportFactInserter inserter, MeterRegistry meterRegistry) {
    this.inserter = inserter;
    this.meterRegistry = meterRegistry;
  }

  public void recordVisit(UUID sourceId, Supplier<VisitFact> fact) {
    write("visit", sourceId, () -> inserter.insertVisit(fact.get()));
  }

  public void recordRevenue(RevenueKind kind, UUID sourceId, Supplier<RevenueFact> fact) {
    write(
        "revenue-" + kind.name().toLowerCase(Locale.ROOT),
        sourceId,
        () -> inserter.insertRevenue(fact.get()));
  }

  public void recordDrugConsumption(UUID sourceId, Supplier<DrugConsumptionFact> fact) {
    write("drug_consumption", sourceId, () -> inserter.insertDrugConsumption(fact.get()));
  }

  private void write(String type, UUID sourceId, Runnable action) {
    try {
      action.run();
      counter(type, "created").increment();
    } catch (DataIntegrityViolationException ex) {
      counter(type, "duplicate").increment();
      log.atInfo()
          .addKeyValue("type", type)
          .addKeyValue("sourceId", sourceId)
          .log("reporting.fact_duplicate_ignored");
    } catch (RuntimeException ex) {
      counter(type, "failed").increment();
      log.atError()
          .addKeyValue("type", type)
          .addKeyValue("sourceId", sourceId)
          .setCause(ex)
          .log("reporting.fact_projection_failed");
    }
  }

  private Counter counter(String type, String outcome) {
    return Counter.builder(METRIC_NAME)
        .tag("type", type)
        .tag("outcome", outcome)
        .register(meterRegistry);
  }
}
