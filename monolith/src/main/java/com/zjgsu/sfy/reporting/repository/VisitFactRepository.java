package com.zjgsu.sfy.reporting.repository;

import com.zjgsu.sfy.reporting.entity.VisitFact;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitFactRepository extends JpaRepository<VisitFact, UUID> {

  long countByStatDateBetween(LocalDate from, LocalDate to);

  long countByStatDateBetweenAndDoctorId(LocalDate from, LocalDate to, UUID doctorId);

  @Query(
      """
      select f.statDate as statDate, count(f) as visits
      from VisitFact f
      where f.statDate between :from and :to
      group by f.statDate
      order by f.statDate asc
      """)
  List<DailyVisits> countDaily(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(
      """
      select f.statDate as statDate, count(f) as visits
      from VisitFact f
      where f.statDate between :from and :to and f.doctorId = :doctorId
      group by f.statDate
      order by f.statDate asc
      """)
  List<DailyVisits> countDailyByDoctor(
      @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("doctorId") UUID doctorId);

  @Query(
      """
      select f.doctorId as doctorId, count(f) as visits
      from VisitFact f
      where f.statDate between :from and :to
      group by f.doctorId
      order by count(f) desc, f.doctorId asc
      """)
  List<DoctorVisits> countByDoctor(@Param("from") LocalDate from, @Param("to") LocalDate to);

  interface DailyVisits {

    LocalDate getStatDate();

    long getVisits();
  }

  interface DoctorVisits {

    UUID getDoctorId();

    long getVisits();
  }
}
