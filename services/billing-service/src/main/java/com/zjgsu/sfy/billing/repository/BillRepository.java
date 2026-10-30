package com.zjgsu.sfy.billing.repository;

import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.entity.Bill;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillRepository
    extends JpaRepository<Bill, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Bill> {

  boolean existsByMedicalRecordIdAndStatusNot(UUID recordId, BillStatus status);

  java.util.List<Bill> findByMedicalRecordIdInAndStatusNot(
      java.util.Collection<UUID> ids, BillStatus status);

  /** 支付 / 退款 / 作废前锁定账单行，串行化同一账单的状态迁移。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Bill b where b.id = :billId")
  Optional<Bill> findByIdForUpdate(@Param("billId") UUID billId);

  Page<Bill> findByPetIdOrderByCreatedAtDesc(UUID petId, Pageable pageable);

  Page<Bill> findByPetIdAndStatusOrderByCreatedAtDesc(
      UUID petId, BillStatus status, Pageable pageable);

  long countByMedicalRecordId(UUID medicalRecordId);
}
