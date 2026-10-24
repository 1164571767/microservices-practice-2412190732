package com.zjgsu.sfy.billing.dto.req;

import com.zjgsu.sfy.billing.service.command.BillLineCommand;
import com.zjgsu.sfy.billing.service.command.CreateBillCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "生成账单的请求体；金额由服务端计算，不接受客户端传入")
public record CreateBillRequest(
    @Schema(description = "病历 ID，账单的宠物与医生信息都从病历取", example = "9d1e3f5a-7b9c-4d1e-8f2a-3b4c5d6e7f80")
        @NotNull
        UUID medicalRecordId,
    @Schema(
            description = "处方 ID，可空；传入时必须属于同一病历和同一宠物",
            example = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e")
        UUID prescriptionId,
    @Schema(description = "账单明细，1–100 行；同一收费项目只能出现一次") @NotEmpty @Size(max = 100) @Valid
        List<BillLineRequest> items) {

  public CreateBillCommand toCommand() {
    return new CreateBillCommand(
        medicalRecordId,
        prescriptionId,
        items.stream()
            .map(item -> new BillLineCommand(item.chargeItemId(), item.quantity()))
            .toList());
  }
}
