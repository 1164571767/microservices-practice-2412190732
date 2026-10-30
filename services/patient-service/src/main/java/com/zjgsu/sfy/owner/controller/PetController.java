package com.zjgsu.sfy.owner.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.dto.req.UpsertPetRequest;
import com.zjgsu.sfy.owner.dto.resp.PageResponse;
import com.zjgsu.sfy.owner.dto.resp.PetResponse;
import com.zjgsu.sfy.owner.service.PetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/pets")
@Tag(name = "宠物档案", description = "主人自助维护本人宠物；院内代维护使用客户宠物管理接口，禁止物理删除")
public class PetController {

  private final PetService petService;
  private final IdentityApi identityApi;

  public PetController(PetService petService, IdentityApi identityApi) {
    this.petService = petService;
    this.identityApi = identityApi;
  }

  @GetMapping
  @Operation(
      summary = "我的宠物列表",
      description = "需要登录。分页返回本人名下的宠物，按创建时间倒序；没有主人档案时返回 RESOURCE_NOT_FOUND。")
  public PageResponse<PetResponse> listPets(
      @Parameter(description = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "每页条数，1–100", example = "20") @RequestParam(defaultValue = "20")
          int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.of(
        petService.listPets(identityApi.currentUser(), pageable), PetResponse::from);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "新增宠物",
      description = "主人给自己登记一只宠物。必须先有主人档案，否则返回 RESOURCE_NOT_FOUND；出生日期不能填未来。")
  public PetResponse createPet(@Valid @RequestBody UpsertPetRequest request) {
    return PetResponse.from(petService.createPet(identityApi.currentUser(), request.toCommand()));
  }

  @GetMapping("/{petId}")
  @Operation(summary = "宠物详情", description = "宠物归属主人可以查看；前台、医生、管理员也可以查看；其他主人查看返回 AUTH_FORBIDDEN。")
  public PetResponse getPet(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId) {
    return PetResponse.from(petService.getPet(identityApi.currentUser(), petId));
  }

  @PutMapping("/{petId}")
  @Operation(summary = "修改宠物信息", description = "只有宠物的归属主人可以修改；员工不能代改。整体覆盖姓名、物种、出生日期和体重。")
  public PetResponse updatePet(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId,
      @Valid @RequestBody UpsertPetRequest request) {
    return PetResponse.from(
        petService.updatePet(identityApi.currentUser(), petId, request.toCommand()));
  }

  @DeleteMapping("/{petId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "已停用：物理删除宠物",
      description = "有读取权限时固定返回 OPERATION_NOT_ALLOWED（409），请由院内员工填写原因归档；历史记录不会删除。")
  public void deletePet(
      @Parameter(description = "宠物 ID", example = "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f601")
          @PathVariable
          UUID petId) {
    petService.deletePet(identityApi.currentUser(), petId);
  }
}
