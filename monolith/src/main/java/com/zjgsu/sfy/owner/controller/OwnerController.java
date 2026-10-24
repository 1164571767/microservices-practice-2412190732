package com.zjgsu.sfy.owner.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.dto.req.UpsertOwnerRequest;
import com.zjgsu.sfy.owner.dto.resp.OwnerResponse;
import com.zjgsu.sfy.owner.service.OwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner")
@Tag(name = "主人档案", description = "宠物主人的联系资料；只能改自己的，员工只能查看")
public class OwnerController {

  private final OwnerService ownerService;
  private final IdentityApi identityApi;

  public OwnerController(OwnerService ownerService, IdentityApi identityApi) {
    this.ownerService = ownerService;
    this.identityApi = identityApi;
  }

  @GetMapping("/me")
  @Operation(
      summary = "查看我的档案",
      description = "需要登录。返回本人的主人档案；如果还没建过档案会返回 RESOURCE_NOT_FOUND，先调「保存我的档案」创建一份。")
  public OwnerResponse me() {
    return OwnerResponse.from(ownerService.getOwnProfile(identityApi.currentUser()));
  }

  @PutMapping("/me")
  @Operation(summary = "保存我的档案", description = "需要登录。创建或更新本人的主人档案，重复调用结果一致（幂等）。主人改姓名、电话、地址都走这个接口。")
  public OwnerResponse upsertMe(@Valid @RequestBody UpsertOwnerRequest request) {
    return OwnerResponse.from(
        ownerService.upsertOwnProfile(identityApi.currentUser(), request.toCommand()));
  }

  @GetMapping("/owners/{ownerId}")
  @Operation(
      summary = "查看指定主人档案",
      description = "本人可以查看自己；前台、医生、管理员可以查看任意主人；其他主人查看别人的档案返回 AUTH_FORBIDDEN。")
  public OwnerResponse getOwner(
      @Parameter(description = "主人档案 ID", example = "2d7e9a41-3b6c-4f82-9d10-8a5c7e3b1f04")
          @PathVariable
          UUID ownerId) {
    return OwnerResponse.from(ownerService.getProfile(identityApi.currentUser(), ownerId));
  }
}
