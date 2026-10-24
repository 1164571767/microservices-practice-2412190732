package com.zjgsu.sfy.pharmacy.service.command;

public record AdjustInventoryCommand(int delta, String reason) {}
