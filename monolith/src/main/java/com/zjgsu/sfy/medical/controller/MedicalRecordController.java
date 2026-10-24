package com.zjgsu.sfy.medical.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.medical.dto.req.CreateMedicalRecordRequest;
import com.zjgsu.sfy.medical.dto.resp.MedicalRecordResponse;
import com.zjgsu.sfy.medical.dto.resp.PageResponse;
import com.zjgsu.sfy.medical.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "门诊病历", description = "医生接诊时记录病历，并查询宠物的历史病历")
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
  @Operation(
      summary = "记录接诊病历",
      description =
          "只有医生可用，而且必须是该预约的接诊医生。一次预约只能写一份病历，重复提交返回 OPERATION_NOT_ALLOWED；就诊时间默认取当前时间，不能填未来。")
  public MedicalRecordResponse createRecord(
      @Valid @RequestBody CreateMedicalRecordRequest request) {
    return MedicalRecordResponse.of(
        medicalRecordService.createRecord(identityApi.currentUser(), request.toCommand()),
        List.of());
  }

  @GetMapping("/pets/{petId}/records")
  @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
  @Operation(
      summary = "宠物历史病历",
      description =
          "临床视角：前台、医生、管理员按宠物查历史病历（含处方），按就诊时间倒序分页。主人视角请用 /api/owner/pets/{petId}/medical-records。")
  public PageResponse<MedicalRecordResponse> petHistory(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId,
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "visitedAt").and(Sort.by(Sort.Direction.DESC, "id")));
    return PageResponse.of(
        medicalRecordService.history(petId, pageable), MedicalRecordResponse::from);
  }
}
