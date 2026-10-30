package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.UUID;

/** 药房只读任务索引的事务内事件，不包含诊断或病历正文。 */
public record PrescriptionIssued(
    UUID prescriptionId, UUID petId, UUID doctorId, Instant issuedAt) {}
