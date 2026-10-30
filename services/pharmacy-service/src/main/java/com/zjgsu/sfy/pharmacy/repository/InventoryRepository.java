package com.zjgsu.sfy.pharmacy.repository;

import com.zjgsu.sfy.pharmacy.entity.Inventory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

  @EntityGraph(attributePaths = "drug")
  @Query("select i from Inventory i where i.drugId = :drugId")
  Optional<Inventory> findWithDrugByDrugId(@Param("drugId") UUID drugId);

  /** 调整库存前锁定该行，串行化同一药品的并发调整。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.drugId = :drugId")
  Optional<Inventory> findByDrugIdForUpdate(@Param("drugId") UUID drugId);

  /**
   * 原子条件扣减：只有库存足够时才扣减，并发下由行锁保证不超卖。
   *
   * @return 受影响行数；0 表示库存不足（或库存行不存在）。
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update Inventory i set i.quantity = i.quantity - :quantity,"
          + " i.version = i.version + 1, i.updatedAt = :now"
          + " where i.drugId = :drugId and i.quantity >= :quantity")
  int deduct(
      @Param("drugId") UUID drugId, @Param("quantity") int quantity, @Param("now") Instant now);

  @EntityGraph(attributePaths = "drug")
  @Query(
      "select i from Inventory i where lower(i.drug.name) like lower(:pattern) escape '!'"
          + " or lower(i.drug.specification) like lower(:pattern) escape '!'")
  Page<Inventory> search(@Param("pattern") String pattern, Pageable pageable);

  @EntityGraph(attributePaths = "drug")
  @Query(
      "select i from Inventory i where i.quantity < i.drug.lowStockThreshold"
          + " and (lower(i.drug.name) like lower(:pattern) escape '!'"
          + " or lower(i.drug.specification) like lower(:pattern) escape '!')")
  Page<Inventory> searchLowStock(@Param("pattern") String pattern, Pageable pageable);
}
