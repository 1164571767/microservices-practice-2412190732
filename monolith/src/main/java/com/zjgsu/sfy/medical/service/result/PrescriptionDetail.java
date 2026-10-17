package com.zjgsu.sfy.medical.service.result;

import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import java.util.List;

/** 处方 + 明细行，供 API 组合输出。 */
public record PrescriptionDetail(Prescription prescription, List<PrescriptionItem> items) {}
