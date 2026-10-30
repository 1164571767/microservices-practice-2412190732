package com.zjgsu.sfy.billing.service.command;

import java.util.UUID;

public record BillLineCommand(UUID chargeItemId, int quantity) {}
