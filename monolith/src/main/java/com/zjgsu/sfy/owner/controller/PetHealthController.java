package com.zjgsu.sfy.owner.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.dto.req.CreateAllergyRequest;
import com.zjgsu.sfy.owner.dto.req.CreateVaccinationRequest;
import com.zjgsu.sfy.owner.dto.resp.AllergyResponse;
import com.zjgsu.sfy.owner.dto.resp.MedicalRecordSummaryResponse;
import com.zjgsu.sfy.owner.dto.resp.VaccinationResponse;
import com.zjgsu.sfy.owner.service.PetHealthService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/pets/{petId}")
public class PetHealthController {

  private final PetHealthService petHealthService;
  private final IdentityApi identityApi;

  public PetHealthController(PetHealthService petHealthService, IdentityApi identityApi) {
    this.petHealthService = petHealthService;
    this.identityApi = identityApi;
  }

  @GetMapping("/vaccinations")
  public List<VaccinationResponse> listVaccinations(@PathVariable UUID petId) {
    return petHealthService.listVaccinations(identityApi.currentUser(), petId).stream()
        .map(VaccinationResponse::from)
        .toList();
  }

  @PostMapping("/vaccinations")
  @ResponseStatus(HttpStatus.CREATED)
  public VaccinationResponse addVaccination(
      @PathVariable UUID petId, @Valid @RequestBody CreateVaccinationRequest request) {
    return VaccinationResponse.from(
        petHealthService.addVaccination(identityApi.currentUser(), petId, request.toCommand()));
  }

  @GetMapping("/allergies")
  public List<AllergyResponse> listAllergies(@PathVariable UUID petId) {
    return petHealthService.listAllergies(identityApi.currentUser(), petId).stream()
        .map(AllergyResponse::from)
        .toList();
  }

  @PostMapping("/allergies")
  @ResponseStatus(HttpStatus.CREATED)
  public AllergyResponse addAllergy(
      @PathVariable UUID petId, @Valid @RequestBody CreateAllergyRequest request) {
    return AllergyResponse.from(
        petHealthService.addAllergy(identityApi.currentUser(), petId, request.toCommand()));
  }

  @GetMapping("/medical-records")
  public List<MedicalRecordSummaryResponse> listMedicalRecords(@PathVariable UUID petId) {
    return petHealthService.listMedicalRecords(identityApi.currentUser(), petId).stream()
        .map(MedicalRecordSummaryResponse::from)
        .toList();
  }
}
