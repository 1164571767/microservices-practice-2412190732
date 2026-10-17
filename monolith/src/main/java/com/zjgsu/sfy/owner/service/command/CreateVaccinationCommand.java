package com.zjgsu.sfy.owner.service.command;

import java.time.LocalDate;

public record CreateVaccinationCommand(
    String vaccineName, LocalDate administeredOn, LocalDate nextDueOn, String note) {}
