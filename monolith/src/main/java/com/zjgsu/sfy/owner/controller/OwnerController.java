package com.zjgsu.sfy.owner.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.dto.req.UpsertOwnerRequest;
import com.zjgsu.sfy.owner.dto.resp.OwnerResponse;
import com.zjgsu.sfy.owner.service.OwnerService;
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
public class OwnerController {

  private final OwnerService ownerService;
  private final IdentityApi identityApi;

  public OwnerController(OwnerService ownerService, IdentityApi identityApi) {
    this.ownerService = ownerService;
    this.identityApi = identityApi;
  }

  @GetMapping("/me")
  public OwnerResponse me() {
    return OwnerResponse.from(ownerService.getOwnProfile(identityApi.currentUser()));
  }

  @PutMapping("/me")
  public OwnerResponse upsertMe(@Valid @RequestBody UpsertOwnerRequest request) {
    return OwnerResponse.from(
        ownerService.upsertOwnProfile(identityApi.currentUser(), request.toCommand()));
  }

  @GetMapping("/owners/{ownerId}")
  public OwnerResponse getOwner(@PathVariable UUID ownerId) {
    return OwnerResponse.from(ownerService.getProfile(identityApi.currentUser(), ownerId));
  }
}
