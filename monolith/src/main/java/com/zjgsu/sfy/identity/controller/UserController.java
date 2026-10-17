package com.zjgsu.sfy.identity.controller;

import com.zjgsu.sfy.identity.CurrentUser;
import com.zjgsu.sfy.identity.IdentityApi;
import com.zjgsu.sfy.identity.dto.req.CreateUserRequest;
import com.zjgsu.sfy.identity.dto.resp.UserResponse;
import com.zjgsu.sfy.identity.service.UserService;
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
public class UserController {

  private final UserService userService;
  private final IdentityApi identityApi;

  public UserController(UserService userService, IdentityApi identityApi) {
    this.userService = userService;
    this.identityApi = identityApi;
  }

  @GetMapping("/me")
  public UserResponse me() {
    CurrentUser currentUser = identityApi.currentUser();
    return UserResponse.from(userService.getProfile(currentUser, currentUser.userId()));
  }

  @GetMapping("/users/{id}")
  public UserResponse getUser(@PathVariable UUID id) {
    return UserResponse.from(userService.getProfile(identityApi.currentUser(), id));
  }

  @PostMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
    return UserResponse.from(userService.createUser(request.toCommand()));
  }
}
