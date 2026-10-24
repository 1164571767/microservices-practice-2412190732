package com.zjgsu.sfy.pharmacy.dto.resp;

import com.zjgsu.sfy.pharmacy.entity.DispenseItem;
import com.zjgsu.sfy.pharmacy.service.result.DispenseDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "发药记录（含发药明细）")
public record DispenseResponse(
    @Schema(description = "发药记录 ID", example = "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e") UUID id,
    @Schema(description = "处方 ID", example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID prescriptionId,
    @Schema(description = "病历 ID", example = "9d1e3f5a-7b9c-4d1e-8f2a-3b4c5d6e7f80")
        UUID medicalRecordId,
    @Schema(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "开方医生 ID", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID doctorId,
    @Schema(description = "发药人（账号）ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
        UUID dispensedBy,
    @Schema(description = "发药时间（UTC）", example = "2026-09-15T02:00:00Z") Instant dispensedAt,
    @Schema(description = "发药明细，同一药品已按药品聚合") List<DispenseItemResponse> items) {

  public static DispenseResponse from(DispenseDetail detail) {
    return new DispenseResponse(
        detail.record().getId(),
        detail.record().getPrescriptionId(),
        detail.record().getMedicalRecordId(),
        detail.record().getPetId(),
        detail.record().getDoctorId(),
        detail.record().getDispensedBy(),
        detail.record().getDispensedAt(),
        detail.items().stream().map(DispenseItemResponse::from).toList());
  }

  @Schema(description = "一条发药明细")
  public record DispenseItemResponse(
      @Schema(description = "药品 ID", example = "6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d") UUID drugId,
      @Schema(description = "药品名称（发药时的快照）", example = "阿莫西林") String drugName,
      @Schema(description = "规格（快照）", example = "0.5g") String specification,
      @Schema(description = "单位（快照）", example = "盒") String unit,
      @Schema(description = "发药数量", example = "3") int quantity,
      @Schema(description = "扣减后的库存", example = "7") int stockAfter) {

    public static DispenseItemResponse from(DispenseItem item) {
      return new DispenseItemResponse(
          item.getDrugId(),
          item.getDrugName(),
          item.getSpecification(),
          item.getUnit(),
          item.getQuantity(),
          item.getStockAfter());
    }
  }
}
