package com.zjgsu.sfy.integration;

import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.medical.*;
import com.zjgsu.sfy.owner.*;
import java.util.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class IdentityHttpClient implements IdentityApi {
  private final HttpTransport http;
  private final PeerProperties peers;

  public IdentityHttpClient(HttpTransport http, PeerProperties peers) {
    this.http = http;
    this.peers = peers;
  }

  public CurrentUser currentUser() {
    return RequestIdentity.current();
  }

  public Optional<UserProfile> findUser(UUID id) {
    return Optional.ofNullable(
        http.get(
            peers.getIdentityUrl() + "/internal/identity/users/" + id,
            new ParameterizedTypeReference<UserProfile>() {}));
  }

  public UserProfile getUser(UUID id) {
    return findUser(id)
        .orElseThrow(
            () ->
                new com.zjgsu.sfy.common.error.BusinessException(
                    com.zjgsu.sfy.common.error.ErrorCode.RESOURCE_NOT_FOUND));
  }

  public Map<UUID, UserProfile> findUsers(Collection<UUID> ids) {
    return http.required(
        http.post(
            peers.getIdentityUrl() + "/internal/identity/users/batch",
            ids,
            new ParameterizedTypeReference<Map<UUID, UserProfile>>() {}));
  }

  public Optional<UserProfile> findUserByUsername(String username) {
    String url =
        UriComponentsBuilder.fromUriString(
                peers.getIdentityUrl() + "/internal/identity/users/by-username")
            .queryParam("username", username)
            .build()
            .encode()
            .toUriString();
    return Optional.ofNullable(http.get(url, new ParameterizedTypeReference<UserProfile>() {}));
  }

  public void requireActiveDoctorForBooking(UUID id) {
    http.post(
        peers.getIdentityUrl() + "/internal/identity/doctors/" + id + "/admission",
        Map.of(),
        new ParameterizedTypeReference<Void>() {});
  }

  public List<UUID> findActiveUserIdsInRoles(Collection<String> roles) {
    return http.required(
        http.post(
            peers.getIdentityUrl() + "/internal/identity/active-ids",
            roles,
            new ParameterizedTypeReference<List<UUID>>() {}));
  }
}
