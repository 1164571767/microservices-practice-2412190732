package com.zjgsu.sfy.medical.service;

import com.zjgsu.sfy.common.error.*;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.AppointmentLookup;
import com.zjgsu.sfy.medical.AppointmentLookup.AppointmentInfo;
import com.zjgsu.sfy.medical.ConsultationHandoffLookup;
import com.zjgsu.sfy.medical.dto.resp.*;
import com.zjgsu.sfy.medical.repository.*;
import com.zjgsu.sfy.medical.repository.ConsultationRepository.State;
import com.zjgsu.sfy.medical.service.command.*;
import com.zjgsu.sfy.medical.service.command.ConsultationDraft.Choice;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ConsultationService {
  private final AppointmentLookup appointments;
  private final ConsultationRepository consultations;
  private final MedicalRecordRepository records;
  private final PrescriptionRepository prescriptions;
  private final PrescriptionItemRepository items;
  private final MedicalRecordService recordWriter;
  private final PrescriptionService prescriptionWriter;
  private final ConsultationHandoffLookup.Billing billing;
  private final ConsultationHandoffLookup.Pharmacy pharmacy;

  public ConsultationService(
      AppointmentLookup appointments,
      ConsultationRepository consultations,
      MedicalRecordRepository records,
      PrescriptionRepository prescriptions,
      PrescriptionItemRepository items,
      MedicalRecordService recordWriter,
      PrescriptionService prescriptionWriter,
      ConsultationHandoffLookup.Billing billing,
      ConsultationHandoffLookup.Pharmacy pharmacy) {
    this.appointments = appointments;
    this.consultations = consultations;
    this.records = records;
    this.prescriptions = prescriptions;
    this.items = items;
    this.recordWriter = recordWriter;
    this.prescriptionWriter = prescriptionWriter;
    this.billing = billing;
    this.pharmacy = pharmacy;
  }

  public record Detail(
      UUID appointmentId,
      UUID petId,
      UUID doctorId,
      String appointmentStatus,
      long version,
      boolean canWrite,
      ConsultationDraft draft,
      String prescriptionChoice,
      Instant completedAt,
      Instant updatedAt,
      MedicalRecordResponse record) {}

  public record Handoff(
      UUID appointmentId,
      UUID medicalRecordId,
      UUID prescriptionId,
      ConsultationHandoffLookup.BillProgress bill,
      ConsultationHandoffLookup.DispenseProgress dispense) {}

  @Transactional(readOnly = true)
  public Detail get(CurrentUser user, UUID id) {
    requireClinical(user);
    return detail(user, requireAppointment(id, false));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Detail saveDraft(CurrentUser user, UUID id, long version, ConsultationDraft draft) {
    var a = requireWriter(user, id);
    var state = requireVersion(id, version);
    if (records.findByAppointmentId(id).isPresent()) fail("正式病历已提交，不能覆盖草稿");
    consultations.save(
        id, new State(version + 1, draft, "PENDING", null, null), user.userId(), "DRAFT_SAVED");
    return detail(user, a);
  }

  /** Record, prescription, handoff index, audit and revision all commit or roll back together. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Detail submit(CurrentUser user, UUID id, long version) {
    var a = requireWriter(user, id);
    var state = requireVersion(id, version);
    var draft = state.draft();
    if (draft == null) fail("请先保存诊疗草稿");
    validateSubmission(draft);
    if (records.findByAppointmentId(id).isPresent()) fail("该预约已提交正式病历，请查询核实");
    var record =
        recordWriter.createRecord(
            user,
            new CreateMedicalRecordCommand(
                id,
                draft.symptoms().trim(),
                draft.diagnosis().trim(),
                draft.advice().trim(),
                null));
    if (draft.prescriptionChoice() == Choice.PRESCRIPTION) {
      prescriptionWriter.issue(
          user,
          record.getId(),
          new IssuePrescriptionCommand(
              draft.items().stream().map(ConsultationDraft.Line::toCommand).toList()));
    }
    consultations.save(
        id,
        new State(version + 1, null, draft.prescriptionChoice().name(), null, null),
        user.userId(),
        "SUBMITTED");
    return detail(user, a);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Detail complete(CurrentUser user, UUID id, long version, boolean noPrescription) {
    var a = requireWriter(user, id);
    var state = requireVersion(id, version);
    var record =
        records
            .findByAppointmentId(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "须先提交正式病历"));
    boolean hasPrescription = prescriptions.existsByMedicalRecordId(record.getId());
    if (hasPrescription && noPrescription) fail("已开具处方，不能标记无需处方");
    if (!hasPrescription && !noPrescription && !"NONE".equals(state.prescriptionChoice()))
      fail("请开具处方或明确确认无需处方");
    consultations.save(
        id,
        new State(
            version + 1, null, hasPrescription ? "PRESCRIPTION" : "NONE", Instant.now(), null),
        user.userId(),
        "COMPLETED");
    return detail(user, a);
  }

  @Transactional(readOnly = true)
  public Handoff handoff(CurrentUser user, UUID id) {
    requireClinical(user);
    requireAppointment(id, false);
    var record = records.findByAppointmentId(id).orElse(null);
    if (record == null) return new Handoff(id, null, null, null, null);
    var prescription =
        prescriptions.findByMedicalRecordIdIn(List.of(record.getId())).stream()
            .findFirst()
            .orElse(null);
    // Failures propagate; absence is never substituted for a failed dependency read.
    return new Handoff(
        id,
        record.getId(),
        prescription == null ? null : prescription.getId(),
        billing.findBill(record.getId()),
        prescription == null ? null : pharmacy.findDispense(prescription.getId()));
  }

  @Transactional(readOnly = true)
  public List<ConsultationRepository.Operation> operations(CurrentUser user, UUID id, int page) {
    requireClinical(user);
    requireAppointment(id, false);
    return consultations.operations(id, page);
  }

  private AppointmentInfo requireAppointment(UUID id, boolean lock) {
    return (lock ? appointments.lockById(id) : appointments.findById(id))
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在"));
  }

  private AppointmentInfo requireWriter(CurrentUser user, UUID id) {
    var a = requireAppointment(id, true);
    if (!user.isDoctor() || !user.userId().equals(a.doctorId()))
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "仅接诊医生可写");
    if (!"CALLED".equals(a.status()))
      throw new BusinessException(ErrorCode.APPOINTMENT_INVALID_STATE, "仅已叫号预约可诊疗");
    if (consultations.get(id).completedAt() != null) fail("诊疗已完成，正式记录只读");
    return a;
  }

  private State requireVersion(UUID id, long version) {
    var state = consultations.get(id);
    if (state.version() != version)
      throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "诊疗内容已更新，请重新读取核对，保留本机未保存内容");
    return state;
  }

  private Detail detail(CurrentUser user, AppointmentInfo a) {
    var state = consultations.get(a.id());
    var record = records.findByAppointmentId(a.id()).orElse(null);
    MedicalRecordResponse response = null;
    if (record != null) {
      var rx = prescriptions.findByMedicalRecordIdIn(List.of(record.getId()));
      var lines =
          rx.isEmpty()
              ? List.<com.zjgsu.sfy.medical.entity.PrescriptionItem>of()
              : items.findByPrescriptionIdIn(rx.stream().map(p -> p.getId()).toList());
      response =
          MedicalRecordResponse.of(
              record,
              rx.stream()
                  .map(
                      p ->
                          PrescriptionResponse.from(
                              new com.zjgsu.sfy.medical.service.result.PrescriptionDetail(
                                  p,
                                  lines.stream()
                                      .filter(i -> i.getPrescriptionId().equals(p.getId()))
                                      .toList())))
                  .toList());
    }
    boolean attending = user.isDoctor() && user.userId().equals(a.doctorId());
    String choice =
        response != null && !response.prescriptions().isEmpty()
            ? "PRESCRIPTION"
            : state.prescriptionChoice();
    return new Detail(
        a.id(),
        a.petId(),
        a.doctorId(),
        a.status(),
        state.version(),
        attending && "CALLED".equals(a.status()) && state.completedAt() == null,
        attending && record == null ? state.draft() : null,
        choice,
        state.completedAt(),
        state.updatedAt(),
        response);
  }

  private static void requireClinical(CurrentUser user) {
    if (!user.isClinicStaff()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }

  private static void fail(String message) {
    throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, message);
  }

  private static void validateSubmission(ConsultationDraft draft) {
    if (draft.symptoms().isBlank() || draft.diagnosis().isBlank()) invalid("症状和诊断为必填项");
    if (draft.prescriptionChoice() == Choice.PENDING) invalid("请选择开具处方或无需处方");
    if (draft.prescriptionChoice() == Choice.NONE && !draft.items().isEmpty())
      invalid("无需处方时不能包含用药明细");
    if (draft.prescriptionChoice() == Choice.PRESCRIPTION) {
      if (draft.items().isEmpty()) invalid("处方至少需要一条用药明细");
      for (var line : draft.items())
        if (line.drugName().isBlank() || line.dosage().isBlank() || line.frequency().isBlank())
          invalid("请补全药品、剂量和频次");
    }
  }

  private static void invalid(String message) {
    throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
  }
}
