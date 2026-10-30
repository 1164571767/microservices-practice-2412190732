package com.zjgsu.sfy.billing.service.command;

import java.math.BigDecimal;

public record CreateChargeItemCommand(String name, BigDecimal amount) {}
