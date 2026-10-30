package com.zjgsu.sfy.pharmacy.service.command;

public record UpdateDrugCommand(
    String name, String specification, String unit, int lowStockThreshold, boolean active) {}
