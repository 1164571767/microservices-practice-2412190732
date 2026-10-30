package com.zjgsu.sfy.identity.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import com.zjgsu.sfy.identity.dto.req.*;
import com.zjgsu.sfy.identity.dto.resp.*;
import com.zjgsu.sfy.identity.service.EmployeeAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/identity")
@Tag(name = "员工账号管理", description = "员工资料、角色、状态、密码与操作审计；不管理主人账号")
public class EmployeeAccountController {
  private final EmployeeAccountService accounts;
  private final IdentityApi identity;

  public EmployeeAccountController(EmployeeAccountService accounts, IdentityApi identity) {
    this.accounts = accounts;
    this.identity = identity;
  }

  @GetMapping("/employees")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "员工账号分页检索")
  public PageResponse<EmployeeResponse> list(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) Role role,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(
        accounts.list(
            identity.currentUser(),
            query,
            status,
            role,
            PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))),
        EmployeeResponse::from);
  }

  @PutMapping("/employees/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "维护员工资料、角色及状态",
      description = "必须携带所读版本及操作原因；角色和状态变更撤销目标账号所有会话。禁止修改本人角色或停用本人。")
  public EmployeeResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request) {
    return EmployeeResponse.from(accounts.update(identity.currentUser(), id, request.toCommand()));
  }

  @PostMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "员工自助修改密码", description = "验证当前密码；成功后撤销本人全部会话，需重新登录。")
  public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    accounts.changePassword(identity.currentUser(), request.oldPassword(), request.newPassword());
  }

  @PostMapping("/employees/{id}/password-reset")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "管理员重置员工密码", description = "需要当前版本和重置原因；撤销目标账号所有会话，不能重置自己的密码。")
  public void reset(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
    accounts.resetPassword(
        identity.currentUser(), id, request.version(), request.newPassword(), request.reason());
  }

  @GetMapping("/employees/{id}/operations")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "员工账号敏感操作审计", description = "仅管理员可读；没有密码、密码哈希或令牌。")
  public PageResponse<AccountAuditResponse> operations(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    return PageResponse.of(
        accounts.history(
            identity.currentUser(),
            id,
            PageRequest.of(
                Math.max(0, page),
                10,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))),
        AccountAuditResponse::from);
  }
}
