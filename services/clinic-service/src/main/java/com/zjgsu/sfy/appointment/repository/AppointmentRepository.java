package com.zjgsu.sfy.appointment.repository;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.entity.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Appointment a where a.id = :id")
  Optional<Appointment> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select a from Appointment a where a.startAt >= :dayStart and a.startAt < :dayEnd
        and (:doctorId is null or a.doctorId = :doctorId)
        and (:status is null or a.status = :status)
        and (:petId is null or a.petId = :petId)
      """)
  org.springframework.data.domain.Page<Appointment> reception(
      Instant dayStart,
      Instant dayEnd,
      UUID doctorId,
      AppointmentStatus status,
      UUID petId,
      org.springframework.data.domain.Pageable pageable);

  @Query(
      """
      select a from Appointment a where a.doctorId = :doctorId and a.status = 'CHECKED_IN'
        and a.startAt >= :dayStart and a.startAt < :dayEnd
        and a.checkedInAt >= :dayStart and a.checkedInAt < :dayEnd
      order by a.checkedInAt, a.id
      """)
  List<Appointment> todayQueue(UUID doctorId, Instant dayStart, Instant dayEnd);

  List<Appointment> findByPetIdOrderByStartAtDesc(UUID petId);

  /** 与 [rangeStart, rangeEnd) 有重叠、且状态不为给定状态（用于排除已取消）的预约。 */
  List<Appointment> findByDoctorIdAndStatusNotAndStartAtLessThanAndEndAtGreaterThan(
      UUID doctorId, AppointmentStatus excludedStatus, Instant rangeEnd, Instant rangeStart);

  /** 改期场景：与 [rangeStart, rangeEnd) 重叠、且排除自身后的未取消预约是否存在。 */
  boolean existsByDoctorIdAndStatusNotAndIdNotAndStartAtLessThanAndEndAtGreaterThan(
      UUID doctorId,
      AppointmentStatus excludedStatus,
      UUID excludedId,
      Instant rangeEnd,
      Instant rangeStart);

  List<Appointment> findByDoctorIdAndStatusOrderByCheckedInAtAsc(
      UUID doctorId, AppointmentStatus status);

  /**
   * 数据库只认领一条候诊记录。使用覆盖过滤和排序的索引，避免 filesort 扫描并锁住整个候诊区间； LIMIT 必须在 SQL 中，不能先锁全表结果再用 Java
   * findFirst。MySQL SKIP LOCKED 跳过其他叫号事务已认领的患者。
   */
  @Query(
      value =
          """
        select a.* from appointment a force index (idx_appointment_waiting)
        where a.doctor_id = :doctorId and a.status = 'CHECKED_IN'
          and a.start_at >= :dayStart and a.start_at < :dayEnd
          and a.checked_in_at >= :dayStart and a.checked_in_at < :dayEnd
        order by a.checked_in_at asc, a.id asc
        limit 1 for update skip locked
        """,
      nativeQuery = true)
  Optional<Appointment> lockNextWaiting(
      @Param("doctorId") UUID doctorId,
      @Param("dayStart") Instant dayStart,
      @Param("dayEnd") Instant dayEnd);
}
