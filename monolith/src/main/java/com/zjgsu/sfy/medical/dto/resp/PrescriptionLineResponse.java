package com.zjgsu.sfy.medical.dto.resp;

import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import java.util.UUID;

public record PrescriptionLineResponse(
    UUID id,
    String drugName,
    String specification,
    String dosage,
    String frequency,
    int quantity,
    int days,
    String note) {

  public static PrescriptionLineResponse from(PrescriptionItem item) {
    return new PrescriptionLineResponse(
        item.getId(),
        item.getDrugName(),
        item.getSpecification(),
        item.getDosage(),
        item.getFrequency(),
        item.getQuantity(),
        item.getDays(),
        item.getNote());
  }
}
