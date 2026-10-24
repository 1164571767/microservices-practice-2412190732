package com.zjgsu.sfy.reporting.service.listener;

import com.zjgsu.sfy.medical.MedicalRecordCreated;
import com.zjgsu.sfy.reporting.common.ReportingProperties;
import com.zjgsu.sfy.reporting.entity.VisitFact;
import com.zjgsu.sfy.reporting.service.ReportFactWriter;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 订阅 medical 的病历创建事件，投影门诊量与医生工作量。 */
@Component
public class VisitReportListener {

  private final ReportingProperties properties;
  private final ReportFactWriter writer;

  public VisitReportListener(ReportingProperties properties, ReportFactWriter writer) {
    this.properties = properties;
    this.writer = writer;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onMedicalRecordCreated(MedicalRecordCreated event) {
    writer.recordVisit(
        event.recordId(),
        () -> {
          LocalDate statDate = LocalDate.ofInstant(event.visitedAt(), properties.zoneId());
          return VisitFact.create(
              event.recordId(), event.petId(), event.doctorId(), event.visitedAt(), statDate);
        });
  }
}
