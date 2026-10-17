package com.zjgsu.sfy.appointment.service;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.appointment.repository.DoctorScheduleRepository;
import com.zjgsu.sfy.appointment.service.command.CreateScheduleCommand;
import com.zjgsu.sfy.appointment.service.result.TimeSlot;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.UserProfile;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 排班维护（仅管理员）与可预约时段查询。 */
@Service
public class DoctorScheduleService {

  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

  private final DoctorScheduleRepository scheduleRepository;
  private final AppointmentRepository appointmentRepository;
  private final IdentityApi identityApi;

  public DoctorScheduleService(
      DoctorScheduleRepository scheduleRepository,
      AppointmentRepository appointmentRepository,
      IdentityApi identityApi) {
    this.scheduleRepository = scheduleRepository;
    this.appointmentRepository = appointmentRepository;
    this.identityApi = identityApi;
  }

  @Transactional
  public DoctorSchedule createSchedule(CreateScheduleCommand command) {
    requireDoctor(command.doctorId());
    boolean overlaps =
        !scheduleRepository
            .findByDoctorIdAndStartAtLessThanAndEndAtGreaterThan(
                command.doctorId(), command.endAt(), command.startAt())
            .isEmpty();
    if (overlaps) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT, "该医生排班时段重叠");
    }
    DoctorSchedule schedule =
        DoctorSchedule.create(
            command.doctorId(), command.startAt(), command.endAt(), command.slotMinutes());
    try {
      return scheduleRepository.saveAndFlush(schedule);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT, "该医生排班时段重叠");
    }
  }

  @Transactional(readOnly = true)
  public List<DoctorSchedule> listSchedules(UUID doctorId) {
    return scheduleRepository.findByDoctorIdOrderByStartAtAsc(doctorId);
  }

  @Transactional
  public void deleteSchedule(UUID scheduleId) {
    DoctorSchedule schedule =
        scheduleRepository
            .findById(scheduleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    scheduleRepository.delete(schedule);
  }

  /** 按排班切分某医生某日（Asia/Shanghai）的可预约时段，已约满或未到排班范围内的不返回。 */
  @Transactional(readOnly = true)
  public List<TimeSlot> availability(UUID doctorId, LocalDate date) {
    Instant dayStart = date.atStartOfDay(CLINIC_ZONE).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
    List<DoctorSchedule> schedules =
        scheduleRepository.findByDoctorIdAndStartAtLessThanAndEndAtGreaterThan(
            doctorId, dayEnd, dayStart);
    List<Appointment> booked =
        appointmentRepository.findByDoctorIdAndStatusNotAndStartAtLessThanAndEndAtGreaterThan(
            doctorId, AppointmentStatus.CANCELLED, dayEnd, dayStart);

    List<TimeSlot> slots = new ArrayList<>();
    for (DoctorSchedule schedule : schedules) {
      Instant cursor = schedule.getStartAt();
      while (!cursor
          .plus(schedule.getSlotMinutes(), ChronoUnit.MINUTES)
          .isAfter(schedule.getEndAt())) {
        Instant slotEnd = cursor.plus(schedule.getSlotMinutes(), ChronoUnit.MINUTES);
        if (overlaps(cursor, slotEnd, dayStart, dayEnd) && isFree(cursor, slotEnd, booked)) {
          slots.add(new TimeSlot(cursor, slotEnd));
        }
        cursor = slotEnd;
      }
    }
    slots.sort(Comparator.comparing(TimeSlot::startAt));
    return slots;
  }

  private void requireDoctor(UUID doctorId) {
    UserProfile user = identityApi.getUser(doctorId);
    if (!user.isDoctor()) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "指定的用户不是医生");
    }
  }

  private static boolean isFree(Instant slotStart, Instant slotEnd, List<Appointment> booked) {
    return booked.stream()
        .noneMatch(b -> overlaps(slotStart, slotEnd, b.getStartAt(), b.getEndAt()));
  }

  private static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
    return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
  }
}
