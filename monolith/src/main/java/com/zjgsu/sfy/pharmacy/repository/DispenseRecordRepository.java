package com.zjgsu.sfy.pharmacy.repository;

import com.zjgsu.sfy.pharmacy.entity.DispenseRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispenseRecordRepository extends JpaRepository<DispenseRecord, UUID> {

  Optional<DispenseRecord> findByPrescriptionId(UUID prescriptionId);
}
