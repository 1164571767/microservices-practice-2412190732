package com.zjgsu.sfy.identity.controller;

import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.dto.req.CreateUserRequest;
import com.zjgsu.sfy.identity.dto.resp.UserResponse;
import com.zjgsu.sfy.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity")
@Tag(name = "用户与权限", description = "账号资料查询与员工账号开通")
public class UserController {

  private final UserService userService;
  private final IdentityApi identityApi;

  public UserController(UserService userService, IdentityApi identityApi) {
    this.userService = userService;
    this.identityApi = identityApi;
  }

  @GetMapping("/me")
  @Operation(summary = "查看当前登录人", description = "需要登录。返回当前令牌对应的账号资料和角色，联调时可以先调它确认自己是谁、有哪些权限。")
  public UserResponse me() {
    CurrentUser currentUser = identityApi.currentUser();
    return UserResponse.from(userService.getProfile(currentUser, currentUser.userId()));
  }

  @GetMapping("/users/{id}")
  @Operation(
      summary = "查看指定用户资料",
      description = "本人可以查看自己；员工（STAFF / DOCTOR / ADMIN）可以查看任意账号；宠物主人查看他人账号返回 AUTH_FORBIDDEN。")
  public UserResponse getUser(
      @Parameter(description = "用户 ID", example = "6f1d2c3b-4a59-4e8f-9b01-2c3d4e5f6a7b")
          @PathVariable
          UUID id) {
    return UserResponse.from(userService.getProfile(identityApi.currentUser(), id));
  }

  @PostMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "开通员工账号",
      description = "仅管理员可用。直接创建指定角色的账号（可多选），用于开通医生、前台、药房等员工；用户名和手机号不能重复。")
  public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
    return UserResponse.from(userService.createUser(request.toCommand()));
  }
}
