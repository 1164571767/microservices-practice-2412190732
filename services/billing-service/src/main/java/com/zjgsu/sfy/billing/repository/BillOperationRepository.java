package com.zjgsu.sfy.billing.repository;

import com.zjgsu.sfy.billing.entity.BillOperation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillOperationRepository extends JpaRepository<BillOperation, UUID> {

  List<BillOperation> findByBillIdOrderByCreatedAtAscIdAsc(UUID billId);
}
