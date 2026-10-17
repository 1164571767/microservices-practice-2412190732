package com.zjgsu.sfy.medical.repository;

import com.zjgsu.sfy.medical.entity.MedicalRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

  /** 病历索引：按就诊时间倒序，供其他模块经 MedicalApi 只读聚合。 */
  List<MedicalRecord> findByPetIdOrderByVisitedAtDesc(UUID petId);

  Page<MedicalRecord> findByPetId(UUID petId, Pageable pageable);
}
