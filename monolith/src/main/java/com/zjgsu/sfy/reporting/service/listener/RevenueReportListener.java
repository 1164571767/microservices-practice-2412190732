package com.zjgsu.sfy.reporting.service.listener;

import com.zjgsu.sfy.billing.BillPaid;
import com.zjgsu.sfy.billing.BillRefunded;
import com.zjgsu.sfy.reporting.common.ReportingProperties;
import com.zjgsu.sfy.reporting.common.RevenueKind;
import com.zjgsu.sfy.reporting.entity.RevenueFact;
import com.zjgsu.sfy.reporting.service.ReportFactWriter;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 订阅 billing 的支付 / 退款事件，分别投影为收入与退款事实。 */
@Component
public class RevenueReportListener {

  private final ReportingProperties properties;
  private final ReportFactWriter writer;

  public RevenueReportListener(ReportingProperties properties, ReportFactWriter writer) {
    this.properties = properties;
    this.writer = writer;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBillPaid(BillPaid event) {
    writer.recordRevenue(
        RevenueKind.PAID,
        event.billId(),
        () ->
            RevenueFact.create(
                event.billId(),
                event.paymentId(),
                RevenueKind.PAID,
                event.amount(),
                event.currency(),
                event.petId(),
                event.paidAt(),
                statDate(event.paidAt())));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBillRefunded(BillRefunded event) {
    writer.recordRevenue(
        RevenueKind.REFUNDED,
        event.billId(),
        () ->
            RevenueFact.create(
                event.billId(),
                event.paymentId(),
                RevenueKind.REFUNDED,
                event.amount(),
                event.currency(),
                event.petId(),
                event.refundedAt(),
                statDate(event.refundedAt())));
  }

  private LocalDate statDate(Instant occurredAt) {
    return LocalDate.ofInstant(occurredAt, properties.zoneId());
  }
}
