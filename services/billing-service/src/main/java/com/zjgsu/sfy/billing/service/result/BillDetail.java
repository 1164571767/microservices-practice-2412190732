package com.zjgsu.sfy.billing.service.result;

import com.zjgsu.sfy.billing.entity.Bill;
import com.zjgsu.sfy.billing.entity.BillItem;
import com.zjgsu.sfy.billing.entity.BillOperation;
import com.zjgsu.sfy.billing.entity.PaymentRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 账单聚合视图：账单头 + 明细 + 支付记录（可空）+ 审计流水。 */
public record BillDetail(
    Bill bill,
    List<BillItem> items,
    Optional<PaymentRecord> payment,
    List<BillOperation> operations) {

  public BillDetail {
    Objects.requireNonNull(bill, "bill must not be null");
    items = List.copyOf(items);
    payment = payment == null ? Optional.empty() : payment;
    operations = List.copyOf(operations);
  }
}
