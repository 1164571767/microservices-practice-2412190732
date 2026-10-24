package com.zjgsu.sfy.pharmacy.service.result;

import com.zjgsu.sfy.pharmacy.entity.DispenseItem;
import com.zjgsu.sfy.pharmacy.entity.DispenseRecord;
import java.util.List;

/** 发药记录 + 明细，供 API 组合输出。 */
public record DispenseDetail(DispenseRecord record, List<DispenseItem> items) {

  public DispenseDetail {
    items = List.copyOf(items);
  }
}
