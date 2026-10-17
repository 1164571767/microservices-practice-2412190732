package com.zjgsu.sfy.appointment.repository;

import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {

  List<DoctorSchedule> findByDoctorIdOrderByStartAtAsc(UUID doctorId);

  /** 与 [rangeStart, rangeEnd) 有重叠的排班。 */
  List<DoctorSchedule> findByDoctorIdAndStartAtLessThanAndEndAtGreaterThan(
      UUID doctorId, Instant rangeEnd, Instant rangeStart);

  /** 悲观锁定该医生的全部排班，串行化同一医生的并发预约。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from DoctorSchedule s where s.doctorId = :doctorId order by s.startAt")
  List<DoctorSchedule> lockByDoctorId(@Param("doctorId") UUID doctorId);
}
