package com.zjgsu.sfy.billing.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 退款 / 作废原因；写入审计流水，必须可追溯。 */
@Schema(description = "退款或作废的原因")
public record BillReasonRequest(
    @Schema(description = "原因说明，最多 255 字，会写入账单操作流水", example = "主人取消服务") @NotBlank @Size(max = 255)
        String reason) {}
