package com.zjgsu.sfy.billing.service.command;

import java.util.List;
import java.util.UUID;

public record CreateBillCommand(
    UUID medicalRecordId, UUID prescriptionId, List<BillLineCommand> items) {

  public CreateBillCommand {
    items = List.copyOf(items);
  }
}
