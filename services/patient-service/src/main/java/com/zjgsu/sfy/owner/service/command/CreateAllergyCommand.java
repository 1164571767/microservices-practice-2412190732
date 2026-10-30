package com.zjgsu.sfy.owner.service.command;

import com.zjgsu.sfy.owner.common.AllergySeverity;

public record CreateAllergyCommand(String allergen, AllergySeverity severity, String note) {}
