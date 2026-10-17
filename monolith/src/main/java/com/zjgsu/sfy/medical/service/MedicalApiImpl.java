package com.zjgsu.sfy.medical.service;

import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.medical.MedicalRecordSummary;
import com.zjgsu.sfy.medical.repository.MedicalRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalApiImpl implements MedicalApi {

  private final MedicalRecordRepository medicalRecordRepository;

  public MedicalApiImpl(MedicalRecordRepository medicalRecordRepository) {
    this.medicalRecordRepository = medicalRecordRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MedicalRecordSummary> findRecordsByPet(UUID petId) {
    return medicalRecordRepository.findByPetIdOrderByVisitedAtDesc(petId).stream()
        .map(
            record ->
                new MedicalRecordSummary(
                    record.getId(),
                    record.getPetId(),
                    record.getVisitedAt(),
                    record.getDiagnosis(),
                    record.getDoctorId()))
        .toList();
  }
}
