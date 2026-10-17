package com.zjgsu.sfy.medical.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.medical.dto.req.CreatePrescriptionRequest;
import com.zjgsu.sfy.medical.dto.resp.PrescriptionResponse;
import com.zjgsu.sfy.medical.service.PrescriptionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medical/records/{recordId}/prescriptions")
public class PrescriptionController {

  private final PrescriptionService prescriptionService;
  private final IdentityApi identityApi;

  public PrescriptionController(PrescriptionService prescriptionService, IdentityApi identityApi) {
    this.prescriptionService = prescriptionService;
    this.identityApi = identityApi;
  }

  @PostMapping
  @PreAuthorize("hasRole('DOCTOR')")
  @ResponseStatus(HttpStatus.CREATED)
  public PrescriptionResponse issue(
      @PathVariable UUID recordId, @Valid @RequestBody CreatePrescriptionRequest request) {
    return PrescriptionResponse.from(
        prescriptionService.issue(identityApi.currentUser(), recordId, request.toCommand()));
  }
}
