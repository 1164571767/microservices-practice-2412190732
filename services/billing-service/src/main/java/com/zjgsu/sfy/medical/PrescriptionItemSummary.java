package com.zjgsu.sfy.medical;

import java.util.UUID;

/** 处方明细的只读视图。M4 内嵌药品名称 / 规格，pharmacy 按此匹配药品目录。 */
public record PrescriptionItemSummary(
    UUID id, String drugName, String specification, int quantity) {}
