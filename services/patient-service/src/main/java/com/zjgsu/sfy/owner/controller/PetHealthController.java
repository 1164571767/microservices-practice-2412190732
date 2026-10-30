package com.zjgsu.sfy.owner.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.dto.req.CreateAllergyRequest;
import com.zjgsu.sfy.owner.dto.req.CreateVaccinationRequest;
import com.zjgsu.sfy.owner.dto.resp.AllergyResponse;
import com.zjgsu.sfy.owner.dto.resp.MedicalRecordSummaryResponse;
import com.zjgsu.sfy.owner.dto.resp.VaccinationResponse;
import com.zjgsu.sfy.owner.service.PetHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "疫苗与过敏史", description = "宠物健康档案：疫苗、过敏史和历史病历索引")
public class PetHealthController {

  private final PetHealthService petHealthService;
  private final IdentityApi identityApi;

  public PetHealthController(PetHealthService petHealthService, IdentityApi identityApi) {
    this.petHealthService = petHealthService;
    this.identityApi = identityApi;
  }

  @GetMapping("/vaccinations")
  @Operation(summary = "疫苗记录", description = "宠物归属主人或员工可查看，按接种日期倒序返回全部疫苗记录。")
  public List<VaccinationResponse> listVaccinations(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId) {
    return petHealthService.listVaccinations(identityApi.currentUser(), petId).stream()
        .map(VaccinationResponse::from)
        .toList();
  }

  @PostMapping("/vaccinations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "新增疫苗记录", description = "宠物归属主人登记一次接种记录；接种日期不能填未来，下次到期日可空。")
  public VaccinationResponse addVaccination(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId,
      @Valid @RequestBody CreateVaccinationRequest request) {
    return VaccinationResponse.from(
        petHealthService.addVaccination(identityApi.currentUser(), petId, request.toCommand()));
  }

  @GetMapping("/allergies")
  @Operation(summary = "过敏史", description = "宠物归属主人或员工可查看，按创建时间正序返回全部过敏记录。")
  public List<AllergyResponse> listAllergies(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId) {
    return petHealthService.listAllergies(identityApi.currentUser(), petId).stream()
        .map(AllergyResponse::from)
        .toList();
  }

  @PostMapping("/allergies")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "新增过敏记录",
      description = "宠物归属主人登记过敏原与严重程度（MILD 轻微 / MODERATE 中度 / SEVERE 严重）。")
  public AllergyResponse addAllergy(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId,
      @Valid @RequestBody CreateAllergyRequest request) {
    return AllergyResponse.from(
        petHealthService.addAllergy(identityApi.currentUser(), petId, request.toCommand()));
  }

  @PostMapping("/vaccinations/{recordId}/corrections")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public VaccinationResponse correctVaccination(
      @PathVariable UUID petId,
      @PathVariable UUID recordId,
      @Valid @RequestBody com.zjgsu.sfy.owner.dto.req.CorrectVaccinationRequest r) {
    return VaccinationResponse.from(
        petHealthService.correctVaccination(
            identityApi.currentUser(), petId, recordId, r.record().toCommand(), r.reason()));
  }

  @PostMapping("/allergies/{recordId}/corrections")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public AllergyResponse correctAllergy(
      @PathVariable UUID petId,
      @PathVariable UUID recordId,
      @Valid @RequestBody com.zjgsu.sfy.owner.dto.req.CorrectAllergyRequest r) {
    return AllergyResponse.from(
        petHealthService.correctAllergy(
            identityApi.currentUser(), petId, recordId, r.record().toCommand(), r.reason()));
  }

  @GetMapping("/medical-records")
  @Operation(summary = "历史病历索引", description = "按就诊时间倒序返回该宠物的病历摘要（只读，数据来自门诊模块）。主人看自己的宠物，员工可看任意宠物。")
  public List<MedicalRecordSummaryResponse> listMedicalRecords(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId) {
    return petHealthService.listMedicalRecords(identityApi.currentUser(), petId).stream()
        .map(MedicalRecordSummaryResponse::from)
        .toList();
  }
}
