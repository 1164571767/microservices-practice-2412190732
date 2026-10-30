package com.zjgsu.sfy.reporting.repository;

import com.zjgsu.sfy.reporting.common.RevenueKind;
import com.zjgsu.sfy.reporting.entity.RevenueFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevenueFactRepository extends JpaRepository<RevenueFact, UUID> {

  boolean existsByBillIdAndKind(UUID billId, RevenueKind kind);

  @Query(
      """
      select f.statDate as statDate, f.kind as kind, sum(f.amount) as amount, count(f) as entries
      from RevenueFact f
      where f.statDate between :from and :to
      group by f.statDate, f.kind
      order by f.statDate asc
      """)
  List<DailyRevenue> summarize(@Param("from") LocalDate from, @Param("to") LocalDate to);

  interface DailyRevenue {

    LocalDate getStatDate();

    RevenueKind getKind();

    BigDecimal getAmount();

    long getEntries();
  }
}
