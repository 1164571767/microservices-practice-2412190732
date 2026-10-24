package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 处方的只读视图，供其他模块（pharmacy 发药）读取，不暴露 medical 实体。 */
public record PrescriptionSummary(
    UUID id,
    UUID medicalRecordId,
    UUID petId,
    UUID doctorId,
    Instant issuedAt,
    List<PrescriptionItemSummary> items) {

  public PrescriptionSummary {
    items = List.copyOf(items);
  }
}
