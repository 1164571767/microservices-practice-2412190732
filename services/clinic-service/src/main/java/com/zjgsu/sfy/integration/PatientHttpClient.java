package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.common.error.BusinessException;
import com.zjgsu.sfy.common.error.ErrorCode;
import com.zjgsu.sfy.owner.OwnerApi;
import com.zjgsu.sfy.owner.PatientContactSummary;
import com.zjgsu.sfy.owner.PetSummary;
import com.zjgsu.sfy.owner.PharmacyAllergy;
import feign.FeignException;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class PatientHttpClient implements OwnerApi {
  private final PatientFeignClient patient;
  private final HttpTransport http;

  public PatientHttpClient(PatientFeignClient patient, HttpTransport http) {
    this.patient = patient;
    this.http = http;
  }

  public Optional<PetSummary> findPet(UUID id) {
    return Optional.ofNullable(call(() -> patient.findPet(id)));
  }

  public void requireActivePetForBooking(UUID id) {
    try {
      patient.requireActivePetForBooking(id);
    } catch (FeignException ex) {
      throw translate(ex);
    }
  }

  public Optional<UUID> findOwnerIdByUser(UUID id) {
    return Optional.ofNullable(call(() -> patient.findOwnerIdByUser(id)));
  }

  public Optional<UUID> findUserIdByPet(UUID id) {
    return Optional.ofNullable(call(() -> patient.findUserIdByPet(id)));
  }

  public List<PharmacyAllergy> pharmacyAllergies(UUID id) {
    return http.required(call(() -> patient.pharmacyAllergies(id)));
  }

  public List<UUID> searchPatientIds(String query) {
    return http.required(call(() -> patient.searchPatientIds(query)));
  }

  public Map<UUID, PatientContactSummary> patientContacts(Collection<UUID> ids) {
    return http.required(call(() -> patient.patientContacts(ids)));
  }

  private <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (FeignException ex) {
      throw translate(ex);
    }
  }

  private BusinessException translate(FeignException ex) {
    if (ex.status() >= 400 && ex.status() < 500)
      return http.clientError(ex.status(), ex.contentUTF8());
    return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "患者档案服务暂时不可用");
  }
}
