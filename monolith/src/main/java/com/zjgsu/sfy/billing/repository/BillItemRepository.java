package com.zjgsu.sfy.billing.repository;

import com.zjgsu.sfy.billing.entity.BillItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillItemRepository extends JpaRepository<BillItem, UUID> {

  List<BillItem> findByBillIdOrderByCreatedAtAscIdAsc(UUID billId);
}
