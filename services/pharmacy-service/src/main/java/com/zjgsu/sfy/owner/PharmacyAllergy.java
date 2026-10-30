package com.zjgsu.sfy.owner;

/** 当前有效过敏提示，不含更正操作及账号身份信息。 */
public record PharmacyAllergy(String allergen, String severity, String note) {}
