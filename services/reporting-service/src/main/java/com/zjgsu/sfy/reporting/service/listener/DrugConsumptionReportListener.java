package com.zjgsu.sfy.reporting.service.listener;

import com.zjgsu.sfy.pharmacy.PrescriptionDispensed;
import com.zjgsu.sfy.reporting.common.ReportingProperties;
import com.zjgsu.sfy.reporting.entity.DrugConsumptionFact;
import com.zjgsu.sfy.reporting.service.ReportFactWriter;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 订阅 pharmacy 的发药完成事件，按发药明细投影药品消耗。 */
@Component
public class DrugConsumptionReportListener {

  private final ReportingProperties properties;
  private final ReportFactWriter writer;

  public DrugConsumptionReportListener(ReportingProperties properties, ReportFactWriter writer) {
    this.properties = properties;
    this.writer = writer;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPrescriptionDispensed(PrescriptionDispensed event) {
    for (PrescriptionDispensed.DispensedItem item : event.items()) {
      writer.recordDrugConsumption(
          event.dispenseId(),
          () ->
              DrugConsumptionFact.create(
                  event.dispenseId(),
                  item.drugId(),
                  item.drugName(),
                  item.specification(),
                  item.unit(),
                  item.quantity(),
                  event.dispensedAt(),
                  statDate(event)));
    }
  }

  private LocalDate statDate(PrescriptionDispensed event) {
    return LocalDate.ofInstant(event.dispensedAt(), properties.zoneId());
  }
}
