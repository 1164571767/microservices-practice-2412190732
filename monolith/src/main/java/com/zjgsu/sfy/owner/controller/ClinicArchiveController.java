package com.zjgsu.sfy.owner.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.owner.dto.req.*;
import com.zjgsu.sfy.owner.dto.resp.*;
import com.zjgsu.sfy.owner.service.ClinicArchiveService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(
    name = "客户与宠物管理",
    description = "STAFF/DOCTOR/ADMIN 管理全院档案；绑定仅 ADMIN；归档保留历史")
@RestController
@RequestMapping("/api/owner")
@PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
public class ClinicArchiveController {
  private final ClinicArchiveService service;
  private final IdentityApi identity;

  public ClinicArchiveController(ClinicArchiveService service, IdentityApi identity) {
    this.service = service;
    this.identity = identity;
  }

  private Pageable page(int page, int size) {
    return PageRequest.of(
        Math.max(0, page),
        Math.min(100, Math.max(1, size)),
        Sort.by(Sort.Direction.DESC, "createdAt", "id"));
  }

  @GetMapping("/clients")
  @io.swagger.v3.oas.annotations.Operation(summary = "检索客户")
  public PageResponse<OwnerResponse> clients(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Boolean archived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(
        service.clients(identity.currentUser(), query, archived, page(page, size)),
        OwnerResponse::from);
  }

  @PostMapping("/clients")
  @ResponseStatus(HttpStatus.CREATED)
  @io.swagger.v3.oas.annotations.Operation(summary = "建立未绑定客户档案")
  public OwnerResponse create(@Valid @RequestBody UpsertOwnerRequest r) {
    return OwnerResponse.from(service.createClient(identity.currentUser(), r.toCommand()));
  }

  @PutMapping("/clients/{id}")
  @io.swagger.v3.oas.annotations.Operation(summary = "按版本更新客户资料")
  public OwnerResponse update(@PathVariable UUID id, @Valid @RequestBody ClientWriteRequest r) {
    return OwnerResponse.from(
        service.updateClient(identity.currentUser(), id, r.version(), r.details().toCommand()));
  }

  @PostMapping("/clients/{id}/archive")
  @io.swagger.v3.oas.annotations.Operation(summary = "归档客户并保留历史")
  public OwnerResponse archive(@PathVariable UUID id, @Valid @RequestBody ArchiveRequest r) {
    return OwnerResponse.from(
        service.archiveClient(identity.currentUser(), id, r.version(), r.reason()));
  }

  @PostMapping("/clients/{id}/pets")
  @ResponseStatus(HttpStatus.CREATED)
  @io.swagger.v3.oas.annotations.Operation(summary = "为客户登记宠物")
  public PetResponse createPet(@PathVariable UUID id, @Valid @RequestBody ClinicPetWriteRequest r) {
    return PetResponse.from(
        service.createPet(
            identity.currentUser(), id, r.details().toCommand(), r.birthDateEstimated()));
  }

  @GetMapping("/clinic-pets")
  @io.swagger.v3.oas.annotations.Operation(summary = "检索宠物与主人联系摘要")
  public PageResponse<PetDirectoryResponse> pets(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Boolean archived,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(
        service.pets(identity.currentUser(), query, archived, ownerId, page(page, size)), p -> p);
  }

  @PutMapping("/clinic-pets/{id}")
  @io.swagger.v3.oas.annotations.Operation(summary = "按版本更新宠物资料")
  public PetResponse updatePet(@PathVariable UUID id, @Valid @RequestBody ClinicPetWriteRequest r) {
    return PetResponse.from(
        service.updatePet(
            identity.currentUser(),
            id,
            r.version(),
            r.details().toCommand(),
            r.birthDateEstimated()));
  }

  @PostMapping("/clinic-pets/{id}/archive")
  @io.swagger.v3.oas.annotations.Operation(summary = "归档宠物并保留历史")
  public PetResponse archivePet(@PathVariable UUID id, @Valid @RequestBody ArchiveRequest r) {
    return PetResponse.from(
        service.archivePet(identity.currentUser(), id, r.version(), r.reason()));
  }

  @GetMapping("/clients/binding-candidate")
  @PreAuthorize("hasRole('ADMIN')")
  @io.swagger.v3.oas.annotations.Operation(summary = "按准确用户名查找有效主人账号")
  public BindingCandidateResponse candidate(@RequestParam String username) {
    return service.candidate(identity.currentUser(), username);
  }

  @PostMapping("/clients/{id}/bind")
  @PreAuthorize("hasRole('ADMIN')")
  @io.swagger.v3.oas.annotations.Operation(summary = "管理员核对后绑定账号")
  public OwnerResponse bind(@PathVariable UUID id, @Valid @RequestBody BindAccountRequest r) {
    return OwnerResponse.from(
        service.bind(identity.currentUser(), id, r.version(), r.username(), r.expectedUserId()));
  }

  @GetMapping("/clients/{id}/operations")
  @io.swagger.v3.oas.annotations.Operation(summary = "查询客户操作留痕")
  public PageResponse<OperationResponse> clientOperations(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(
        service.operations(identity.currentUser(), id, false, page(page, size)),
        OperationResponse::from);
  }

  @GetMapping("/clinic-pets/{id}/operations")
  @io.swagger.v3.oas.annotations.Operation(summary = "查询宠物操作留痕")
  public PageResponse<OperationResponse> petOperations(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(
        service.operations(identity.currentUser(), id, true, page(page, size)),
        OperationResponse::from);
  }
}
