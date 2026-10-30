package com.zjgsu.sfy.billing.dto.resp;

import com.zjgsu.sfy.billing.service.result.BillDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "账单详情（含明细、支付记录与操作流水）")
public record BillResponse(
    @Schema(description = "账单 ID", example = "9a1f2b3c-4d5e-4f60-8a7b-1c2d3e4f5a6b") UUID id,
    @Schema(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601") UUID petId,
    @Schema(description = "病历 ID", example = "9d1e3f5a-7b9c-4d1e-8f2a-3b4c5d6e7f80")
        UUID medicalRecordId,
    @Schema(description = "处方 ID，未关联处方为空", example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID prescriptionId,
    @Schema(description = "账单总金额，单位元", example = "75.00") BigDecimal totalAmount,
    @Schema(description = "币种，固定 CNY", example = "CNY") String currency,
    @Schema(
            description = "账单状态：UNPAID 待支付 / PAID 已支付 / REFUNDED 已退款 / VOID 已作废",
            example = "UNPAID")
        String status,
    @Schema(description = "开单人（账号）ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
        UUID createdBy,
    @Schema(description = "创建时间（UTC）", example = "2026-10-20T01:05:00Z") Instant createdAt,
    @Schema(description = "最后更新时间（UTC）", example = "2026-10-20T02:00:00Z") Instant updatedAt,
    @Schema(description = "账单明细") List<BillItemResponse> items,
    @Schema(description = "支付记录，未支付为空") PaymentResponse payment,
    @Schema(description = "操作流水，按时间升序") List<BillOperationResponse> operations) {

  public static BillResponse from(BillDetail detail) {
    return new BillResponse(
        detail.bill().getId(),
        detail.bill().getPetId(),
        detail.bill().getMedicalRecordId(),
        detail.bill().getPrescriptionId(),
        detail.bill().getTotalAmount(),
        detail.bill().getCurrency(),
        detail.bill().getStatus().name(),
        detail.bill().getCreatedBy(),
        detail.bill().getCreatedAt(),
        detail.bill().getUpdatedAt(),
        detail.items().stream().map(BillItemResponse::from).toList(),
        detail.payment().map(PaymentResponse::from).orElse(null),
        detail.operations().stream().map(BillOperationResponse::from).toList());
  }
}
