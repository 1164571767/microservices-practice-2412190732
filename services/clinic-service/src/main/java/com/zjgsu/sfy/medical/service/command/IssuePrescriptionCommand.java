package com.zjgsu.sfy.medical.service.command;

import java.util.List;

public record IssuePrescriptionCommand(List<PrescriptionLineCommand> items) {}
