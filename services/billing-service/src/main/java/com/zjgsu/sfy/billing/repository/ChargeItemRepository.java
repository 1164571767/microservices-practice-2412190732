package com.zjgsu.sfy.billing.repository;

import com.zjgsu.sfy.billing.entity.ChargeItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, UUID> {

  Optional<ChargeItem> findByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  /** 转义符固定为 `!`，与 `LikePatterns` 保持一致，避免用户输入扩大 LIKE 匹配范围。 */
  @Query("select c from ChargeItem c where lower(c.name) like lower(:pattern) escape '!'")
  Page<ChargeItem> search(@Param("pattern") String pattern, Pageable pageable);
}
