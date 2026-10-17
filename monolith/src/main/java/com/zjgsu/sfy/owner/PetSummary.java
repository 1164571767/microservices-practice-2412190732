package com.zjgsu.sfy.owner;

import java.util.UUID;

/** 宠物档案的只读投影，供其他模块做归属/存在性判断。 */
public record PetSummary(UUID id, UUID ownerId, String name, String species) {}
