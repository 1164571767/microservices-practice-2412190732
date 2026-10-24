package com.zjgsu.sfy.medical.service;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.medical.AppointmentLookup;
import com.zjgsu.sfy.medical.AppointmentLookup.AppointmentInfo;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.entity.Prescription;
import com.zjgsu.sfy.medical.entity.PrescriptionItem;
import com.zjgsu.sfy.medical.repository.MedicalRecordRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionItemRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionRepository;
import com.zjgsu.sfy.medical.service.command.IssuePrescriptionCommand;
import com.zjgsu.sfy.medical.service.command.PrescriptionLineCommand;
import com.zjgsu.sfy.medical.service.policy.MedicalWritePolicy;
import com.zjgsu.sfy.medical.service.result.PrescriptionDetail;
import com.zjgsu.sfy.medical.util.UniqueViolations;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 处方用例：仅接诊医生可为病历开具一张处方。 */
@Service
public class PrescriptionService {

  private final MedicalRecordRepository medicalRecordRepository;
  private final PrescriptionRepository prescriptionRepository;
  private final PrescriptionItemRepository prescriptionItemRepository;
  private final AppointmentLookup appointmentLookup;
  private final MedicalWritePolicy writePolicy;
  private final org.springframework.context.ApplicationEventPublisher events;

  public PrescriptionService(
      MedicalRecordRepository medicalRecordRepository,
      PrescriptionRepository prescriptionRepository,
      PrescriptionItemRepository prescriptionItemRepository,
      AppointmentLookup appointmentLookup,
      MedicalWritePolicy writePolicy,
      org.springframework.context.ApplicationEventPublisher events) {
    this.medicalRecordRepository = medicalRecordRepository;
    this.prescriptionRepository = prescriptionRepository;
    this.prescriptionItemRepository = prescriptionItemRepository;
    this.appointmentLookup = appointmentLookup;
    this.writePolicy = writePolicy;
    this.events = events;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public PrescriptionDetail issue(
      CurrentUser requester, UUID recordId, IssuePrescriptionCommand command) {
    MedicalRecord record =
        medicalRecordRepository
            .findById(recordId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "病历不存在"));
    AppointmentInfo appointment =
        appointmentLookup
            .lockById(record.getAppointmentId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在"));
    writePolicy.enforce(requester, appointment);
    if (prescriptionRepository.existsByMedicalRecordId(recordId)) {
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该病历已开具处方");
    }

    Prescription prescription = Prescription.issue(record.getId(), record.getDoctorId());
    try {
      prescriptionRepository.saveAndFlush(prescription);
    } catch (DataIntegrityViolationException ex) {
      if (!UniqueViolations.causedByUniqueConstraint(ex)) {
        throw ex;
      }
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该病历已开具处方");
    }

    List<PrescriptionItem> items =
        command.items().stream().map(line -> toItem(prescription.getId(), line)).toList();
    prescriptionItemRepository.saveAllAndFlush(items);
    events.publishEvent(
        new com.zjgsu.sfy.medical.PrescriptionIssued(
            prescription.getId(),
            record.getPetId(),
            prescription.getDoctorId(),
            prescription.getIssuedAt()));
    return new PrescriptionDetail(prescription, items);
  }

  private static PrescriptionItem toItem(UUID prescriptionId, PrescriptionLineCommand line) {
    return PrescriptionItem.create(
        prescriptionId,
        line.drugName(),
        line.specification(),
        line.dosage(),
        line.frequency(),
        line.quantity(),
        line.days(),
        line.note());
  }
}
