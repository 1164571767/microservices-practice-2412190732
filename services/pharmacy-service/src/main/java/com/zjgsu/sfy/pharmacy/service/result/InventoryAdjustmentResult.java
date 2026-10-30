package com.zjgsu.sfy.pharmacy.service.result;

import com.zjgsu.sfy.pharmacy.entity.InventoryAdjustment;

/** 一次库存调整的结果：调整流水 + 调整后的库存视图。 */
public record InventoryAdjustmentResult(
    InventoryDetail inventory, InventoryAdjustment adjustment) {}
