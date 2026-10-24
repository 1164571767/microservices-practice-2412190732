package com.zjgsu.sfy.billing.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 收费项目启用状态；停用后不能再用于生成新账单。 */
@Schema(description = "收费项目状态：ACTIVE 启用 / INACTIVE 已停用（不能用于生成新账单）")
public enum ChargeItemStatus {
  ACTIVE,
  INACTIVE
}
