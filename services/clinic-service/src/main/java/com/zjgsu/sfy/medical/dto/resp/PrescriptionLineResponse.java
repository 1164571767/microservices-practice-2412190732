package com.zjgsu.sfy.medical.dto.resp;

import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "处方中的一条用药明细")
public record PrescriptionLineResponse(
    @Schema(description = "明细 ID", example = "7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f") UUID id,
    @Schema(description = "药品名称", example = "阿莫西林") String drugName,
    @Schema(description = "规格", example = "0.5g") String specification,
    @Schema(description = "单次剂量", example = "1片") String dosage,
    @Schema(description = "用药频次", example = "每日2次") String frequency,
    @Schema(description = "发药总数量", example = "6") int quantity,
    @Schema(description = "用药天数", example = "5") int days,
    @Schema(description = "备注", example = "饭后服用") String note) {

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
