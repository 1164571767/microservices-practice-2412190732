package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.common.error.*;
import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.owner.OwnerApi;
import com.zjgsu.sfy.pharmacy.dto.resp.*;
import com.zjgsu.sfy.pharmacy.entity.*;
import com.zjgsu.sfy.pharmacy.repository.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PharmacyWorkbenchService {
  private final PrescriptionTaskRepository tasks;
  private final DispenseRecordRepository records;
  private final DispenseQueryService dispenses;
  private final MedicalApi medical;
  private final OwnerApi owner;
  private final IdentityApi identity;

  public PharmacyWorkbenchService(
      PrescriptionTaskRepository tasks,
      DispenseRecordRepository records,
      DispenseQueryService dispenses,
      MedicalApi medical,
      OwnerApi owner,
      IdentityApi identity) {
    this.tasks = tasks;
    this.records = records;
    this.dispenses = dispenses;
    this.medical = medical;
    this.owner = owner;
    this.identity = identity;
  }

  public Page<PrescriptionTaskResponse> list(
      CurrentUser user, String status, String query, Pageable pageable) {
    requirePharmacy(user);
    if (!Set.of("PENDING", "DISPENSED").contains(status))
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "处方状态无效");
    var petIds = query == null || query.isBlank() ? null : owner.searchPatientIds(query);
    if (petIds != null && petIds.isEmpty()) return Page.empty(pageable);
    var page =
        tasks.findAll(
            (root, q, cb) -> {
              var sub = q.subquery(UUID.class);
              var d = sub.from(DispenseRecord.class);
              sub.select(d.get("id"))
                  .where(cb.equal(d.get("prescriptionId"), root.get("prescriptionId")));
              var state = status.equals("DISPENSED") ? cb.exists(sub) : cb.not(cb.exists(sub));
              return petIds == null ? state : cb.and(state, root.get("petId").in(petIds));
            },
            pageable);
    var patients =
        owner.patientContacts(page.stream().map(PrescriptionTask::getPetId).distinct().toList());
    var doctors =
        identity.findUsers(page.stream().map(PrescriptionTask::getDoctorId).distinct().toList());
    return page.map(
        t ->
            new PrescriptionTaskResponse(
                t.getPrescriptionId(),
                t.getPetId(),
                t.getIssuedAt(),
                patients.get(t.getPetId()),
                doctors.containsKey(t.getDoctorId())
                    ? doctors.get(t.getDoctorId()).displayName()
                    : "历史医生",
                status.equals("DISPENSED")));
  }

  public PrescriptionReviewResponse review(CurrentUser user, UUID id) {
    requirePharmacy(user);
    var prescription =
        medical
            .pharmacyPrescription(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "处方不存在"));
    var patient = owner.patientContacts(List.of(prescription.petId())).get(prescription.petId());
    // 缺失档案与上游异常不解释为无过敏；禁止前端在资料不完整时确认发药。
    if (patient == null)
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "患者资料缺失，请联系管理员核实");
    var doctor = identity.findUsers(List.of(prescription.doctorId())).get(prescription.doctorId());
    return new PrescriptionReviewResponse(
        prescription,
        patient,
        doctor == null ? "历史医生" : doctor.displayName(),
        owner.pharmacyAllergies(prescription.petId()),
        dispenses.findByPrescriptionId(id).map(DispenseResponse::from).orElse(null));
  }

  public DispenseResponse result(CurrentUser user, UUID id) {
    requirePharmacy(user);
    return dispenses
        .findByPrescriptionId(id)
        .map(DispenseResponse::from)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "尚未查到发药记录，请核实或使用原幂等键重试"));
  }

  private void requirePharmacy(CurrentUser user) {
    if (!user.isPharmacyStaff()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }
}
