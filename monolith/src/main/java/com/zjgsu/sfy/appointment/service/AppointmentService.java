package com.zjgsu.sfy.appointment.service;

import com.zjgsu.sfy.appointment.AppointmentBooked;
import com.zjgsu.sfy.appointment.QueueNumberCalled;
import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.common.ScheduleState;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.entity.DoctorSchedule;
import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.appointment.repository.DoctorScheduleRepository;
import com.zjgsu.sfy.appointment.repository.ScheduleLedgerRepository;
import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
import com.zjgsu.sfy.appointment.service.command.RescheduleCommand;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.common.idempotency.IdempotencyKeys;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecord;
import com.zjgsu.sfy.common.idempotency.IdempotencyRecordRepository;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.UserProfile;
import com.zjgsu.sfy.owner.OwnerApi;
import com.zjgsu.sfy.owner.PetSummary;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 预约用例：创建（幂等 + 冲突兜底）、取消/改期、签到与候诊队列。 */
@Service
public class AppointmentService {

  private final AppointmentRepository appointmentRepository;
  private final DoctorScheduleRepository scheduleRepository;
  private final OwnerApi ownerApi;
  private final IdentityApi identityApi;
  private final IdempotencyRecordRepository idempotencyRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final ScheduleLedgerRepository ledger;
  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

  public AppointmentService(
      AppointmentRepository appointmentRepository,
      DoctorScheduleRepository scheduleRepository,
      OwnerApi ownerApi,
      IdentityApi identityApi,
      IdempotencyRecordRepository idempotencyRepository,
      ApplicationEventPublisher events,
      @org.springframework.beans.factory.annotation.Qualifier("appointmentClock") Clock clock,
      ScheduleLedgerRepository ledger) {
    this.appointmentRepository = appointmentRepository;
    this.scheduleRepository = scheduleRepository;
    this.ownerApi = ownerApi;
    this.identityApi = identityApi;
    this.idempotencyRepository = idempotencyRepository;
    this.events = events;
    this.clock = clock;
    this.ledger = ledger;
  }

  /**
   * 创建预约。
   *
   * <p>READ_COMMITTED 是正确性要求：MySQL 默认 REPEATABLE READ 会在本方法前段的普通 SELECT（宠物/幂等查询） 处固定快照，
   * 导致拿到排班悲观锁后的重叠查询仍读旧快照，不同 startAt 的重叠区间可被同时预约。改用 READ_COMMITTED 后，锁内查询读取最新已提交数据。
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Appointment book(
      CurrentUser requester, BookAppointmentCommand command, String idempotencyKey) {
    PetSummary pet = requireAccessiblePet(requester, command.petId());
    requireDoctor(command.doctorId());

    String requestHash = hash(command);
    String key = IdempotencyKeys.normalize(idempotencyKey);
    boolean idempotent = key != null;
    if (idempotent) {
      Optional<Appointment> replayed = replay(key, requestHash);
      if (replayed.isPresent()) {
        return replayed.get();
      }
    }

    // 先锁定该医生的排班：并发预约在此排队。拿到锁后重新检查幂等记录，
    // 让并发同键重试回放先到者的结果，而不是在唯一键上失败。
    ownerApi.requireActivePetForBooking(command.petId());
    ledger.lockDoctor(command.doctorId());
    scheduleRepository.lockByDoctorId(command.doctorId());
    if (idempotent) {
      Optional<Appointment> replayed = replay(key, requestHash);
      if (replayed.isPresent()) {
        return replayed.get();
      }
    }

    requireBookingWindow(command.startAt());
    DoctorSchedule schedule =
        requireWithinSchedule(command.doctorId(), command.startAt(), command.endAt(), null);

    Appointment appointment =
        Appointment.book(pet.id(), command.doctorId(), command.startAt(), command.endAt());
    appointment.assignSchedule(schedule.getId());
    try {
      appointmentRepository.saveAndFlush(appointment);
      ledger.remember(appointment);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT);
    }

    if (idempotent) {
      try {
        idempotencyRepository.saveAndFlush(
            new IdempotencyRecord(key, requestHash, appointment.getId()));
      } catch (DataIntegrityViolationException ex) {
        throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
      }
    }
    events.publishEvent(
        new AppointmentBooked(
            appointment.getId(),
            appointment.getPetId(),
            appointment.getDoctorId(),
            appointment.getStartAt(),
            appointment.getEndAt(),
            Instant.now()));
    return appointment;
  }

  @Transactional(readOnly = true)
  public Appointment getAppointment(CurrentUser requester, UUID appointmentId) {
    return requireAccessibleAppointment(requester, appointmentId);
  }

  @Transactional(readOnly = true)
  public List<Appointment> listByPet(CurrentUser requester, UUID petId) {
    requireAccessiblePet(requester, petId);
    return appointmentRepository.findByPetIdOrderByStartAtDesc(petId);
  }

  @Transactional
  public Appointment cancel(CurrentUser requester, UUID appointmentId) {
    Appointment appointment = requireAccessibleAppointment(requester, appointmentId);
    if (!appointment.canCancel()) {
      throw new BusinessException(ErrorCode.APPOINTMENT_INVALID_STATE);
    }
    appointment.cancel();
    return appointment;
  }

  /** 改期同样需要 READ_COMMITTED：理由见 {@link #book}，重叠校验必须在排班锁内读取最新快照。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Appointment reschedule(
      CurrentUser requester, UUID appointmentId, RescheduleCommand command) {
    Appointment appointment = requireAccessibleAppointment(requester, appointmentId);
    if (!appointment.canReschedule()) {
      throw new BusinessException(ErrorCode.APPOINTMENT_INVALID_STATE);
    }
    ownerApi.requireActivePetForBooking(appointment.getPetId());
    ledger.lockDoctor(appointment.getDoctorId());
    requireBookingWindow(command.startAt());
    DoctorSchedule schedule =
        requireWithinSchedule(
            appointment.getDoctorId(), command.startAt(), command.endAt(), appointment.getId());
    ledger.rememberLegacy(appointment);
    appointment.reschedule(command.startAt(), command.endAt());
    appointment.assignSchedule(schedule.getId());
    try {
      appointmentRepository.saveAndFlush(appointment);
      ledger.remember(appointment);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT);
    }
    return appointment;
  }

  @Transactional
  public Appointment checkIn(CurrentUser requester, UUID appointmentId) {
    requireStaff(requester);
    Appointment appointment =
        appointmentRepository
            .findById(appointmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!appointment.canCheckIn()) {
      throw new BusinessException(ErrorCode.APPOINTMENT_INVALID_STATE);
    }
    if (!appointment
        .getStartAt()
        .atZone(CLINIC_ZONE)
        .toLocalDate()
        .equals(LocalDate.now(clock.withZone(CLINIC_ZONE)))) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "仅允许就诊当日签到，迟到仍可签到");
    }
    appointment.checkIn(clock.instant());
    return appointment;
  }

  @Transactional(readOnly = true)
  public List<Appointment> queue(CurrentUser requester, UUID doctorId) {
    requireStaff(requester);
    Instant dayStart =
        LocalDate.now(clock.withZone(CLINIC_ZONE)).atStartOfDay(CLINIC_ZONE).toInstant();
    return appointmentRepository.todayQueue(doctorId, dayStart, dayStart.plusSeconds(86400));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Appointment callNext(CurrentUser requester, UUID doctorId) {
    requireStaff(requester);
    // SKIP LOCKED：并发叫号各自认领不同患者；没有可认领的行时才视为队列为空。
    Instant dayStart =
        LocalDate.now(clock.withZone(CLINIC_ZONE)).atStartOfDay(CLINIC_ZONE).toInstant();
    Appointment next =
        appointmentRepository
            .lockNextWaiting(doctorId, dayStart, dayStart.plusSeconds(86400))
            .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "候诊队列为空"));
    next.call(clock.instant());
    appointmentRepository.saveAndFlush(next);
    events.publishEvent(
        new QueueNumberCalled(
            next.getId(), next.getPetId(), next.getDoctorId(), next.getCalledAt()));
    return next;
  }

  private Appointment requireAccessibleAppointment(CurrentUser requester, UUID appointmentId) {
    Appointment appointment =
        appointmentRepository
            .findById(appointmentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    requireAccessiblePet(requester, appointment.getPetId());
    return appointment;
  }

  private PetSummary requireAccessiblePet(CurrentUser requester, UUID petId) {
    PetSummary pet =
        ownerApi
            .findPet(petId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (requester.isClinicStaff()) {
      return pet;
    }
    UUID myOwnerId =
        ownerApi
            .findOwnerIdByUser(requester.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "尚未创建主人档案"));
    if (!pet.ownerId().equals(myOwnerId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
    return pet;
  }

  private void requireDoctor(UUID doctorId) {
    UserProfile user = identityApi.getUser(doctorId);
    if (!user.isDoctor()) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "指定的用户不是医生");
    }
  }

  /** 在持有该医生排班悲观锁的前提下，校验时间落在排班内且不与其它未取消预约重叠。 MySQL 没有 EXCLUDE 约束，锁定排班行可串行化同一医生的并发预约。 */
  private DoctorSchedule requireWithinSchedule(
      UUID doctorId, Instant startAt, Instant endAt, UUID excludeAppointmentId) {
    DoctorSchedule schedule =
        scheduleRepository.lockByDoctorId(doctorId).stream()
            .filter(s -> s.getState() == ScheduleState.OPEN && s.covers(startAt, endAt))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.APPOINTMENT_NOT_AVAILABLE));
    boolean conflict =
        excludeAppointmentId == null
            ? !appointmentRepository
                .findByDoctorIdAndStatusNotAndStartAtLessThanAndEndAtGreaterThan(
                    doctorId, AppointmentStatus.CANCELLED, endAt, startAt)
                .isEmpty()
            : appointmentRepository
                .existsByDoctorIdAndStatusNotAndIdNotAndStartAtLessThanAndEndAtGreaterThan(
                    doctorId, AppointmentStatus.CANCELLED, excludeAppointmentId, endAt, startAt);
    if (conflict) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT);
    }
    return schedule;
  }

  private void requireBookingWindow(Instant startAt) {
    LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
    LocalDate date = startAt.atZone(CLINIC_ZONE).toLocalDate();
    if (date.isBefore(today)
        || date.isAfter(today.plusMonths(1))
        || startAt.isBefore(clock.instant())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅可预约今天至下个月同日内尚未开始的时段");
    }
  }

  private void requireStaff(CurrentUser requester) {
    if (!requester.isClinicStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
  }

  private Optional<Appointment> replay(String key, String requestHash) {
    return idempotencyRepository
        .findByIdempotencyKey(key)
        .map(
            record -> {
              if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
              }
              UUID resultRef = record.getResultRef();
              if (resultRef == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "幂等记录缺少预约");
              }
              return appointmentRepository
                  .findById(resultRef)
                  .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            });
  }

  private static String hash(BookAppointmentCommand command) {
    String canonical =
        command.petId()
            + "|"
            + command.doctorId()
            + "|"
            + command.startAt()
            + "|"
            + command.endAt();
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
