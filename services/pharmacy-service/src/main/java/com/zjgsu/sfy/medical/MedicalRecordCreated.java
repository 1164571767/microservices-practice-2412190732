package com.zjgsu.sfy.medical;

import java.time.Instant;
import java.util.UUID;

/** 病历创建事件；供统计等下游模块投影门诊量与医生工作量，不暴露病历实体。 */
public record MedicalRecordCreated(
    UUID recordId, UUID petId, UUID doctorId, Instant visitedAt, Instant recordedAt) {}
