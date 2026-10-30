package com.zjgsu.sfy.integration.controller;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.integration.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClinicInternalController {

  private final MedicalApi api;
  private final com.zjgsu.sfy.medical.repository.PrescriptionRepository prescriptions;
  private final com.zjgsu.sfy.medical.repository.MedicalRecordRepository records;

  public ClinicInternalController(
      MedicalApi api,
      com.zjgsu.sfy.medical.repository.PrescriptionRepository prescriptions,
      com.zjgsu.sfy.medical.repository.MedicalRecordRepository records) {
    this.api = api;
    this.prescriptions = prescriptions;
    this.records = records;
  }

  @PostMapping("/internal/clinic/consultations/batch")
  public Map<UUID, ConsultationSummary> consultations(@RequestBody Collection<UUID> ids) {
    return api.consultationSummaries(ids);
  }

  @GetMapping("/internal/clinic/prescriptions/{id}/review")
  public PharmacyPrescription review(@PathVariable UUID id) {
    return api.pharmacyPrescription(id).orElse(null);
  }

  @GetMapping("/internal/clinic/prescriptions/{id}")
  public PrescriptionSummary prescription(@PathVariable UUID id) {
    return api.findPrescription(id).orElse(null);
  }

  @GetMapping("/internal/clinic/records/{id}")
  public MedicalRecordSummary record(@PathVariable UUID id) {
    return api.findRecord(id).orElse(null);
  }

  @GetMapping("/internal/clinic/pets/{id}/records")
  public List<MedicalRecordSummary> pet(@PathVariable UUID id) {
    return api.findRecordsByPet(id);
  }

  @GetMapping("/internal/clinic/records")
  public List<MedicalRecordSummary> records(@RequestParam Instant from, @RequestParam Instant to) {
    return api.findRecordsVisitedBetween(from, to);
  }

  @GetMapping("/internal/clinic/billing-records")
  public PageEnvelope<BillingRecordSummary> billing(
      @RequestParam UUID petId, @RequestParam int page, @RequestParam int size) {
    return PageEnvelope.from(
        api.billingRecords(
            petId,
            PageRequest.of(
                page,
                Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "visitedAt")
                    .and(Sort.by(Sort.Direction.DESC, "id")))));
  }

  @GetMapping("/internal/clinic/prescription-index")
  public PageEnvelope<PrescriptionIssued> prescriptionIndex(
      @RequestParam int page, @RequestParam int size) {
    var result = prescriptions.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by("id")));
    var owners =
        records.findAllById(result.stream().map(p -> p.getMedicalRecordId()).toList()).stream()
            .collect(java.util.stream.Collectors.toMap(r -> r.getId(), r -> r.getPetId()));
    return PageEnvelope.from(
        result.map(
            p ->
                new PrescriptionIssued(
                    p.getId(),
                    owners.get(p.getMedicalRecordId()),
                    p.getDoctorId(),
                    p.getIssuedAt())));
  }
}
