package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 药房核对所需的最小处方视图，不含诊断和病历正文。 */
public record PharmacyPrescription(
    UUID id, UUID medicalRecordId, UUID petId, UUID doctorId, Instant issuedAt, List<Line> items) {
  public record Line(
      UUID id,
      String drugName,
      String specification,
      String dosage,
      String frequency,
      int quantity,
      int days,
      String note) {}
}
