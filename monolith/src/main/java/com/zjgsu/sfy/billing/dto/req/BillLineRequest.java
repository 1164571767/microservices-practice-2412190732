package com.zjgsu.sfy.billing.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "账单中的一行明细")
public record BillLineRequest(
    @Schema(description = "收费项目 ID，必须是启用状态", example = "0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        @NotNull
        UUID chargeItemId,
    @Schema(description = "数量，≥ 1；同一账单内同一收费项目只能出现一次", example = "1") @Min(1) int quantity) {}
