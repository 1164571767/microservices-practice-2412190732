package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.UUID;

/** Consumer-owned read ports, implemented by billing/pharmacy to keep the module graph acyclic. */
public final class ConsultationHandoffLookup {
  private ConsultationHandoffLookup() {}

  public record BillProgress(UUID id, String status) {}

  public record DispenseProgress(UUID id, Instant dispensedAt) {}

  public interface Billing {
    BillProgress findBill(UUID medicalRecordId);
  }

  public interface Pharmacy {
    DispenseProgress findDispense(UUID prescriptionId);
  }
}
