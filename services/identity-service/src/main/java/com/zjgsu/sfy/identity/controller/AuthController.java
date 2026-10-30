package com.zjgsu.sfy.identity.controller;

import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.dto.req.LoginRequest;
import com.zjgsu.sfy.identity.dto.req.RefreshRequest;
import com.zjgsu.sfy.identity.dto.req.RegisterRequest;
import com.zjgsu.sfy.identity.dto.resp.TokenResponse;
import com.zjgsu.sfy.identity.dto.resp.UserResponse;
import com.zjgsu.sfy.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "注册、登录、刷新令牌与退出登录；除退出外均无需认证")
public class AuthController {

  private final AuthService authService;
  private final IdentityApi identityApi;

  public AuthController(AuthService authService, IdentityApi identityApi) {
    this.authService = authService;
    this.identityApi = identityApi;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements
  @Operation(
      summary = "注册主人账号",
      description = "宠物主人自助注册。注册后固定获得 OWNER 角色，不能指定其他角色；用户名和手机号都不能与已有账号重复。")
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    return UserResponse.from(authService.register(request.toCommand()));
  }

  @PostMapping("/login")
  @SecurityRequirements
  @Operation(
      summary = "账号登录",
      description =
          "用用户名和密码换取令牌。拿到的 accessToken 填到右上角 Authorize（Bearer）即可调其他接口，refreshToken 用于过期后换新令牌。")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return TokenResponse.from(authService.login(request.toCommand()));
  }

  @PostMapping("/refresh")
  @SecurityRequirements
  @Operation(
      summary = "刷新访问令牌",
      description =
          "accessToken 过期后调用：用 refreshToken 换一组新令牌，旧 refreshToken 同时失效（轮换）。令牌无效或已被用过时返回 AUTH_INVALID_CREDENTIALS。")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return TokenResponse.from(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "退出登录",
      description = "需要登录。撤销请求体中的 refreshToken，之后它不能再换新令牌；已签发的 accessToken 在过期前仍然有效。")
  public void logout(@Valid @RequestBody RefreshRequest request) {
    authService.logout(identityApi.currentUser(), request.refreshToken());
  }
}
