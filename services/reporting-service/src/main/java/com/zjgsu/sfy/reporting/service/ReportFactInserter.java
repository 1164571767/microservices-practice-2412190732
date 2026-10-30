package com.zjgsu.sfy.reporting.service;

import com.zjgsu.sfy.reporting.entity.DrugConsumptionFact;
import com.zjgsu.sfy.reporting.entity.RevenueFact;
import com.zjgsu.sfy.reporting.entity.VisitFact;
import com.zjgsu.sfy.reporting.repository.DrugConsumptionFactRepository;
import com.zjgsu.sfy.reporting.repository.RevenueFactRepository;
import com.zjgsu.sfy.reporting.repository.VisitFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统计事实写入的独立事务边界。
 *
 * <p>每条事实使用新事务，与事件发布方（已提交）隔离；唯一键冲突在提交时抛出，由事务外的 {@link ReportFactWriter} 视为「重复投递」忽略。
 */
@Service
public class ReportFactInserter {

  private final VisitFactRepository visitRepository;
  private final RevenueFactRepository revenueRepository;
  private final DrugConsumptionFactRepository drugConsumptionRepository;

  public ReportFactInserter(
      VisitFactRepository visitRepository,
      RevenueFactRepository revenueRepository,
      DrugConsumptionFactRepository drugConsumptionRepository) {
    this.visitRepository = visitRepository;
    this.revenueRepository = revenueRepository;
    this.drugConsumptionRepository = drugConsumptionRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void insertVisit(VisitFact fact) {
    visitRepository.saveAndFlush(fact);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void insertRevenue(RevenueFact fact) {
    revenueRepository.saveAndFlush(fact);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void insertDrugConsumption(DrugConsumptionFact fact) {
    drugConsumptionRepository.saveAndFlush(fact);
  }
}
