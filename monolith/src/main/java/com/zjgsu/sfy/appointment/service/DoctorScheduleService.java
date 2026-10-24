package com.zjgsu.sfy.appointment.service;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.common.ScheduleState;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.appointment.repository.DoctorScheduleRepository;
import com.zjgsu.sfy.appointment.repository.ScheduleLedgerRepository;
import com.zjgsu.sfy.appointment.service.command.CreateScheduleCommand;
import com.zjgsu.sfy.appointment.service.result.ScheduleOperation;
import com.zjgsu.sfy.appointment.service.result.TimeSlot;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.UserProfile;
import java.time.Clock;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 排班维护（仅管理员）与可预约时段查询。 */
@Service
public class DoctorScheduleService {

  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

  private final DoctorScheduleRepository scheduleRepository;
  private final AppointmentRepository appointmentRepository;
  private final IdentityApi identityApi;
  private final ScheduleLedgerRepository ledger;
  private final Clock clock;

  public DoctorScheduleService(
      DoctorScheduleRepository scheduleRepository,
      AppointmentRepository appointmentRepository,
      IdentityApi identityApi,
      ScheduleLedgerRepository ledger,
      @org.springframework.beans.factory.annotation.Qualifier("appointmentClock") Clock clock) {
    this.scheduleRepository = scheduleRepository;
    this.appointmentRepository = appointmentRepository;
    this.identityApi = identityApi;
    this.ledger = ledger;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DoctorSchedule createSchedule(CurrentUser actor, CreateScheduleCommand command) {
    requireAdmin(actor);
    requireDoctor(command.doctorId());
    ledger.lockDoctor(command.doctorId());
    if (!command.endAt().isAfter(clock.instant())
        || command
            .endAt()
            .isBefore(command.startAt().plus(command.slotMinutes(), ChronoUnit.MINUTES))
        || command.endAt().isAfter(command.startAt().plus(1, ChronoUnit.DAYS))) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排班须尚未结束、至少包含一个完整号源，且单次不超过24小时");
    }
    boolean overlaps =
        scheduleRepository
            .findByDoctorIdAndStartAtLessThanAndEndAtGreaterThan(
                command.doctorId(), command.endAt(), command.startAt())
            .stream()
            .anyMatch(s -> s.getState() != ScheduleState.CANCELLED);
    if (overlaps) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT, "该医生排班时段重叠");
    }
    DoctorSchedule schedule =
        DoctorSchedule.create(
            command.doctorId(), command.startAt(), command.endAt(), command.slotMinutes());
    try {
      DoctorSchedule saved = scheduleRepository.saveAndFlush(schedule);
      ledger.audit(saved.getId(), actor, "CREATE", "新增排班", clock.instant());
      return saved;
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT, "该医生排班时段重叠");
    }
  }

  @Transactional(readOnly = true)
  public List<DoctorSchedule> listSchedules(UUID doctorId) {
    return scheduleRepository.findByDoctorIdOrderByStartAtAsc(doctorId);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void deleteSchedule(CurrentUser actor, UUID scheduleId, long version, String reason) {
    requireAdmin(actor);
    DoctorSchedule schedule = locked(scheduleId, version);
    if (!schedule.getStartAt().isAfter(clock.instant()) || ledger.hasHistory(schedule)) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "仅未开始且从未关联预约的排班可删除；有历史请停止放号或停诊");
    }
    ledger.audit(scheduleId, actor, "DELETE", reason, clock.instant());
    scheduleRepository.delete(schedule);
    scheduleRepository.flush();
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DoctorSchedule changeState(
      CurrentUser actor, UUID id, long version, String reason, ScheduleState target) {
    requireAdmin(actor);
    DoctorSchedule s = locked(id, version);
    if (!s.getEndAt().isAfter(clock.instant())
        || s.getState() == ScheduleState.CANCELLED
        || s.getState() == target
        || (target == ScheduleState.OPEN && s.getState() != ScheduleState.BOOKING_CLOSED)) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "历史排班、已停诊排班或当前状态不允许此操作");
    }
    if (target == ScheduleState.CANCELLED && ledger.hasActiveAppointments(s)) {
      throw new BusinessException(
          ErrorCode.OPERATION_NOT_ALLOWED, "仍有待到院、已签到或已叫号预约，请先核实并改期或取消；已叫号不能直接取消");
    }
    if (target == ScheduleState.OPEN) {
      requireDoctor(s.getDoctorId());
      boolean overlaps =
          scheduleRepository
              .findByDoctorIdAndStartAtLessThanAndEndAtGreaterThan(
                  s.getDoctorId(), s.getEndAt(), s.getStartAt())
              .stream()
              .anyMatch(
                  other ->
                      !other.getId().equals(id) && other.getState() != ScheduleState.CANCELLED);
      if (overlaps) throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT, "恢复放号与其他出诊安排冲突");
    }
    s.changeState(target);
    scheduleRepository.saveAndFlush(s);
    ledger.audit(id, actor, target.name(), reason, clock.instant());
    return s;
  }

  @Transactional(readOnly = true)
  public List<ScheduleOperation> operations(CurrentUser actor, UUID id, int page) {
    requireAdmin(actor);
    return ledger.operations(id, page);
  }

  private DoctorSchedule locked(UUID id, long version) {
    // Scalar read avoids putting a stale pre-lock entity in the persistence context.
    UUID doctorId =
        scheduleRepository
            .doctorId(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    ledger.lockDoctor(doctorId);
    DoctorSchedule s =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (s.getVersion() != version)
      throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "排班已变化，请刷新后重新核对");
    return s;
  }

  private static void requireAdmin(CurrentUser actor) {
    if (!actor.isAdmin()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
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
      if (schedule.getState() != ScheduleState.OPEN) continue;
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
    if (!user.isActiveDoctor()) {
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
