package com.zjgsu.sfy.billing.service;

import com.zjgsu.sfy.billing.common.BillStatus;
import com.zjgsu.sfy.billing.dto.resp.*;
import com.zjgsu.sfy.billing.entity.Bill;
import com.zjgsu.sfy.billing.repository.BillRepository;
import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.medical.MedicalApi;
import com.zjgsu.sfy.owner.OwnerApi;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BillingWorkbenchService {
  private final BillRepository bills;
  private final OwnerApi owner;
  private final MedicalApi medical;
  private final IdentityApi identity;

  public BillingWorkbenchService(
      BillRepository bills, OwnerApi owner, MedicalApi medical, IdentityApi identity) {
    this.bills = bills;
    this.owner = owner;
    this.medical = medical;
    this.identity = identity;
  }

  public Page<WorkbenchBillResponse> list(
      CurrentUser user, UUID petId, BillStatus status, Pageable pageable) {
    requireStaff(user);
    if (petId != null) requirePet(petId);
    Specification<Bill> filter =
        (root, query, cb) -> {
          var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
          if (petId != null) predicates.add(cb.equal(root.get("petId"), petId));
          if (status != null) predicates.add(cb.equal(root.get("status"), status));
          return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var page = bills.findAll(filter, pageable);
    var patients = owner.patientContacts(page.stream().map(Bill::getPetId).distinct().toList());
    return page.map(
        b -> new WorkbenchBillResponse(BillSummaryResponse.from(b), patients.get(b.getPetId())));
  }

  public Page<BillingRecordResponse> records(CurrentUser user, UUID petId, Pageable pageable) {
    requireStaff(user);
    requirePet(petId);
    var records = medical.billingRecords(petId, pageable);
    var existing =
        bills
            .findByMedicalRecordIdInAndStatusNot(
                records.stream().map(r -> r.recordId()).toList(), BillStatus.VOID)
            .stream()
            .collect(java.util.stream.Collectors.toMap(Bill::getMedicalRecordId, b -> b));
    var doctors = identity.findUsers(records.stream().map(r -> r.doctorId()).distinct().toList());
    return records.map(
        r -> {
          var bill = existing.get(r.recordId());
          return new BillingRecordResponse(
              r,
              doctors.containsKey(r.doctorId()) ? doctors.get(r.doctorId()).displayName() : "历史医生",
              bill == null ? null : bill.getId(),
              bill == null ? null : bill.getStatus().name());
        });
  }

  private void requirePet(UUID id) {
    owner.findPet(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private void requireStaff(CurrentUser user) {
    if (!user.isBillingStaff()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }
}
