package com.zjgsu.sfy.owner;

import java.util.UUID;

/** 接待所需的档案摘要，不包含诊疗或账号认证资料。归档资料仍可用于历史追溯。 */
public record PatientContactSummary(
    UUID petId,
    String petName,
    String species,
    UUID ownerId,
    String ownerName,
    String ownerPhone,
    boolean petArchived,
    boolean ownerArchived) {}
