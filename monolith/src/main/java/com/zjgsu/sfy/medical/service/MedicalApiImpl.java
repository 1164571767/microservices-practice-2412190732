package com.zjgsu.sfy.medical.service;

import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.medical.MedicalRecordSummary;
import com.zjgsu.sfy.medical.PrescriptionItemSummary;
import com.zjgsu.sfy.medical.PrescriptionSummary;
import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.repository.MedicalRecordRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionItemRepository;
import com.zjgsu.sfy.medical.repository.PrescriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalApiImpl implements MedicalApi {

  private final com.zjgsu.sfy.medical.repository.ConsultationRepository consultations;

  @Override
  @Transactional(readOnly = true)
  public java.util.Map<UUID, com.zjgsu.sfy.medical.ConsultationSummary> consultationSummaries(
      java.util.Collection<UUID> ids) {
    var result = new java.util.HashMap<>(consultations.summaries(ids));
    if (!ids.isEmpty())
      for (var record : medicalRecordRepository.findByAppointmentIdIn(ids)) {
        var state = result.get(record.getAppointmentId());
        result.put(
            record.getAppointmentId(),
            new com.zjgsu.sfy.medical.ConsultationSummary(
                record.getAppointmentId(),
                record.getId(),
                false,
                state == null ? null : state.completedAt()));
      }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<com.zjgsu.sfy.medical.PharmacyPrescription> pharmacyPrescription(UUID id) {
    return prescriptionRepository
        .findById(id)
        .flatMap(
            p ->
                medicalRecordRepository
                    .findById(p.getMedicalRecordId())
                    .map(
                        r ->
                            new com.zjgsu.sfy.medical.PharmacyPrescription(
                                p.getId(),
                                r.getId(),
                                r.getPetId(),
                                p.getDoctorId(),
                                p.getIssuedAt(),
                                prescriptionItemRepository.findByPrescriptionId(id).stream()
                                    .map(
                                        i ->
                                            new com.zjgsu.sfy.medical.PharmacyPrescription.Line(
                                                i.getId(),
                                                i.getDrugName(),
                                                i.getSpecification(),
                                                i.getDosage(),
                                                i.getFrequency(),
                                                i.getQuantity(),
                                                i.getDays(),
                                                i.getNote()))
                                    .toList())));
  }

  private final MedicalRecordRepository medicalRecordRepository;
  private final PrescriptionRepository prescriptionRepository;
  private final PrescriptionItemRepository prescriptionItemRepository;

  public MedicalApiImpl(
      MedicalRecordRepository medicalRecordRepository,
      PrescriptionRepository prescriptionRepository,
      PrescriptionItemRepository prescriptionItemRepository,
      com.zjgsu.sfy.medical.repository.ConsultationRepository consultations) {
    this.medicalRecordRepository = medicalRecordRepository;
    this.prescriptionRepository = prescriptionRepository;
    this.prescriptionItemRepository = prescriptionItemRepository;
    this.consultations = consultations;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MedicalRecordSummary> findRecordsByPet(UUID petId) {
    return medicalRecordRepository.findByPetIdOrderByVisitedAtDesc(petId).stream()
        .map(MedicalApiImpl::toSummary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<com.zjgsu.sfy.medical.BillingRecordSummary>
      billingRecords(UUID petId, org.springframework.data.domain.Pageable pageable) {
    var records = medicalRecordRepository.findByPetId(petId, pageable);
    var prescriptions =
        prescriptionRepository
            .findByMedicalRecordIdIn(records.stream().map(MedicalRecord::getId).toList())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(p -> p.getMedicalRecordId(), p -> p.getId()));
    return records.map(
        r ->
            new com.zjgsu.sfy.medical.BillingRecordSummary(
                r.getId(),
                r.getPetId(),
                r.getDoctorId(),
                r.getVisitedAt(),
                prescriptions.get(r.getId())));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MedicalRecordSummary> findRecord(UUID recordId) {
    return medicalRecordRepository.findById(recordId).map(MedicalApiImpl::toSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MedicalRecordSummary> findRecordsVisitedBetween(
      Instant fromInclusive, Instant toExclusive) {
    return medicalRecordRepository
        .findByVisitedAtGreaterThanEqualAndVisitedAtLessThanOrderByVisitedAtAsc(
            fromInclusive, toExclusive)
        .stream()
        .map(MedicalApiImpl::toSummary)
        .toList();
  }

  private static MedicalRecordSummary toSummary(MedicalRecord record) {
    return new MedicalRecordSummary(
        record.getId(),
        record.getPetId(),
        record.getVisitedAt(),
        record.getDiagnosis(),
        record.getDoctorId());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PrescriptionSummary> findPrescription(UUID prescriptionId) {
    return prescriptionRepository
        .findById(prescriptionId)
        .flatMap(
            prescription ->
                medicalRecordRepository
                    .findById(prescription.getMedicalRecordId())
                    .map(
                        record ->
                            new PrescriptionSummary(
                                prescription.getId(),
                                record.getId(),
                                record.getPetId(),
                                prescription.getDoctorId(),
                                prescription.getIssuedAt(),
                                prescriptionItemRepository
                                    .findByPrescriptionId(prescription.getId())
                                    .stream()
                                    .map(
                                        item ->
                                            new PrescriptionItemSummary(
                                                item.getId(),
                                                item.getDrugName(),
                                                item.getSpecification(),
                                                item.getQuantity()))
                                    .toList())));
  }
}
