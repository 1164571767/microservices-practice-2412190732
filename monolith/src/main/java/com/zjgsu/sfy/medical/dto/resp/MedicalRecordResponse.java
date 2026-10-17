package com.zjgsu.sfy.medical.dto.resp;

import com.zjgsu.sfy.medical.entity.MedicalRecord;
import com.zjgsu.sfy.medical.service.result.MedicalRecordDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MedicalRecordResponse(
    UUID id,
    UUID appointmentId,
    UUID petId,
    UUID doctorId,
    String symptoms,
    String diagnosis,
    String advice,
    Instant visitedAt,
    Instant createdAt,
    List<PrescriptionResponse> prescriptions) {

  public static MedicalRecordResponse of(
      MedicalRecord record, List<PrescriptionResponse> prescriptions) {
    return new MedicalRecordResponse(
        record.getId(),
        record.getAppointmentId(),
        record.getPetId(),
        record.getDoctorId(),
        record.getSymptoms(),
        record.getDiagnosis(),
        record.getAdvice(),
        record.getVisitedAt(),
        record.getCreatedAt(),
        prescriptions);
  }

  public static MedicalRecordResponse from(MedicalRecordDetail detail) {
    return of(
        detail.record(), detail.prescriptions().stream().map(PrescriptionResponse::from).toList());
  }
}
