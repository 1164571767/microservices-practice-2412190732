package com.zjgsu.sfy.medical.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.medical.dto.req.CreateMedicalRecordRequest;
import com.zjgsu.sfy.medical.dto.resp.MedicalRecordResponse;
import com.zjgsu.sfy.medical.dto.resp.PageResponse;
import com.zjgsu.sfy.medical.service.MedicalRecordService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medical")
public class MedicalRecordController {

  private final MedicalRecordService medicalRecordService;
  private final IdentityApi identityApi;

  public MedicalRecordController(
      MedicalRecordService medicalRecordService, IdentityApi identityApi) {
    this.medicalRecordService = medicalRecordService;
    this.identityApi = identityApi;
  }

  @PostMapping("/records")
  @PreAuthorize("hasRole('DOCTOR')")
  @ResponseStatus(HttpStatus.CREATED)
  public MedicalRecordResponse createRecord(
      @Valid @RequestBody CreateMedicalRecordRequest request) {
    return MedicalRecordResponse.of(
        medicalRecordService.createRecord(identityApi.currentUser(), request.toCommand()),
        List.of());
  }

  @GetMapping("/pets/{petId}/records")
  @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
  public PageResponse<MedicalRecordResponse> petHistory(
      @PathVariable UUID petId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "visitedAt").and(Sort.by(Sort.Direction.DESC, "id")));
    return PageResponse.of(
        medicalRecordService.history(petId, pageable), MedicalRecordResponse::from);
  }
}
