package com.zjgsu.sfy.medical.repository;

import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {

  /** 批量加载，供历史查询一次取回、避免逐条查询（N+1）。 */
  List<PrescriptionItem> findByPrescriptionIdIn(Collection<UUID> prescriptionIds);
}
