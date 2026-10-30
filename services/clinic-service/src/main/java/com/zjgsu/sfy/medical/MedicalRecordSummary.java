package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.UUID;

/** 病历索引的只读投影，供其他模块聚合展示，不含病历明细。 */
public record MedicalRecordSummary(
    UUID recordId, UUID petId, Instant visitedAt, String diagnosis, UUID doctorId) {}
