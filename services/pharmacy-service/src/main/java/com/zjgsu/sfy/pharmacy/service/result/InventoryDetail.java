package com.zjgsu.sfy.pharmacy.service.result;

import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.pharmacy.entity.Inventory;

/** 库存 + 对应药品目录，供 API 组合输出。 */
public record InventoryDetail(Inventory inventory, Drug drug) {}
