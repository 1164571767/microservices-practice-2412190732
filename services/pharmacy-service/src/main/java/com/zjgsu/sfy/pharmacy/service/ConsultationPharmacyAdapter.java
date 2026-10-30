package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.medical.ConsultationHandoffLookup;
import com.zjgsu.sfy.pharmacy.repository.DispenseRecordRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConsultationPharmacyAdapter implements ConsultationHandoffLookup.Pharmacy {
  private final DispenseRecordRepository records;

  public ConsultationPharmacyAdapter(DispenseRecordRepository records) {
    this.records = records;
  }

  @Override
  public ConsultationHandoffLookup.DispenseProgress findDispense(UUID id) {
    return records
        .findByPrescriptionId(id)
        .map(d -> new ConsultationHandoffLookup.DispenseProgress(d.getId(), d.getDispensedAt()))
        .orElse(null);
  }
}
