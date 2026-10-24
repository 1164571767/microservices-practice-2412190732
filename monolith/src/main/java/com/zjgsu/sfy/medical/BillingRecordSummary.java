package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.UUID;

/** 收费选择所需的最小索引，不包含主诉、诊断、检查或治疗内容。 */
public record BillingRecordSummary(
    UUID recordId, UUID petId, UUID doctorId, Instant visitedAt, UUID prescriptionId) {}
