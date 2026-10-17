package com.zjgsu.sfy.identity.controller;

import com.zjgsu.sfy.identity.dto.req.LoginRequest;
import com.zjgsu.sfy.identity.dto.req.RefreshRequest;
import com.zjgsu.sfy.identity.dto.req.RegisterRequest;
import com.zjgsu.sfy.identity.dto.resp.TokenResponse;
import com.zjgsu.sfy.identity.dto.resp.UserResponse;
import com.zjgsu.sfy.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    return UserResponse.from(authService.register(request.toCommand()));
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return TokenResponse.from(authService.login(request.toCommand()));
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return TokenResponse.from(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody RefreshRequest request) {
    authService.logout(request.refreshToken());
  }
}
