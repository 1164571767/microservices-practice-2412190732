package com.zjgsu.sfy.billing.service.command;

import java.math.BigDecimal;

public record UpdateChargeItemCommand(String name, BigDecimal amount, boolean active) {}
