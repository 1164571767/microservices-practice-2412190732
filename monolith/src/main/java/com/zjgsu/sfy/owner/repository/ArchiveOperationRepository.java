package com.zjgsu.sfy.owner.repository;

import com.zjgsu.sfy.owner.entity.ArchiveOperation;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveOperationRepository extends JpaRepository<ArchiveOperation, UUID> {
  Page<ArchiveOperation> findByTargetId(UUID targetId, Pageable pageable);
}
