package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.pharmacy.entity.DispenseRecord;
import com.zjgsu.sfy.pharmacy.repository.DispenseItemRepository;
import com.zjgsu.sfy.pharmacy.repository.DispenseRecordRepository;
import com.zjgsu.sfy.pharmacy.service.result.DispenseDetail;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 发药记录的只读查询，供 API 回放与响应装配。 */
@Service
public class DispenseQueryService {

  private final DispenseRecordRepository dispenseRecordRepository;
  private final DispenseItemRepository dispenseItemRepository;

  public DispenseQueryService(
      DispenseRecordRepository dispenseRecordRepository,
      DispenseItemRepository dispenseItemRepository) {
    this.dispenseRecordRepository = dispenseRecordRepository;
    this.dispenseItemRepository = dispenseItemRepository;
  }

  @Transactional(readOnly = true)
  public Optional<DispenseDetail> findByPrescriptionId(UUID prescriptionId) {
    return dispenseRecordRepository.findByPrescriptionId(prescriptionId).map(this::toDetail);
  }

  @Transactional(readOnly = true)
  public Optional<DispenseDetail> findById(UUID dispenseId) {
    return dispenseRecordRepository.findById(dispenseId).map(this::toDetail);
  }

  private DispenseDetail toDetail(DispenseRecord record) {
    return new DispenseDetail(
        record, dispenseItemRepository.findByDispenseIdOrderByDrugNameAsc(record.getId()));
  }
}
