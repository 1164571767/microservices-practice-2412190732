package com.zjgsu.sfy.medical.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.AppointmentLookup;
import com.zjgsu.sfy.medical.AppointmentLookup.AppointmentInfo;
import com.zjgsu.sfy.medical.MedicalRecordCreated;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import com.zjgsu.sfy.medical.repository.MedicalRecordRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionItemRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionRepository;
import com.zjgsu.sfy.medical.service.command.CreateMedicalRecordCommand;
import com.zjgsu.sfy.medical.service.policy.MedicalWritePolicy;
import com.zjgsu.sfy.medical.service.result.MedicalRecordDetail;
import com.zjgsu.sfy.medical.service.result.PrescriptionDetail;
import com.zjgsu.sfy.medical.util.UniqueViolations;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 门诊病历用例：接诊医生写病历、按宠物分页查历史。 */
@Service
public class MedicalRecordService {

  private final MedicalRecordRepository medicalRecordRepository;
  private final PrescriptionRepository prescriptionRepository;
  private final PrescriptionItemRepository prescriptionItemRepository;
  private final AppointmentLookup appointmentLookup;
  private final MedicalWritePolicy writePolicy;
  private final ApplicationEventPublisher events;

  public MedicalRecordService(
      MedicalRecordRepository medicalRecordRepository,
      PrescriptionRepository prescriptionRepository,
      PrescriptionItemRepository prescriptionItemRepository,
      AppointmentLookup appointmentLookup,
      MedicalWritePolicy writePolicy,
      ApplicationEventPublisher events) {
    this.medicalRecordRepository = medicalRecordRepository;
    this.prescriptionRepository = prescriptionRepository;
    this.prescriptionItemRepository = prescriptionItemRepository;
    this.appointmentLookup = appointmentLookup;
    this.writePolicy = writePolicy;
    this.events = events;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public MedicalRecord createRecord(CurrentUser requester, CreateMedicalRecordCommand command) {
    AppointmentInfo appointment =
        appointmentLookup
            .lockById(command.appointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在"));
    writePolicy.enforce(requester, appointment);
    Instant visitedAt = command.visitedAt() != null ? command.visitedAt() : Instant.now();
    MedicalRecord record =
        MedicalRecord.create(
            appointment.id(),
            appointment.petId(),
            appointment.doctorId(),
            command.symptoms(),
            command.diagnosis(),
            command.advice(),
            visitedAt);
    MedicalRecord saved;
    try {
      saved = medicalRecordRepository.saveAndFlush(record);
    } catch (DataIntegrityViolationException ex) {
      if (!UniqueViolations.causedByUniqueConstraint(ex)) {
        throw ex;
      }
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该预约已有病历");
    }
    events.publishEvent(
        new MedicalRecordCreated(
            saved.getId(),
            saved.getPetId(),
            saved.getDoctorId(),
            saved.getVisitedAt(),
            Instant.now()));
    return saved;
  }

  /** 宠物历史病历（含处方）分页查询：处方与明细各一次批量加载，页大小不影响查询条数（无 N+1）。 */
  @Transactional(readOnly = true)
  public Page<MedicalRecordDetail> history(UUID petId, Pageable pageable) {
    Page<MedicalRecord> page = medicalRecordRepository.findByPetId(petId, pageable);
    List<UUID> recordIds = page.getContent().stream().map(MedicalRecord::getId).toList();
    if (recordIds.isEmpty()) {
      return page.map(record -> new MedicalRecordDetail(record, List.of()));
    }
    List<Prescription> prescriptions = prescriptionRepository.findByMedicalRecordIdIn(recordIds);
    Map<UUID, List<PrescriptionItem>> itemsByPrescription = loadItemsByPrescription(prescriptions);
    Map<UUID, List<PrescriptionDetail>> prescriptionsByRecord =
        groupByRecord(prescriptions, itemsByPrescription);
    return page.map(
        record ->
            new MedicalRecordDetail(
                record, prescriptionsByRecord.getOrDefault(record.getId(), List.of())));
  }

  private Map<UUID, List<PrescriptionItem>> loadItemsByPrescription(
      List<Prescription> prescriptions) {
    List<UUID> prescriptionIds = prescriptions.stream().map(Prescription::getId).toList();
    if (prescriptionIds.isEmpty()) {
      return Map.of();
    }
    return prescriptionItemRepository.findByPrescriptionIdIn(prescriptionIds).stream()
        .collect(Collectors.groupingBy(PrescriptionItem::getPrescriptionId));
  }

  private Map<UUID, List<PrescriptionDetail>> groupByRecord(
      List<Prescription> prescriptions, Map<UUID, List<PrescriptionItem>> itemsByPrescription) {
    Map<UUID, List<PrescriptionDetail>> byRecord = new HashMap<>();
    for (Prescription prescription : prescriptions) {
      byRecord
          .computeIfAbsent(prescription.getMedicalRecordId(), key -> new ArrayList<>())
          .add(
              new PrescriptionDetail(
                  prescription, itemsByPrescription.getOrDefault(prescription.getId(), List.of())));
    }
    return byRecord;
  }
}
