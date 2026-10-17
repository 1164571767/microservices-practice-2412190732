package com.zjgsu.sfy.medical.service.result;

import com.zjgsu.sfy.medical.entity.MedicalRecord;
import java.util.List;

/** 病历 + 其处方（历史查询用，处方经批量加载，避免 N+1）。 */
public record MedicalRecordDetail(MedicalRecord record, List<PrescriptionDetail> prescriptions) {}
