package com.zjgsu.sfy.pharmacy.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 药品目录状态；停用的药品不能再发药，但历史库存与流水仍保留。 */
@Schema(description = "药品状态：ACTIVE 启用 / INACTIVE 已停用（不能再发药）")
public enum DrugStatus {
  ACTIVE,
  INACTIVE
}
