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
public class PatientHttpClient implements OwnerApi {
  private final HttpTransport http;
  private final PeerProperties peers;

  public PatientHttpClient(HttpTransport http, PeerProperties peers) {
    this.http = http;
    this.peers = peers;
  }

  public Optional<PetSummary> findPet(UUID id) {
    return Optional.ofNullable(
        http.get(
            peers.getPatientUrl() + "/internal/patients/pets/" + id,
            new ParameterizedTypeReference<PetSummary>() {}));
  }

  public void requireActivePetForBooking(UUID id) {
    http.post(
        peers.getPatientUrl() + "/internal/patients/pets/" + id + "/admission",
        Map.of(),
        new ParameterizedTypeReference<Void>() {});
  }

  public Optional<UUID> findOwnerIdByUser(UUID id) {
    return Optional.ofNullable(
        http.get(
            peers.getPatientUrl() + "/internal/patients/owners/by-user/" + id,
            new ParameterizedTypeReference<UUID>() {}));
  }

  public Optional<UUID> findUserIdByPet(UUID id) {
    return Optional.ofNullable(
        http.get(
            peers.getPatientUrl() + "/internal/patients/pets/" + id + "/user",
            new ParameterizedTypeReference<UUID>() {}));
  }

  public List<PharmacyAllergy> pharmacyAllergies(UUID id) {
    return http.required(
        http.get(
            peers.getPatientUrl() + "/internal/patients/pets/" + id + "/allergies",
            new ParameterizedTypeReference<List<PharmacyAllergy>>() {}));
  }

  public List<UUID> searchPatientIds(String query) {
    String url =
        UriComponentsBuilder.fromUriString(peers.getPatientUrl() + "/internal/patients/search")
            .queryParam("query", query)
            .build()
            .encode()
            .toUriString();
    return http.required(http.get(url, new ParameterizedTypeReference<List<UUID>>() {}));
  }

  public Map<UUID, PatientContactSummary> patientContacts(Collection<UUID> ids) {
    return http.required(
        http.post(
            peers.getPatientUrl() + "/internal/patients/contacts",
            ids,
            new ParameterizedTypeReference<Map<UUID, PatientContactSummary>>() {}));
  }
}
