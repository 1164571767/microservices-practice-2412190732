package com.zjgsu.sfy.pharmacy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 处方发药完成事件，携带按药品聚合的发药明细，供通知 / 统计等下游模块订阅。 */
public record PrescriptionDispensed(
    UUID dispenseId,
    UUID prescriptionId,
    UUID medicalRecordId,
    UUID petId,
    UUID dispensedBy,
    Instant dispensedAt,
    List<DispensedItem> items) {

  public PrescriptionDispensed {
    items = List.copyOf(items);
  }

  /** 发药明细的自包含快照；名称 / 规格 / 单位取自发药时的药品目录。 */
  public record DispensedItem(
      UUID drugId, String drugName, String specification, String unit, int quantity) {}
}
