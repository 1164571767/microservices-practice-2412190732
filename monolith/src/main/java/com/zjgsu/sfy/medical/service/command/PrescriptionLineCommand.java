package com.zjgsu.sfy.medical.service.command;

public record PrescriptionLineCommand(
    String drugName,
    String specification,
    String dosage,
    String frequency,
    int quantity,
    int days,
    String note) {}
