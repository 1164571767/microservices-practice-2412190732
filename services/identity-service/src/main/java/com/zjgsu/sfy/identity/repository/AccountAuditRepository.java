package com.zjgsu.sfy.identity.repository;

import com.zjgsu.sfy.identity.entity.AccountAudit;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuditRepository extends JpaRepository<AccountAudit, UUID> {
  Page<AccountAudit> findByTargetUserId(UUID targetUserId, Pageable pageable);
}
