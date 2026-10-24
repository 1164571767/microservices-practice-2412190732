package com.zjgsu.sfy.pharmacy.repository;

import com.zjgsu.sfy.pharmacy.entity.DispenseItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispenseItemRepository extends JpaRepository<DispenseItem, UUID> {

  List<DispenseItem> findByDispenseIdOrderByDrugNameAsc(UUID dispenseId);
}
