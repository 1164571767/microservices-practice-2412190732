package com.zjgsu.sfy.pharmacy.repository;

import com.zjgsu.sfy.pharmacy.entity.PrescriptionTask;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface PrescriptionTaskRepository
    extends JpaRepository<PrescriptionTask, UUID>, JpaSpecificationExecutor<PrescriptionTask> {
  @Modifying
  @Query(
      value =
          "INSERT INTO pharmacy_prescription_task(prescription_id,pet_id,doctor_id,issued_at) VALUES (:id,:pet,:doctor,:issued) ON DUPLICATE KEY UPDATE prescription_id=VALUES(prescription_id)",
      nativeQuery = true)
  void upsert(UUID id, UUID pet, UUID doctor, Instant issued);
}
