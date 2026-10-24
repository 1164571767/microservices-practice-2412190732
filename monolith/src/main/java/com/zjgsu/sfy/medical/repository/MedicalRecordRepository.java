package com.zjgsu.sfy.medical.repository;

import com.zjgsu.sfy.medical.entity.MedicalRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

  java.util.Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

  List<MedicalRecord> findByAppointmentIdIn(java.util.Collection<UUID> appointmentIds);

  /** 病历索引：按就诊时间倒序，供其他模块经 MedicalApi 只读聚合。 */
  List<MedicalRecord> findByPetIdOrderByVisitedAtDesc(UUID petId);

  Page<MedicalRecord> findByPetId(UUID petId, Pageable pageable);

  /** 按就诊时间窗口读取病历索引，供通知等下游模块聚合（半开区间）。 */
  List<MedicalRecord> findByVisitedAtGreaterThanEqualAndVisitedAtLessThanOrderByVisitedAtAsc(
      Instant fromInclusive, Instant toExclusive);
}
