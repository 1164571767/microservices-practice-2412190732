package com.zjgsu.sfy.integration.controller;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.integration.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class IdentityInternalController {

  private final IdentityApi api;
  private final com.zjgsu.sfy.identity.repository.UserRepository users;

  public IdentityInternalController(
      IdentityApi api, com.zjgsu.sfy.identity.repository.UserRepository users) {
    this.api = api;
    this.users = users;
  }

  @GetMapping("/internal/identity/users/{id}")
  public UserProfile user(@PathVariable UUID id) {
    return api.findUser(id).orElse(null);
  }

  @PostMapping("/internal/identity/users/batch")
  public Map<UUID, UserProfile> users(@RequestBody Collection<UUID> ids) {
    return api.findUsers(ids);
  }

  @GetMapping("/internal/identity/users/by-username")
  public UserProfile username(@RequestParam String username) {
    return api.findUserByUsername(username).orElse(null);
  }

  @PostMapping("/internal/identity/active-ids")
  public List<UUID> active(@RequestBody Collection<String> roles) {
    return api.findActiveUserIdsInRoles(roles);
  }

  @PostMapping("/internal/identity/doctors/{id}/admission")
  public void admission(@PathVariable UUID id) {
    api.requireActiveDoctorForBooking(id);
  }

  @PostMapping("/internal/identity/sessions/validate")
  public boolean session(@RequestBody SessionCheck check) {
    return users
        .findById(check.userId())
        .filter(u -> u.isActive() && u.getSessionVersion() == check.version())
        .isPresent();
  }
}
