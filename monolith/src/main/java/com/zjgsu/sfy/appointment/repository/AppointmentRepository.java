package com.zjgsu.sfy.appointment.repository;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.entity.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

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

  Optional<Appointment> findFirstByDoctorIdAndStatusOrderByCheckedInAtAsc(
      UUID doctorId, AppointmentStatus status);
}
