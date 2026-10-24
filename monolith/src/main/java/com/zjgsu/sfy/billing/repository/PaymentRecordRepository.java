package com.zjgsu.sfy.billing.repository;

import com.zjgsu.sfy.billing.entity.PaymentRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

  Optional<PaymentRecord> findByBillId(UUID billId);
}
