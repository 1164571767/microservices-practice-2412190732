package com.zjgsu.sfy.reporting.repository;

import com.zjgsu.sfy.reporting.entity.DrugConsumptionFact;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrugConsumptionFactRepository extends JpaRepository<DrugConsumptionFact, UUID> {

  @Query(
      """
      select f.drugId as drugId, f.drugName as drugName, f.specification as specification,
             f.unit as unit, sum(f.quantity) as quantity, count(distinct f.dispenseId) as dispenseCount
      from DrugConsumptionFact f
      where f.statDate between :from and :to
      group by f.drugId, f.drugName, f.specification, f.unit
      order by sum(f.quantity) desc, f.drugName asc, f.drugId asc
      """)
  List<DrugConsumption> summarize(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(
      """
      select f.drugId as drugId, f.drugName as drugName, f.specification as specification,
             f.unit as unit, sum(f.quantity) as quantity, count(distinct f.dispenseId) as dispenseCount
      from DrugConsumptionFact f
      where f.statDate between :from and :to and f.drugId = :drugId
      group by f.drugId, f.drugName, f.specification, f.unit
      order by sum(f.quantity) desc, f.drugName asc, f.drugId asc
      """)
  List<DrugConsumption> summarizeByDrug(
      @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("drugId") UUID drugId);

  @Query(
      """
      select count(distinct f.dispenseId)
      from DrugConsumptionFact f
      where f.statDate between :from and :to
      """)
  long countDispenses(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(
      """
      select count(distinct f.dispenseId)
      from DrugConsumptionFact f
      where f.statDate between :from and :to and f.drugId = :drugId
      """)
  long countDispensesByDrug(
      @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("drugId") UUID drugId);

  interface DrugConsumption {

    UUID getDrugId();

    String getDrugName();

    String getSpecification();

    String getUnit();

    long getQuantity();

    long getDispenseCount();
  }
}
