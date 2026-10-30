package com.zjgsu.sfy.pharmacy.service.command;

public record CreateDrugCommand(
    String name, String specification, String unit, int lowStockThreshold, int initialQuantity) {}
