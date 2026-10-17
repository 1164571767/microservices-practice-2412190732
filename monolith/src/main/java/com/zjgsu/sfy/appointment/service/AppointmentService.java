package com.zjgsu.sfy.appointment.service;

import com.zjgsu.sfy.appointment.QueueNumberCalled;
import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.appointment.repository.DoctorScheduleRepository;
import com.zjgsu.sfy.appointment.service.command.BookAppointmentCommand;
import com.zjgsu.sfy.appointment.service.command.RescheduleCommand;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

  public AppointmentService(
      AppointmentRepository appointmentRepository,
      DoctorScheduleRepository scheduleRepository,
      OwnerApi ownerApi,
      IdentityApi identityApi,
      IdempotencyRecordRepository idempotencyRepository,
      ApplicationEventPublisher events) {
    this.appointmentRepository = appointmentRepository;
    this.scheduleRepository = scheduleRepository;
    this.ownerApi = ownerApi;
    this.identityApi = identityApi;
    this.idempotencyRepository = idempotencyRepository;
    this.events = events;
  }

  @Transactional
  public Appointment book(
      CurrentUser requester, BookAppointmentCommand command, String idempotencyKey) {
    PetSummary pet = requireAccessiblePet(requester, command.petId());
    requireDoctor(command.doctorId());

    String requestHash = hash(command);
    boolean idempotent = idempotencyKey != null && !idempotencyKey.isBlank();
    if (idempotent) {
      Optional<IdempotencyRecord> existing =
          idempotencyRepository.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
        IdempotencyRecord record = existing.get();
        if (!record.getRequestHash().equals(requestHash)) {
          throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return appointmentRepository
            .findById(record.getResultRef())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
      }
    }

    requireWithinSchedule(command.doctorId(), command.startAt(), command.endAt());

    Appointment appointment =
        Appointment.book(pet.id(), command.doctorId(), command.startAt(), command.endAt());
    try {
      appointmentRepository.saveAndFlush(appointment);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.APPOINTMENT_CONFLICT);
    }

    if (idempotent) {
      try {
        idempotencyRepository.saveAndFlush(
            new IdempotencyRecord(idempotencyKey, requestHash, appointment.getId()));
      } catch (DataIntegrityViolationException ex) {
        throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
      }
    }
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

  @Transactional
  public Appointment reschedule(
      CurrentUser requester, UUID appointmentId, RescheduleCommand command) {
    Appointment appointment = requireAccessibleAppointment(requester, appointmentId);
    if (!appointment.canReschedule()) {
      throw new BusinessException(ErrorCode.APPOINTMENT_INVALID_STATE);
    }
    requireWithinSchedule(
        appointment.getDoctorId(), command.startAt(), command.endAt(), appointment.getId());
    appointment.reschedule(command.startAt(), command.endAt());
    try {
      appointmentRepository.saveAndFlush(appointment);
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
    appointment.checkIn();
    return appointment;
  }

  @Transactional(readOnly = true)
  public List<Appointment> queue(CurrentUser requester, UUID doctorId) {
    requireStaff(requester);
    return appointmentRepository.findByDoctorIdAndStatusOrderByCheckedInAtAsc(
        doctorId, AppointmentStatus.CHECKED_IN);
  }

  @Transactional
  public Appointment callNext(CurrentUser requester, UUID doctorId) {
    requireStaff(requester);
    Appointment next =
        appointmentRepository
            .findFirstByDoctorIdAndStatusOrderByCheckedInAtAsc(
                doctorId, AppointmentStatus.CHECKED_IN)
            .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "候诊队列为空"));
    next.call();
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

  private void requireWithinSchedule(UUID doctorId, Instant startAt, Instant endAt) {
    requireWithinSchedule(doctorId, startAt, endAt, null);
  }

  /** 在持有该医生排班悲观锁的前提下，校验时间落在排班内且不与其它未取消预约重叠。 MySQL 没有 EXCLUDE 约束，锁定排班行可串行化同一医生的并发预约。 */
  private void requireWithinSchedule(
      UUID doctorId, Instant startAt, Instant endAt, UUID excludeAppointmentId) {
    boolean available =
        scheduleRepository.lockByDoctorId(doctorId).stream()
            .anyMatch(schedule -> schedule.covers(startAt, endAt));
    if (!available) {
      throw new BusinessException(ErrorCode.APPOINTMENT_NOT_AVAILABLE);
    }
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
  }

  private void requireStaff(CurrentUser requester) {
    if (!requester.isClinicStaff()) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
    }
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
