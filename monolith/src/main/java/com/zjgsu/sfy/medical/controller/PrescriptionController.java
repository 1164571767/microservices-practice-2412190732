package com.zjgsu.sfy.medical.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.medical.dto.req.CreatePrescriptionRequest;
import com.zjgsu.sfy.medical.dto.resp.PrescriptionResponse;
import com.zjgsu.sfy.medical.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "处方", description = "医生为病历开具处方，供药房发药使用")
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
  @Operation(
      summary = "开具处方",
      description = "只有医生可用，而且必须是该病历的接诊医生。一份病历只能开一张处方，重复开具返回 OPERATION_NOT_ALLOWED；药品信息直接写在处方明细里。")
  public PrescriptionResponse issue(
      @Parameter(description = "病历 ID", example = "9d1e3f5a-7b9c-4d1e-8f2a-3b4c5d6e7f80")
          @PathVariable
          UUID recordId,
      @Valid @RequestBody CreatePrescriptionRequest request) {
    return PrescriptionResponse.from(
        prescriptionService.issue(identityApi.currentUser(), recordId, request.toCommand()));
  }
}
