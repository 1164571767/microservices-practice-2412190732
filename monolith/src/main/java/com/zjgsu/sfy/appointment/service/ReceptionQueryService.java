package com.zjgsu.sfy.appointment.service;

import com.zjgsu.sfy.appointment.common.AppointmentStatus;
import com.zjgsu.sfy.appointment.dto.resp.*;
import com.zjgsu.sfy.appointment.entity.Appointment;
import com.zjgsu.sfy.appointment.repository.AppointmentRepository;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.OwnerApi;
import com.zjgsu.sfy.owner.PatientContactSummary;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 接待查询仅经公开模块 API 批量补充必要患者资料，不访问其他模块实体或仓储。 */
@Service
@Transactional(readOnly = true)
public class ReceptionQueryService {
  private final AppointmentRepository appointments;
  private final IdentityApi identity;
  private final OwnerApi owners;
  private final com.zjgsu.sfy.medical.MedicalApi medical;

  public ReceptionQueryService(
      AppointmentRepository appointments,
      IdentityApi identity,
      OwnerApi owners,
      com.zjgsu.sfy.medical.MedicalApi medical) {
    this.appointments = appointments;
    this.identity = identity;
    this.owners = owners;
    this.medical = medical;
  }

  public List<DoctorOptionResponse> doctors(CurrentUser user) {
    requireStaff(user);
    return identity
        .findUsers(identity.findActiveUserIdsInRoles(List.of("DOCTOR")))
        .values()
        .stream()
        .map(d -> new DoctorOptionResponse(d.id(), d.displayName()))
        .sorted(
            Comparator.comparing(DoctorOptionResponse::displayName)
                .thenComparing(DoctorOptionResponse::id))
        .toList();
  }

  public PageResponse<ReceptionAppointmentResponse> list(
      CurrentUser user,
      LocalDate date,
      UUID doctorId,
      AppointmentStatus status,
      UUID petId,
      int page,
      int size) {
    requireStaff(user);
    if (page < 0 || size < 1 || size > 100)
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    var zone = ZoneId.of("Asia/Shanghai");
    var rows =
        appointments.reception(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
            doctorId,
            status,
            petId,
            PageRequest.of(page, size, Sort.by("startAt", "id")));
    var contacts = contacts(rows.getContent());
    var names = identity.findUsers(rows.stream().map(Appointment::getDoctorId).distinct().toList());
    var progress = medical.consultationSummaries(rows.stream().map(Appointment::getId).toList());
    return new PageResponse<>(
        rows.stream()
            .map(
                a ->
                    new ReceptionAppointmentResponse(
                        AppointmentResponse.from(a),
                        contacts.get(a.getPetId()),
                        names.containsKey(a.getDoctorId())
                            ? names.get(a.getDoctorId()).displayName()
                            : "医生资料不可用",
                        progress.get(a.getId())))
            .toList(),
        rows.getNumber(),
        rows.getSize(),
        rows.getTotalElements(),
        rows.getTotalPages());
  }

  public Map<UUID, PatientContactSummary> contacts(List<Appointment> rows) {
    return owners.patientContacts(rows.stream().map(Appointment::getPetId).distinct().toList());
  }

  private void requireStaff(CurrentUser user) {
    if (!user.isClinicStaff()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }
}
