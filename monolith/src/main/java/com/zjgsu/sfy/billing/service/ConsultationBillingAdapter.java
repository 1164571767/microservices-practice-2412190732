package com.zjgsu.sfy.billing.service;

import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.repository.BillRepository;
import com.zjgsu.sfy.medical.ConsultationHandoffLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConsultationBillingAdapter implements ConsultationHandoffLookup.Billing {
  private final BillRepository bills;

  public ConsultationBillingAdapter(BillRepository bills) {
    this.bills = bills;
  }

  @Override
  public ConsultationHandoffLookup.BillProgress findBill(UUID recordId) {
    return bills.findByMedicalRecordIdInAndStatusNot(List.of(recordId), BillStatus.VOID).stream()
        .findFirst()
        .map(b -> new ConsultationHandoffLookup.BillProgress(b.getId(), b.getStatus().name()))
        .orElse(null);
  }
}
